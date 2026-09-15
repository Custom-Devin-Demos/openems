package io.openems.edge.greenbutton;

import static io.openems.edge.greenbutton.espi.Fixtures.COMED_HOURLY;
import static io.openems.edge.greenbutton.espi.Fixtures.MULTI_USAGEPOINT_COST;
import static io.openems.edge.greenbutton.espi.Fixtures.PGE_15MIN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.Test;

import io.openems.common.bridge.http.api.HttpError;
import io.openems.common.bridge.http.api.HttpResponse;
import io.openems.common.bridge.http.dummy.DummyBridgeHttpBundle;
import io.openems.common.test.TimeLeapClock;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.greenbutton.espi.Fixtures;
import io.openems.edge.timedata.test.DummyTimedata;

public class GreenButtonImportImplTest {

	private static final ChannelAddress TARGET = new ChannelAddress("_sum", "ConsumptionActiveEnergy");
	private static final ChannelAddress POWER = new ChannelAddress("_sum", "ConsumptionActivePower");

	private static ZonedDateTime at(long epochSeconds) {
		return ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.of("UTC"));
	}

	private static record Setup(GreenButtonImportImpl sut, DummyTimedata timedata) {
	}

	private static Setup setup(MyConfig.Builder config) throws Exception {
		return setup(config, DummyBridgeHttpBundle.of());
	}

	/**
	 * Activates the component and waits for the initial asynchronous import by
	 * deactivating it again; further imports are triggered via
	 * {@link GreenButtonImportImpl#runImport()}.
	 *
	 * @param config the config builder
	 * @param http   the {@link DummyBridgeHttpBundle}
	 * @return the activated component and its timedata
	 * @throws Exception on error
	 */
	private static Setup setup(MyConfig.Builder config, DummyBridgeHttpBundle http) throws Exception {
		var clock = new TimeLeapClock(Instant.parse("2024-06-10T12:00:00Z"), ZoneOffset.UTC);
		var timedata = new DummyTimedata("timedata0");
		var sut = new GreenButtonImportImpl();
		new ComponentTest(sut) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("timedata", timedata) //
				.addReference("httpBridgeFactory", http.factory()) //
				.activate(config //
						.setId("greenButton0") //
						.setTimedataId("timedata0") //
						.setPollingIntervalHours(0) //
						.build()) //
				.deactivate();
		return new Setup(sut, timedata);
	}

	private static ImportState importState(GreenButtonImportImpl sut) {
		return sut.getImportStateChannel().getNextValue().asEnum();
	}

	@Test
	public void testImportFileCumulative() throws Exception {
		var s = setup(MyConfig.create() //
				.setSource(Source.FILE) //
				.setPath(Fixtures.path(PGE_15MIN)));

		var written = s.timedata.getValues(TARGET);
		assertEquals(8, written.size());
		assertEquals(125, written.get(at(1709280000)).getAsInt());
		assertEquals(255, written.get(at(1709280900)).getAsInt());
		assertEquals(1030, written.lastEntry().getValue().getAsInt());
		assertTrue(s.timedata.getValues(POWER).isEmpty());

		assertEquals(ImportState.DONE, importState(s.sut));
		assertEquals(Integer.valueOf(8), s.sut.getImportedReadingsChannel().getNextValue().get());
		assertEquals(Long.valueOf(1709280000L), s.sut.getFirstReadingChannel().getNextValue().get());
		assertEquals(Long.valueOf(1709286300L), s.sut.getLastReadingChannel().getNextValue().get());
		assertEquals(Long.valueOf(Instant.parse("2024-06-10T12:00:00Z").getEpochSecond()),
				s.sut.getLastImportChannel().getNextValue().get());
		assertNull(s.sut.getLastErrorChannel().getNextValue().get());
		assertEquals(Boolean.FALSE, s.sut.getImportFailedChannel().getNextValue().get());
	}

	@Test
	public void testImportFilePerIntervalWithPower() throws Exception {
		var s = setup(MyConfig.create() //
				.setSource(Source.FILE) //
				.setPath(Fixtures.path(COMED_HOURLY)) //
				.setCumulative(false) //
				.setPowerChannel(POWER.toString()));

		var energy = s.timedata.getValues(TARGET);
		assertEquals(5, energy.size());
		assertEquals(1000, energy.get(at(1717218000)).getAsInt());
		assertEquals(2000, energy.get(at(1717221600)).getAsInt());
		assertEquals(2000, energy.lastEntry().getValue().getAsInt());

		var power = s.timedata.getValues(POWER);
		assertEquals(5, power.size());
		assertEquals(1000, power.get(at(1717218000)).getAsInt());
		assertEquals(3000, power.get(at(1717304400)).getAsInt());
	}

	@Test
	public void testOnlyConsumptionSeriesIsWritten() throws Exception {
		var s = setup(MyConfig.create() //
				.setSource(Source.FILE) //
				.setPath(Fixtures.path(MULTI_USAGEPOINT_COST)) //
				.setCumulative(false));

		// the reverse-flow solar series is skipped
		assertEquals(Integer.valueOf(2), s.sut.getImportedReadingsChannel().getNextValue().get());
		var energy = s.timedata.getValues(TARGET);
		assertEquals(2000, energy.get(at(1704067200)).getAsInt());
		assertEquals(1000, energy.get(at(1704070800)).getAsInt());
	}

	@Test
	public void testReimportSkipsReadingsUpToLastReading() throws Exception {
		var s = setup(MyConfig.create() //
				.setSource(Source.FILE) //
				.setPath(Fixtures.path(PGE_15MIN)) //
				.setCumulative(false));

		assertEquals(Integer.valueOf(8), s.sut.getImportedReadingsChannel().getNextValue().get());
		assertEquals(0, s.sut.runImport());
		assertEquals(8, s.timedata.getValues(TARGET).size());
		assertEquals(Integer.valueOf(0), s.sut.getImportedReadingsChannel().getNextValue().get());
		assertEquals(Long.valueOf(1709280000L), s.sut.getFirstReadingChannel().getNextValue().get());
		assertEquals(ImportState.DONE, importState(s.sut));
	}

	@Test
	public void testMissingFileFails() throws Exception {
		var s = setup(MyConfig.create() //
				.setSource(Source.FILE) //
				.setPath("/does/not/exist.xml"));

		assertEquals(ImportState.FAILED, importState(s.sut));
		assertEquals(Boolean.TRUE, s.sut.getImportFailedChannel().getNextValue().get());
		assertNotNull(s.sut.getLastErrorChannel().getNextValue().get());
		assertTrue(s.timedata.getValues(TARGET).isEmpty());
	}

	@Test
	public void testImportUrl() throws Exception {
		var http = DummyBridgeHttpBundle.of();
		var called = http.expect(e -> "Bearer secret".equals(e.properties().get("Authorization"))).toBeCalled();
		http.forceNextSuccessfulResult(HttpResponse.ok(Fixtures.read(COMED_HOURLY)));
		var s = setup(MyConfig.create() //
				.setSource(Source.URL) //
				.setUrl("https://greenbutton.example/espi/1_1/resource/Batch/Subscription/1") //
				.setAuthorizationHeader("Bearer secret"), http);

		assertTrue(called.get());
		assertEquals(Integer.valueOf(5), s.sut.getImportedReadingsChannel().getNextValue().get());
		assertEquals(5, s.timedata.getValues(TARGET).size());
		assertEquals(ImportState.DONE, importState(s.sut));
	}

	@Test
	public void testImportUrlHttpError() throws Exception {
		var http = DummyBridgeHttpBundle.of();
		http.forceNextFailedResult(HttpError.ResponseError.notFound());
		var s = setup(MyConfig.create() //
				.setSource(Source.URL) //
				.setUrl("https://greenbutton.example/espi"), http);

		assertEquals(ImportState.FAILED, importState(s.sut));
		assertTrue(s.timedata.getValues(TARGET).isEmpty());
	}
}
