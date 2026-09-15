package io.openems.edge.timeofusetariff.pjm;

import static io.openems.edge.timeofusetariff.pjm.PjmParser.EPT;
import static io.openems.edge.timeofusetariff.pjm.PjmParser.parsePage;
import static io.openems.edge.timeofusetariff.pjm.PjmParser.parsePrices;
import static io.openems.edge.timeofusetariff.pjm.PjmParser.toPrices;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;

import org.junit.Test;

import com.google.common.io.Resources;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.timeofusetariff.api.TimeOfUsePrices;

public class PjmParserTest {

	static final long COMED = 33092371L;

	static String readFixture(String name) {
		var resource = PjmParserTest.class.getResource(name);
		assertNotNull("Missing fixture " + name, resource);
		try {
			return Resources.toString(resource, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Test
	public void testParsePageFixtureShape() throws Exception {
		var page = parsePage(readFixture("pjm-dst-spring-2025-03-09.json"));
		assertEquals(23, page.items().size());
		assertEquals(23, page.totalRows());
		assertNull(page.nextLink());

		var first = page.items().get(0);
		assertEquals(Instant.parse("2025-03-09T05:00:00Z"), first.start());
		assertEquals(COMED, first.pnodeId());
		assertEquals("COMED", first.pnodeName());
		assertEquals(32.819275, first.totalLmpDa(), 0.000001);
	}

	@Test
	public void testDstSpringDayHas23Hours() throws Exception {
		var prices = parsePrices(readFixture("pjm-dst-spring-2025-03-09.json"), COMED, "COMED", 0);
		assertEquals(23 * 4, prices.getRawValues().size());
		// 2025-03-09 00:00 EST = 05:00Z
		assertEquals(Instant.parse("2025-03-09T05:00:00Z"), prices.getFirstTime());
		// no 02:00 EPT hour exists; 03:00 EDT = 07:00Z follows 01:00 EST = 06:00Z
		assertEquals(3, ZonedDateTime.ofInstant(Instant.parse("2025-03-09T07:00:00Z"), EPT).getHour());
		assertNotNull(prices.getAt(Instant.parse("2025-03-09T06:45:00Z")));
		assertNotNull(prices.getAt(Instant.parse("2025-03-09T07:00:00Z")));
		// last hour 23:00 EDT = 03:00Z next day
		assertEquals(Instant.parse("2025-03-10T03:45:00Z"), prices.getLastTime());
	}

	@Test
	public void testDstFallDayHas25Hours() throws Exception {
		var prices = parsePrices(readFixture("pjm-dst-fall-2025-11-02.json"), COMED, "COMED", 0);
		assertEquals(25 * 4, prices.getRawValues().size());
		// the two 01:00 EPT hours are distinct instants (05:00Z EDT and 06:00Z EST)
		assertEquals(35.170583, prices.getAt(Instant.parse("2025-11-02T05:00:00Z")), 0.000001);
		assertEquals(31.813377, prices.getAt(Instant.parse("2025-11-02T06:00:00Z")), 0.000001);
		assertEquals(Instant.parse("2025-11-02T04:00:00Z"), prices.getFirstTime());
		assertEquals(Instant.parse("2025-11-03T04:45:00Z"), prices.getLastTime());
	}

	@Test
	public void testEptFallbackWhenUtcMissing() throws Exception {
		var json = """
				{"items":[
				  {"datetime_beginning_ept":"2025-07-01T14:00:00","pnode_id":33092371,"pnode_name":"COMED","total_lmp_da":40.5}
				]}
				""";
		var prices = parsePrices(json, COMED, "COMED", 0);
		var expected = LocalDateTime.parse("2025-07-01T14:00:00").atZone(EPT).toInstant();
		assertEquals(Instant.parse("2025-07-01T18:00:00Z"), expected);
		assertEquals(expected, prices.getFirstTime());
	}

	@Test
	public void testQuarterExpansion() throws Exception {
		var json = """
				{"items":[
				  {"datetime_beginning_utc":"2025-07-01T10:00:00","pnode_id":33092371,"total_lmp_da":10.0},
				  {"datetime_beginning_utc":"2025-07-01T11:00:00","pnode_id":33092371,"total_lmp_da":20.0}
				]}
				""";
		var prices = parsePrices(json, COMED, "COMED", 0);
		assertEquals(8, prices.getRawValues().size());
		var t = Instant.parse("2025-07-01T10:00:00Z");
		for (var i = 0; i < 4; i++) {
			assertEquals(10.0, prices.getAt(t.plusSeconds(i * 900L)), 0.0);
		}
		for (var i = 4; i < 8; i++) {
			assertEquals(20.0, prices.getAt(t.plusSeconds(i * 900L)), 0.0);
		}
	}

	@Test
	public void testAncillaryCostsAreAdded() throws Exception {
		var json = """
				{"items":[{"datetime_beginning_utc":"2025-07-01T10:00:00","pnode_id":33092371,"total_lmp_da":10.25}]}
				""";
		var prices = parsePrices(json, COMED, "COMED", 4.75);
		assertEquals(15.0, prices.getFirst(), 0.0);
	}

	@Test
	public void testFilterByPnodeId() throws Exception {
		var json = """
				{"items":[
				  {"datetime_beginning_utc":"2025-07-01T10:00:00","pnode_id":33092371,"pnode_name":"COMED","total_lmp_da":10.0},
				  {"datetime_beginning_utc":"2025-07-01T10:00:00","pnode_id":1,"pnode_name":"PJM-RTO","total_lmp_da":99.0},
				  {"datetime_beginning_utc":"2025-07-01T11:00:00","pnode_id":1,"pnode_name":"PJM-RTO","total_lmp_da":98.0}
				]}
				""";
		var prices = parsePrices(json, COMED, "COMED", 0);
		assertEquals(4, prices.getRawValues().size());
		assertEquals(10.0, prices.getFirst(), 0.0);

		var other = parsePrices(json, 1, "COMED", 0);
		assertEquals(8, other.getRawValues().size());
		assertEquals(99.0, other.getFirst(), 0.0);
	}

	@Test
	public void testFilterByZoneNameWithoutPnodeId() throws Exception {
		var json = """
				{"items":[
				  {"datetime_beginning_utc":"2025-07-01T10:00:00","pnode_id":33092371,"pnode_name":"COMED","total_lmp_da":10.0},
				  {"datetime_beginning_utc":"2025-07-01T10:00:00","pnode_id":1,"pnode_name":"PJM-RTO","total_lmp_da":99.0}
				]}
				""";
		var prices = parsePrices(json, 0, "pjm-rto", 0);
		assertEquals(4, prices.getRawValues().size());
		assertEquals(99.0, prices.getFirst(), 0.0);

		assertEquals(TimeOfUsePrices.EMPTY_PRICES, parsePrices(json, 0, "UNKNOWN", 0));
	}

	@Test
	public void testPaginationLinks() throws Exception {
		var page1 = parsePage(readFixture("pjm-page1.json"));
		assertEquals(12, page1.items().size());
		assertEquals(24, page1.totalRows());
		assertNotNull(page1.nextLink());
		assertTrue(page1.nextLink().contains("StartRow=13"));

		var page2 = parsePage(readFixture("pjm-page2.json"));
		assertNull(page2.nextLink());

		var all = new ArrayList<>(page1.items());
		all.addAll(page2.items());
		var prices = toPrices(all, COMED, "COMED", 0);
		assertEquals(24 * 4, prices.getRawValues().size());
		assertEquals(Instant.parse("2025-06-10T04:00:00Z"), prices.getFirstTime());
		assertEquals(Instant.parse("2025-06-11T03:45:00Z"), prices.getLastTime());
	}

	@Test
	public void testEmptyItems() throws Exception {
		var prices = parsePrices("{\"links\":[],\"items\":[],\"totalRows\":0}", COMED, "COMED", 0);
		assertEquals(TimeOfUsePrices.EMPTY_PRICES, prices);
		assertTrue(prices.isEmpty());
	}

	@Test
	public void testInvalidResponses() {
		assertInvalid("");
		assertInvalid("<html>Unauthorized</html>");
		assertInvalid("{\"statusCode\":401,\"message\":\"Access denied\"}");
		assertInvalid("{\"items\":[{\"datetime_beginning_utc\":\"2025-07-01T10:00:00\",\"pnode_id\":1}]}");
	}

	private static void assertInvalid(String json) {
		try {
			parsePage(json);
			fail("Expected OpenemsNamedException for [" + json + "]");
		} catch (OpenemsNamedException e) {
			// expected
		}
	}
}
