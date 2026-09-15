package io.openems.edge.timeofusetariff.comed;

import static io.openems.common.bridge.http.dummy.DummyBridgeHttpFactory.dummyBridgeHttpExecutor;
import static io.openems.common.bridge.http.dummy.DummyBridgeHttpFactory.dummyEndpointFetcher;
import static io.openems.common.bridge.http.dummy.DummyBridgeHttpFactory.ofBridgeImpl;
import static io.openems.edge.timeofusetariff.comed.TimeOfUseTariffComEd.ChannelId.CONSECUTIVE_FAILURES;
import static io.openems.edge.timeofusetariff.comed.TimeOfUseTariffComEd.ChannelId.FEED;
import static io.openems.edge.timeofusetariff.comed.TimeOfUseTariffComEd.ChannelId.HTTP_STATUS_CODE;
import static io.openems.edge.timeofusetariff.comed.TimeOfUseTariffComEd.ChannelId.LAST_SUCCESSFUL_UPDATE;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.openems.common.bridge.http.api.BridgeHttp;
import io.openems.common.bridge.http.api.HttpError;
import io.openems.common.bridge.http.api.HttpResponse;
import io.openems.common.bridge.http.dummy.DummyBridgeHttpExecutor;
import io.openems.common.bridge.http.dummy.DummyEndpointFetcher;
import io.openems.common.test.TimeLeapClock;
import io.openems.common.types.HttpStatus;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.DummyMeta;
import io.openems.edge.timeofusetariff.comed.TimeOfUseTariffComEdImpl.Step;

public class TimeOfUseTariffComEdImplTest {

	private static final String ID = "timeOfUseTariff0";
	/** 2026-09-15 09:22 CDT, within the recorded fixtures. */
	private static final Instant NOW = Instant.parse("2026-09-15T14:22:00Z");

	private static class Harness {
		private final TimeLeapClock clock = new TimeLeapClock(NOW, ZoneId.of("UTC"));
		private final DummyBridgeHttpExecutor executor = dummyBridgeHttpExecutor(this.clock);
		private final DummyEndpointFetcher fetcher = dummyEndpointFetcher();
		private final List<String> requestedUrls = new ArrayList<>();
		private final TimeOfUseTariffComEdImpl sut = new TimeOfUseTariffComEdImpl();
		private boolean failing = false;
		private String dayAheadTomorrowBody = "[]";

		private Harness() {
			this.fetcher.addEndpointHandler(endpoint -> {
				this.requestedUrls.add(endpoint.url());
				if (this.failing) {
					throw new HttpError.ResponseError(HttpStatus.SERVICE_UNAVAILABLE, "down");
				}
				return HttpResponse.ok(this.fixtureFor(endpoint));
			});
		}

		private ComponentTest activate(Feed feed, int pollingIntervalMinutes, int maxRetries) throws Exception {
			return new ComponentTest(this.sut) //
					.addReference("componentManager", new DummyComponentManager(this.clock)) //
					.addReference("meta", new DummyMeta()) //
					.addReference("httpBridgeFactory", ofBridgeImpl(() -> this.fetcher, () -> this.executor)) //
					.activate(MyConfig.create() //
							.setId(ID) //
							.setFeed(feed) //
							.setPollingIntervalMinutes(pollingIntervalMinutes) //
							.setMaxRetries(maxRetries) //
							.setAncillaryCostsPerMwh(0) //
							.setHttpTimeoutSeconds(15) //
							.build());
		}

		/**
		 * Runs all due tasks of the executor, including immediate follow-ups.
		 */
		private void pump() {
			for (var i = 0; i < 10; i++) {
				this.executor.update();
			}
		}

		private void leapMinutes(long minutes) {
			this.clock.leap(minutes, MINUTES);
			this.pump();
		}

		private Integer consecutiveFailures() {
			return this.sut.getConsecutiveFailuresChannel().getNextValue().get();
		}

		private String fixtureFor(BridgeHttp.Endpoint endpoint) {
			final var url = endpoint.url();
			if (url.equals(ComEdApi.DAY_AHEAD_TODAY_URL)) {
				return Fixtures.DAY_AHEAD;
			}
			if (url.equals(ComEdApi.DAY_AHEAD_TOMORROW_URL)) {
				return this.dayAheadTomorrowBody;
			}
			if (url.equals(ComEdApi.FIVE_MINUTE_FEED_URL)) {
				return Fixtures.FIVE_MINUTE_FEED;
			}
			if (url.equals(ComEdApi.CURRENT_HOUR_AVERAGE_URL)) {
				return Fixtures.CURRENT_HOUR_AVERAGE;
			}
			throw new IllegalArgumentException("Unexpected URL " + url);
		}
	}


	@Test
	public void testActivateDayAhead() throws Exception {
		final var h = new Harness();
		h.activate(Feed.DAY_AHEAD, 5, 3) //
				.next(new ComponentTest.TestCase() //
						.output(ID, FEED, "DAY_AHEAD") //
						.output(ID, CONSECUTIVE_FAILURES, 0));
		assertTrue(h.sut.getPrices().isEmpty());

		h.pump();
		assertEquals(List.of(ComEdApi.DAY_AHEAD_TODAY_URL, ComEdApi.DAY_AHEAD_TOMORROW_URL), h.requestedUrls);

		final var prices = h.sut.getPrices();
		assertFalse(prices.isEmpty());
		// prices start at the current quarter (09:15 CDT) and end 23:45 CDT
		assertEquals(Instant.parse("2026-09-15T14:15:00Z"), prices.getFirstTime());
		assertEquals(Instant.parse("2026-09-16T04:45:00Z"), prices.getLastTime());
		assertEquals(23.0, prices.getFirst(), 0.0001); // day-ahead 2.3 Cent/kWh for 09:00
		assertEquals(59, prices.getRawValues().size());

		assertEquals(Integer.valueOf(200), h.sut.getHttpStatusCodeChannel().getNextValue().get());
		assertEquals(Long.valueOf(NOW.getEpochSecond()),
				h.sut.getLastSuccessfulUpdateChannel().getNextValue().get());
		assertEquals(Integer.valueOf(0), h.consecutiveFailures());
		assertTrue(h.sut.debugLog().startsWith("Price:"));
		h.sut.deactivate();
	}

	@Test
	public void testFiveMinuteFeedOverlaysCurrentHour() throws Exception {
		final var h = new Harness();
		h.activate(Feed.FIVE_MINUTE, 5, 3);
		h.pump();
		assertEquals(List.of(ComEdApi.DAY_AHEAD_TODAY_URL, ComEdApi.DAY_AHEAD_TOMORROW_URL,
				ComEdApi.FIVE_MINUTE_FEED_URL), h.requestedUrls);

		final var prices = h.sut.getPrices();
		// 09:15 CDT quarter from 5-minute feed: 2.2, 2.3 -> 2.25 Cent/kWh
		assertEquals(22.5, prices.getAt(Instant.parse("2026-09-15T14:15:00Z")), 0.0001);
		// 09:00 CDT quarter is before NOW and therefore dropped
		assertNull(prices.getAt(Instant.parse("2026-09-15T14:00:00Z")));
		// future from day-ahead: 19:00 CDT = 23.1 Cent/kWh
		assertEquals(231.0, prices.getAt(Instant.parse("2026-09-16T00:00:00Z")), 0.0001);
		h.sut.deactivate();
	}

	@Test
	public void testCurrentHourAverageOverlaysWholeHour() throws Exception {
		final var h = new Harness();
		h.activate(Feed.CURRENT_HOUR_AVERAGE, 5, 3);
		h.pump();
		assertEquals(3, h.requestedUrls.size());
		assertEquals(ComEdApi.CURRENT_HOUR_AVERAGE_URL, h.requestedUrls.get(2));

		final var prices = h.sut.getPrices();
		// current hour average 2.3 replaces day-ahead 2.3 for 09:00-10:00 CDT
		assertEquals(23.0, prices.getAt(Instant.parse("2026-09-15T14:15:00Z")), 0.0001);
		assertEquals(23.0, prices.getAt(Instant.parse("2026-09-15T14:45:00Z")), 0.0001);
		assertEquals(30.0, prices.getAt(Instant.parse("2026-09-15T15:00:00Z")), 0.0001);
		h.sut.deactivate();
	}

	@Test
	public void testPollingInterval() throws Exception {
		final var h = new Harness();
		h.activate(Feed.DAY_AHEAD, 5, 3);
		h.pump();
		assertEquals(2, h.requestedUrls.size());

		h.leapMinutes(4);
		assertEquals(2, h.requestedUrls.size());

		h.leapMinutes(1);
		assertEquals(4, h.requestedUrls.size());
		assertEquals(Long.valueOf(NOW.plus(5, MINUTES).getEpochSecond()),
				h.sut.getLastSuccessfulUpdateChannel().getNextValue().get());
		h.sut.deactivate();
	}

	@Test
	public void testRetryWithExponentialBackoff() throws Exception {
		final var h = new Harness();
		h.failing = true;
		h.activate(Feed.DAY_AHEAD, 5, 3);
		h.pump();
		// first request failed
		assertEquals(1, h.requestedUrls.size());
		assertEquals(Integer.valueOf(1), h.consecutiveFailures());
		assertEquals(Integer.valueOf(503), h.sut.getHttpStatusCodeChannel().getNextValue().get());
		assertTrue(h.sut.getPrices().isEmpty());

		// 1st retry after 5 min
		h.leapMinutes(4);
		assertEquals(1, h.requestedUrls.size());
		h.leapMinutes(1);
		assertEquals(2, h.requestedUrls.size());
		assertEquals(Integer.valueOf(2), h.consecutiveFailures());

		// 2nd retry after 10 min
		h.leapMinutes(9);
		assertEquals(2, h.requestedUrls.size());
		h.leapMinutes(1);
		assertEquals(3, h.requestedUrls.size());
		assertEquals(Integer.valueOf(3), h.consecutiveFailures());

		// 3rd retry after 20 min
		h.leapMinutes(19);
		assertEquals(3, h.requestedUrls.size());
		h.leapMinutes(1);
		assertEquals(4, h.requestedUrls.size());
		assertEquals(Integer.valueOf(4), h.consecutiveFailures());

		// maxRetries exceeded -> capped at 30 min
		h.leapMinutes(29);
		assertEquals(4, h.requestedUrls.size());
		h.leapMinutes(1);
		assertEquals(5, h.requestedUrls.size());
		assertEquals(Integer.valueOf(5), h.consecutiveFailures());
		assertNull(h.sut.getLastSuccessfulUpdateChannel().getNextValue().get());

		// recovery resets failures and restores the polling interval
		h.failing = false;
		h.leapMinutes(30);
		assertEquals(7, h.requestedUrls.size());
		assertEquals(Integer.valueOf(0), h.consecutiveFailures());
		assertEquals(Integer.valueOf(200), h.sut.getHttpStatusCodeChannel().getNextValue().get());
		assertFalse(h.sut.getPrices().isEmpty());
		h.leapMinutes(5);
		assertEquals(9, h.requestedUrls.size());
		h.sut.deactivate();
	}

	@Test
	public void testFailureInSecondStepRestartsCycle() throws Exception {
		final var h = new Harness();
		// actual response of the undocumented api?type=day-ahead
		h.dayAheadTomorrowBody = "Servlet Feed 2.95-SGalertsSync";
		h.activate(Feed.DAY_AHEAD, 5, 3);
		h.pump();
		// today ok, tomorrow invalid -> failure, no prices yet
		assertEquals(Integer.valueOf(1), h.consecutiveFailures());
		assertTrue(h.sut.getPrices().isEmpty());

		h.dayAheadTomorrowBody = "[]";
		h.leapMinutes(5);
		assertEquals(List.of(ComEdApi.DAY_AHEAD_TODAY_URL, ComEdApi.DAY_AHEAD_TOMORROW_URL,
				ComEdApi.DAY_AHEAD_TODAY_URL, ComEdApi.DAY_AHEAD_TOMORROW_URL), h.requestedUrls);
		assertEquals(Integer.valueOf(0), h.consecutiveFailures());
		assertFalse(h.sut.getPrices().isEmpty());
		h.sut.deactivate();
	}

	@Test
	public void testStepsFor() {
		assertEquals(List.of(Step.DAY_AHEAD_TODAY, Step.DAY_AHEAD_TOMORROW),
				TimeOfUseTariffComEdImpl.stepsFor(Feed.DAY_AHEAD));
		assertEquals(List.of(Step.DAY_AHEAD_TODAY, Step.DAY_AHEAD_TOMORROW, Step.FIVE_MINUTE),
				TimeOfUseTariffComEdImpl.stepsFor(Feed.FIVE_MINUTE));
		assertEquals(List.of(Step.DAY_AHEAD_TODAY, Step.DAY_AHEAD_TOMORROW, Step.CURRENT_HOUR_AVERAGE),
				TimeOfUseTariffComEdImpl.stepsFor(Feed.CURRENT_HOUR_AVERAGE));
	}

	@Test
	public void testChannelsBeforeFirstPoll() throws Exception {
		final var h = new Harness();
		new ComponentTest(h.sut) //
				.addReference("componentManager", new DummyComponentManager(h.clock)) //
				.addReference("meta", new DummyMeta()) //
				.addReference("httpBridgeFactory", ofBridgeImpl(() -> h.fetcher, () -> h.executor)) //
				.activate(MyConfig.create() //
						.setId(ID) //
						.build()) //
				.next(new ComponentTest.TestCase() //
						.output(ID, FEED, "DAY_AHEAD") //
						.output(ID, HTTP_STATUS_CODE, null) //
						.output(ID, LAST_SUCCESSFUL_UPDATE, null) //
						.output(ID, CONSECUTIVE_FAILURES, 0));
		assertTrue(h.requestedUrls.isEmpty());
		h.pump();
		assertEquals(2, h.requestedUrls.size());
		h.sut.deactivate();
	}
}
