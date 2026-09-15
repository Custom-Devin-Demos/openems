package io.openems.edge.timeofusetariff.pjm;

import static io.openems.edge.timeofusetariff.pjm.PjmParserTest.COMED;
import static io.openems.edge.timeofusetariff.pjm.PjmParserTest.readFixture;
import static io.openems.edge.timeofusetariff.pjm.TimeOfUseTariffPjm.ChannelId.CONSECUTIVE_FAILURES;
import static io.openems.edge.timeofusetariff.pjm.TimeOfUseTariffPjm.ChannelId.HTTP_STATUS_CODE;
import static io.openems.edge.timeofusetariff.pjm.TimeOfUseTariffPjm.ChannelId.LAST_SUCCESSFUL_UPDATE;
import static io.openems.edge.timeofusetariff.pjm.TimeOfUseTariffPjm.ChannelId.PNODE_ID;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import io.openems.common.bridge.http.api.BridgeHttp.Endpoint;
import io.openems.common.bridge.http.api.HttpError;
import io.openems.common.bridge.http.api.HttpResponse;
import io.openems.common.bridge.http.api.UrlBuilder;
import io.openems.common.bridge.http.dummy.DummyBridgeHttpBundle;
import io.openems.common.bridge.http.dummy.DummyBridgeHttpFactory;
import io.openems.common.bridge.http.time.DelayTimeProvider.Delay;
import io.openems.common.test.TimeLeapClock;
import io.openems.common.types.HttpStatus;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.DummyMeta;
import io.openems.edge.timeofusetariff.api.TimeOfUsePrices;
import io.openems.edge.timeofusetariff.pjm.TimeOfUseTariffPjmImpl.PjmDelayTimeProvider;

public class TimeOfUseTariffPjmImplTest {

	/** 2025-06-10 06:00 EDT, the day covered by the page fixtures. */
	private static final Instant NOW = Instant.parse("2025-06-10T10:00:00Z");

	private record Sut(TimeOfUseTariffPjmImpl sut, ComponentTest test, DummyBridgeHttpBundle bridge,
			TimeLeapClock clock) {
	}

	private static Sut activate(MyConfig config) throws Exception {
		final var clock = new TimeLeapClock(NOW, ZoneOffset.UTC);
		final var bridge = DummyBridgeHttpBundle.of(DummyBridgeHttpFactory.dummyBridgeHttpExecutor(clock, true));
		final var sut = new TimeOfUseTariffPjmImpl();
		final var test = new ComponentTest(sut) //
				.addReference("httpBridgeFactory", bridge.factory()) //
				.addReference("meta", new DummyMeta()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.activate(config);
		return new Sut(sut, test, bridge, clock);
	}

	private static MyConfig.Builder config() {
		return MyConfig.create() //
				.setId("timeOfUseTariff0") //
				.setApiKey("test-key") //
				.setPnodeId(COMED) //
				.setZone("COMED") //
				.setPollingIntervalMinutes(60) //
				.setMaxRetries(3);
	}

	@Test
	public void testActivateAndFetchPrices() throws Exception {
		final var s = activate(config().build());
		s.bridge().forceNextSuccessfulResult(HttpResponse.ok(readFixture("pjm-page2.json")));
		s.bridge().runTasksImmediately();

		var prices = s.sut().getPrices();
		assertFalse(prices.isEmpty());
		// page2 covers 12:00..23:00 EDT of 2025-06-10; getPrices() starts at "now"
		assertEquals(Instant.parse("2025-06-10T16:00:00Z"), prices.getFirstTime());
		assertEquals(18.752485, prices.getFirst(), 0.000001);

		s.test().next(new TestCase() //
				.output(HTTP_STATUS_CODE, 200) //
				.output(CONSECUTIVE_FAILURES, 0) //
				.output(PNODE_ID, COMED) //
				.output(LAST_SUCCESSFUL_UPDATE, NOW.getEpochSecond()));
	}

	@Test
	public void testRequestUrlAndHeader() throws Exception {
		final var s = activate(config().setAncillaryCostsPerMwh(1).build());
		final var captured = new AtomicReference<Endpoint>();
		s.bridge().fetcher().addEndpointHandler(endpoint -> {
			captured.set(endpoint);
			return HttpResponse.ok(readFixture("pjm-page2.json"));
		});
		s.bridge().runTasksImmediately();

		var endpoint = captured.get();
		assertNotNull(endpoint);
		assertEquals("test-key", endpoint.properties().get(TimeOfUseTariffPjmImpl.API_KEY_HEADER));
		var url = UrlBuilder.decode(endpoint.url());
		assertTrue(url, url.startsWith(TimeOfUseTariffPjmImpl.API_URL));
		assertTrue(url, url.contains("datetime_beginning_ept=6/10/2025 00:00 to 6/11/2025 23:59"));
		assertTrue(url, url.contains("pnode_id=" + COMED));
		assertTrue(url, url.contains("format=json"));
		assertTrue(url, url.contains("fields=datetime_beginning_utc,datetime_beginning_ept,pnode_id"));
		assertEquals(15_000, endpoint.connectTimeout());
		assertEquals(15_000, endpoint.readTimeout());
		// ancillary costs applied end-to-end
		assertEquals(19.752485, s.sut().getPrices().getFirst(), 0.000001);
	}

	@Test
	public void testPaginationFollowsNextLink() throws Exception {
		final var s = activate(config().build());
		s.bridge().fetcher().addEndpointHandler(endpoint -> {
			if (endpoint.url().contains("StartRow=13")) {
				return HttpResponse.ok(readFixture("pjm-page2.json"));
			}
			return HttpResponse.ok(readFixture("pjm-page1.json"));
		});
		s.bridge().runTasksImmediately();

		// 24 hours fetched via two pages; getPrices() drops the hours before NOW
		var prices = s.sut().getPrices();
		assertEquals(Instant.parse("2025-06-10T10:00:00Z"), prices.getFirstTime());
		assertEquals(Instant.parse("2025-06-11T03:45:00Z"), prices.getLastTime());
		assertEquals(18 * 4, prices.getRawValues().size());

		s.test().next(new TestCase() //
				.output(HTTP_STATUS_CODE, 200) //
				.output(CONSECUTIVE_FAILURES, 0));
	}

	@Test
	public void testUnauthorizedResponse() throws Exception {
		final var s = activate(config().build());
		s.bridge().forceNextFailedResult(new HttpError.ResponseError(HttpStatus.UNAUTHORIZED,
				"{\"statusCode\":401,\"message\":\"Access denied\"}"));
		s.bridge().runTasksImmediately();

		assertEquals(TimeOfUsePrices.EMPTY_PRICES, s.sut().getPrices());
		s.test().next(new TestCase() //
				.output(HTTP_STATUS_CODE, 401) //
				.output(CONSECUTIVE_FAILURES, 1) //
				.output(LAST_SUCCESSFUL_UPDATE, null));
	}

	@Test
	public void testEmptyAndInvalidResponsesDoNotThrow() throws Exception {
		final var s = activate(config().build());
		s.bridge().forceNextSuccessfulResult(HttpResponse.ok(readFixture("pjm-page2.json")));
		s.bridge().runTasksImmediately();
		var before = s.sut().getPrices();
		assertFalse(before.isEmpty());

		// invalid body: prices are kept, failure is counted
		s.bridge().forceNextSuccessfulResult(HttpResponse.ok("<html>maintenance</html>"));
		s.clock().leap(60, MINUTES);
		s.bridge().runTasksImmediately();
		assertEquals(before, s.sut().getPrices());
		s.test().next(new TestCase() //
				.output(HTTP_STATUS_CODE, -1) //
				.output(CONSECUTIVE_FAILURES, 1));

		// empty items: valid response, prices become empty
		s.bridge().forceNextSuccessfulResult(HttpResponse.ok("{\"links\":[],\"items\":[],\"totalRows\":0}"));
		s.clock().leap(60, MINUTES);
		s.bridge().runTasksImmediately();
		assertTrue(s.sut().getPrices().isEmpty());
		s.test().next(new TestCase() //
				.output(HTTP_STATUS_CODE, 200) //
				.output(CONSECUTIVE_FAILURES, 0));
	}

	@Test
	public void testRetryWithBackoff() throws Exception {
		// no handler registered -> every request fails with 404
		final var s = activate(config().build());
		s.bridge().runTasksImmediately();
		s.test().next(new TestCase() //
				.output(HTTP_STATUS_CODE, 404) //
				.output(CONSECUTIVE_FAILURES, 1));

		// 1st retry after 30s
		s.clock().leap(29, SECONDS);
		s.bridge().runTasksImmediately();
		s.test().next(new TestCase().output(CONSECUTIVE_FAILURES, 1));
		s.clock().leap(1, SECONDS);
		s.bridge().runTasksImmediately();
		s.test().next(new TestCase().output(CONSECUTIVE_FAILURES, 2));

		// 2nd retry after 60s, 3rd after 120s
		s.clock().leap(60, SECONDS);
		s.bridge().runTasksImmediately();
		s.test().next(new TestCase().output(CONSECUTIVE_FAILURES, 3));
		s.clock().leap(120, SECONDS);
		s.bridge().runTasksImmediately();
		s.test().next(new TestCase().output(CONSECUTIVE_FAILURES, 4));

		// maxRetries exceeded -> back to polling interval
		s.clock().leap(240, SECONDS);
		s.bridge().runTasksImmediately();
		s.test().next(new TestCase().output(CONSECUTIVE_FAILURES, 4));
		s.clock().leap(60, MINUTES);
		s.bridge().runTasksImmediately();
		s.test().next(new TestCase().output(CONSECUTIVE_FAILURES, 5));

		// recovery resets the counter
		s.bridge().forceNextSuccessfulResult(HttpResponse.ok(readFixture("pjm-page2.json")));
		s.clock().leap(60, MINUTES);
		s.bridge().runTasksImmediately();
		assertFalse(s.sut().getPrices().isEmpty());
		s.test().next(new TestCase() //
				.output(HTTP_STATUS_CODE, 200) //
				.output(CONSECUTIVE_FAILURES, 0) //
				.output(LAST_SUCCESSFUL_UPDATE, s.clock().instant().getEpochSecond()));
	}

	@Test
	public void testPollingInterval() throws Exception {
		final var s = activate(config().setPollingIntervalMinutes(30).build());
		s.bridge().fetcher().addEndpointHandler(endpoint -> HttpResponse.ok(readFixture("pjm-page2.json")));
		s.bridge().runTasksImmediately();
		s.test().next(new TestCase().output(LAST_SUCCESSFUL_UPDATE, NOW.getEpochSecond()));

		s.clock().leap(29, MINUTES);
		s.bridge().runTasksImmediately();
		s.test().next(new TestCase().output(LAST_SUCCESSFUL_UPDATE, NOW.getEpochSecond()));

		s.clock().leap(1, MINUTES);
		s.bridge().runTasksImmediately();
		s.test().next(new TestCase().output(LAST_SUCCESSFUL_UPDATE, NOW.plus(30, MINUTES).getEpochSecond()));
	}

	@Test
	public void testDelayTimeProvider() {
		var failures = new AtomicInteger();
		var provider = new PjmDelayTimeProvider(60, 3, failures::get);

		assertEquals(Delay.immediate(), provider.onFirstRunDelay());
		assertEquals(Delay.of(Duration.ofMinutes(60)), provider.onSuccessRunDelay(null));

		var error = new HttpError.UnknownError(new RuntimeException("test"));
		failures.set(1);
		assertEquals(Delay.of(Duration.ofSeconds(30)), provider.onErrorRunDelay(error));
		failures.set(2);
		assertEquals(Delay.of(Duration.ofSeconds(60)), provider.onErrorRunDelay(error));
		failures.set(3);
		assertEquals(Delay.of(Duration.ofSeconds(120)), provider.onErrorRunDelay(error));
		failures.set(4);
		assertEquals(Delay.of(Duration.ofMinutes(60)), provider.onErrorRunDelay(error));

		// backoff never exceeds the polling interval
		var shortPolling = new PjmDelayTimeProvider(1, 10, failures::get);
		failures.set(5);
		assertEquals(Delay.of(Duration.ofMinutes(1)), shortPolling.onErrorRunDelay(error));
	}

	@Test
	public void testFixtureFileMode() throws Exception {
		var file = Files.createTempFile("pjm-fixture", ".json");
		try {
			Files.writeString(file, readFixture("pjm-page2.json"), StandardCharsets.UTF_8);
			final var s = activate(config() //
					.setApiKey("") //
					.setAncillaryCostsPerMwh(2.5) //
					.setUseFixtureFile(file.toString()) //
					.build());
			// no HTTP request is made in fixture mode
			s.bridge().runTasksImmediately();

			var prices = s.sut().getPrices();
			assertEquals(Instant.parse("2025-06-10T16:00:00Z"), prices.getFirstTime());
			assertEquals(18.752485 + 2.5, prices.getFirst(), 0.000001);
			s.test().next(new TestCase() //
					.output(HTTP_STATUS_CODE, 200) //
					.output(CONSECUTIVE_FAILURES, 0) //
					.output(PNODE_ID, COMED));
		} finally {
			Files.deleteIfExists(file);
		}
	}

	@Test
	public void testMissingFixtureFile() throws Exception {
		final var s = activate(config().setUseFixtureFile("/nonexistent/pjm.json").build());
		assertTrue(s.sut().getPrices().isEmpty());
		s.test().next(new TestCase() //
				.output(HTTP_STATUS_CODE, -1) //
				.output(CONSECUTIVE_FAILURES, 1));
	}

	@Test
	public void testMissingApiKeyDoesNotRequest() throws Exception {
		final var s = activate(config().setApiKey("").build());
		s.bridge().runTasksImmediately();
		assertTrue(s.sut().getPrices().isEmpty());
		s.test().next(new TestCase() //
				.output(HTTP_STATUS_CODE, null) //
				.output(CONSECUTIVE_FAILURES, 0) //
				.output(PNODE_ID, COMED));
		s.test().deactivate();
	}
}
