package io.openems.edge.controller.api.openadr;

import static io.openems.edge.controller.api.openadr.ControllerApiOpenAdr.ChannelId.ACTIVE_EVENT_END;
import static io.openems.edge.controller.api.openadr.ControllerApiOpenAdr.ChannelId.ACTIVE_EVENT_ID;
import static io.openems.edge.controller.api.openadr.ControllerApiOpenAdr.ChannelId.ACTIVE_EVENT_PRICE;
import static io.openems.edge.controller.api.openadr.ControllerApiOpenAdr.ChannelId.ACTIVE_EVENT_SIGNAL_LEVEL;
import static io.openems.edge.controller.api.openadr.ControllerApiOpenAdr.ChannelId.ACTIVE_EVENT_START;
import static io.openems.edge.controller.api.openadr.ControllerApiOpenAdr.ChannelId.COMMUNICATION_FAILED;
import static io.openems.edge.controller.api.openadr.ControllerApiOpenAdr.ChannelId.CURTAILMENT_ACTIVE;
import static io.openems.edge.controller.api.openadr.ControllerApiOpenAdr.ChannelId.EVENT_COUNT;
import static io.openems.edge.controller.api.openadr.ControllerApiOpenAdr.ChannelId.OPT_STATE;
import static io.openems.edge.controller.api.openadr.ControllerApiOpenAdr.ChannelId.REGISTRATION_STATE;
import static io.openems.edge.controller.api.openadr.ControllerApiOpenAdr.ChannelId.SET_OPT_STATE;
import static io.openems.edge.controller.api.openadr.ControllerApiOpenAdr.ChannelId.VEN_ID;
import static io.openems.edge.ess.api.ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_EQUALS;
import static io.openems.edge.ess.api.ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_GREATER_OR_EQUALS;
import static io.openems.edge.ess.api.ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_LESS_OR_EQUALS;
import static io.openems.edge.evcs.api.ManagedEvcs.ChannelId.SET_CHARGE_POWER_LIMIT;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.Test;

import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.common.test.TimeLeapClock;
import io.openems.edge.common.sum.DummySum;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.controller.api.openadr.oadr.Fixtures;
import io.openems.edge.controller.api.openadr.oadr.OadrMessages;
import io.openems.edge.controller.api.openadr.oadr.OadrXml;
import io.openems.edge.controller.test.ControllerTest;
import io.openems.edge.ess.test.DummyManagedSymmetricEss;
import io.openems.edge.evcs.test.DummyManagedEvcs;

public class ControllerApiOpenAdrImplTest {

	private static final String CTRL_ID = "ctrlOpenAdr0";
	private static final String ESS_ID = "ess0";
	private static final String EVCS_ID = "evcs0";

	/** 15 minutes into the SIMPLE fixture event (12:00 - 13:00). */
	private static final Instant ACTIVE = Instant.parse("2026-09-15T12:15:00Z");
	private static final long AWAIT_MS = 10_000;

	private static int awaitPolls(FakeVtn vtn, int count) throws InterruptedException {
		var deadline = System.currentTimeMillis() + AWAIT_MS;
		while (vtn.received("oadrPoll").size() < count && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}
		var polls = vtn.received("oadrPoll").size();
		assertTrue("expected at least " + count + " polls, got " + polls, polls >= count);
		return polls;
	}

	private static TimeLeapClock clockAt(Instant instant) {
		return new TimeLeapClock(instant, ZoneOffset.UTC);
	}

	private static ControllerTest prepare(ControllerApiOpenAdrImpl sut, TimeLeapClock clock, FakeVtn vtn,
			MyConfig.Builder config) throws Exception {
		return new ControllerTest(sut) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("sum", new DummySum()) //
				.addComponent(new DummyManagedSymmetricEss(ESS_ID)) //
				.addComponent(DummyManagedEvcs.ofDisabled(EVCS_ID)) //
				.activate(config //
						.setId(CTRL_ID) //
						.setVtnUrl(vtn.url()) //
						.setTls(Tls.SYSTEM_TRUSTSTORE) //
						.build());
	}

	@Test
	public void testBackoff() {
		var base = Duration.ofSeconds(10);
		assertEquals(Duration.ofSeconds(10), ControllerApiOpenAdrImpl.backoff(base, 0));
		assertEquals(Duration.ofSeconds(10), ControllerApiOpenAdrImpl.backoff(base, 1));
		assertEquals(Duration.ofSeconds(20), ControllerApiOpenAdrImpl.backoff(base, 2));
		assertEquals(Duration.ofSeconds(40), ControllerApiOpenAdrImpl.backoff(base, 3));
		assertEquals(Duration.ofSeconds(80), ControllerApiOpenAdrImpl.backoff(base, 4));
		// capped
		assertEquals(ControllerApiOpenAdrImpl.MAX_BACKOFF, ControllerApiOpenAdrImpl.backoff(base, 20));
		assertEquals(ControllerApiOpenAdrImpl.MAX_BACKOFF, ControllerApiOpenAdrImpl.backoff(base, 1000));
	}

	@Test
	public void testActivateRegistersWithFakeVtn() throws Exception {
		try (var vtn = new FakeVtn()) {
			var sut = new ControllerApiOpenAdrImpl();
			var clock = clockAt(Instant.parse("2026-09-15T10:00:00Z"));
			final var test = prepare(sut, clock, vtn, MyConfig.create() //
					.setVenName("openems-test-ven") //
					.setRegistrationId("reg-4711"));

			vtn.awaitMessage("oadrRegisterReport", AWAIT_MS);
			vtn.awaitMessage("oadrUpdateReport", AWAIT_MS);
			var query = vtn.awaitMessage("oadrQueryRegistration", AWAIT_MS);
			assertEquals("EiRegisterParty", query.service());
			assertEquals("application/xml", query.contentType());
			var create = vtn.awaitMessage("oadrCreatePartyRegistration", AWAIT_MS);
			assertTrue(create.body().contains("openems-test-ven"));
			assertTrue(create.body().contains("reg-4711"));
			assertEquals("EiReport", vtn.received("oadrRegisterReport").get(0).service());
			// oadrCreateReport in the oadrRegisterReport response is acknowledged
			vtn.awaitMessage("oadrCreatedReport", AWAIT_MS);
			// the very first poll uses the OadrPoll service
			assertEquals("OadrPoll", vtn.awaitMessage("oadrPoll", AWAIT_MS).service());

			test //
					.next(new TestCase() //
							.output(REGISTRATION_STATE, RegistrationState.REGISTERED) //
							.output(VEN_ID, "ven-openems-1") //
							.output(EVENT_COUNT, 0) //
							.output(CURTAILMENT_ACTIVE, false) //
							.output(ACTIVE_EVENT_SIGNAL_LEVEL, -1) //
							.output(COMMUNICATION_FAILED, false)) //
					.deactivate();

			// deactivate cancels the registration
			vtn.awaitMessage("oadrCancelPartyRegistration", AWAIT_MS);
		}
	}

	@Test
	public void testPollDistributesEventAndRespondsOptIn() throws Exception {
		try (var vtn = new FakeVtn()) {
			vtn.enqueuePollResponse(Fixtures.read(Fixtures.DISTRIBUTE_EVENT_SIMPLE));
			var sut = new ControllerApiOpenAdrImpl();
			var clock = clockAt(Instant.parse("2026-09-15T11:00:00Z"));
			final var test = prepare(sut, clock, vtn, MyConfig.create());

			var created = vtn.awaitMessage("oadrCreatedEvent", AWAIT_MS);
			assertEquals("EiEvent", created.service());
			var root = OadrXml.parse(created.body());
			assertEquals("optIn", OadrXml.descendantText(root, "optType").get());
			assertEquals("evt-simple-1", OadrXml.descendantText(root, "eventID").get());
			assertEquals("req-dist-1", OadrXml.descendantText(OadrXml.descendant(root, "eventResponse").get(),
					"requestID").get());

			test //
					.next(new TestCase("before the event") //
							.output(EVENT_COUNT, 1) //
							.output(ACTIVE_EVENT_ID, null) //
							.output(CURTAILMENT_ACTIVE, false)) //
					.deactivate();
		}
	}

	@Test
	public void testPollHttpErrorSetsCommunicationFailedAndBacksOff() throws Exception {
		try (var vtn = new FakeVtn()) {
			vtn.setPollStatusCode(500);
			var sut = new ControllerApiOpenAdrImpl();
			var clock = clockAt(Instant.parse("2026-09-15T11:00:00Z"));
			final var test = prepare(sut, clock, vtn, MyConfig.create().setPollIntervalSeconds(10));

			vtn.awaitMessage("oadrPoll", AWAIT_MS);
			// wait for the executor to process the failure
			Thread.sleep(500);
			test.next(new TestCase() //
					.output(REGISTRATION_STATE, RegistrationState.REGISTERED) //
					.output(COMMUNICATION_FAILED, true));
			// the VTN requested a poll frequency of 30s (oadrRequestedOadrPollFreq)
			// first failure: retry after the regular interval -> second failure
			clock.leap(30, SECONDS);
			final var polls = awaitPolls(vtn, 2);
			Thread.sleep(500);

			// second failure: backoff doubled to 60s -> no poll within 45s
			clock.leap(45, SECONDS);
			Thread.sleep(1500);
			assertEquals(polls, vtn.received("oadrPoll").size());

			// after the backoff the poll is retried and recovers
			vtn.setPollStatusCode(200);
			clock.leap(20, SECONDS);
			awaitPolls(vtn, polls + 1);
			Thread.sleep(500);
			test //
					.next(new TestCase() //
							.output(COMMUNICATION_FAILED, false)) //
					.deactivate();
		}
	}

	@Test
	public void testPollFallsBackToEiEventOn404() throws Exception {
		try (var vtn = new FakeVtn()) {
			vtn.setPollStatusCode(404);
			var sut = new ControllerApiOpenAdrImpl();
			var clock = clockAt(Instant.parse("2026-09-15T11:00:00Z"));
			final var test = prepare(sut, clock, vtn, MyConfig.create());

			awaitPolls(vtn, 2);
			var polls = vtn.received("oadrPoll");
			assertEquals("OadrPoll", polls.get(0).service());
			assertEquals("EiEvent", polls.get(1).service());
			test.deactivate();
		}
	}

	@Test
	public void testSimpleEventCurtailsEssThroughAllPhases() throws Exception {
		try (var vtn = new FakeVtn()) {
			vtn.enqueuePollResponse(Fixtures.read(Fixtures.DISTRIBUTE_EVENT_SIMPLE));
			var sut = new ControllerApiOpenAdrImpl();
			// ramp-up starts at 11:50
			var clock = clockAt(Instant.parse("2026-09-15T11:45:00Z"));
			final var test = prepare(sut, clock, vtn, MyConfig.create() //
					.setEssId(ESS_ID) //
					.setCurtailmentEssDischargeLimitW(500));
			vtn.awaitMessage("oadrCreatedEvent", AWAIT_MS);

			test //
					.next(new TestCase("before") //
							.output(CURTAILMENT_ACTIVE, false) //
							.output(ACTIVE_EVENT_ID, null) //
							.output(ACTIVE_EVENT_SIGNAL_LEVEL, -1) //
							.output(OPT_STATE, OptState.UNDEFINED) //
							.output(ESS_ID, SET_ACTIVE_POWER_GREATER_OR_EQUALS, null) //
							.output(ESS_ID, SET_ACTIVE_POWER_LESS_OR_EQUALS, null)) //
					.next(new TestCase("ramp-up: moderate level, no charging") //
							.timeleap(clock, 10, MINUTES) //
							.output(CURTAILMENT_ACTIVE, true) //
							.output(ACTIVE_EVENT_ID, "evt-simple-1") //
							.output(ACTIVE_EVENT_SIGNAL_LEVEL, 1) //
							.output(ACTIVE_EVENT_START, Instant.parse("2026-09-15T12:00:00Z").getEpochSecond()) //
							.output(ACTIVE_EVENT_END, Instant.parse("2026-09-15T13:00:00Z").getEpochSecond()) //
							.output(OPT_STATE, OptState.OPT_IN) //
							.output(ESS_ID, SET_ACTIVE_POWER_GREATER_OR_EQUALS, 0) //
							.output(ESS_ID, SET_ACTIVE_POWER_LESS_OR_EQUALS, null)) //
					.next(new TestCase("active level 2: discharge cap") //
							.timeleap(clock, 20, MINUTES) //
							.output(ACTIVE_EVENT_SIGNAL_LEVEL, 2) //
							.output(ESS_ID, SET_ACTIVE_POWER_GREATER_OR_EQUALS, 0) //
							.output(ESS_ID, SET_ACTIVE_POWER_LESS_OR_EQUALS, 500)) //
					.next(new TestCase("active level 3: hold SoC") //
							.timeleap(clock, 30, MINUTES) //
							.output(ACTIVE_EVENT_SIGNAL_LEVEL, 3) //
							.output(ESS_ID, SET_ACTIVE_POWER_EQUALS, 0) //
							.output(ESS_ID, SET_ACTIVE_POWER_LESS_OR_EQUALS, null)) //
					.next(new TestCase("recovery: moderate level") //
							.timeleap(clock, 20, MINUTES) //
							.output(CURTAILMENT_ACTIVE, true) //
							.output(ACTIVE_EVENT_SIGNAL_LEVEL, 1) //
							.output(ESS_ID, SET_ACTIVE_POWER_GREATER_OR_EQUALS, 0) //
							.output(ESS_ID, SET_ACTIVE_POWER_EQUALS, null)) //
					.next(new TestCase("after: everything restored") //
							.timeleap(clock, 10, MINUTES) //
							.output(CURTAILMENT_ACTIVE, false) //
							.output(ACTIVE_EVENT_ID, null) //
							.output(ACTIVE_EVENT_SIGNAL_LEVEL, -1) //
							.output(ESS_ID, SET_ACTIVE_POWER_GREATER_OR_EQUALS, null) //
							.output(ESS_ID, SET_ACTIVE_POWER_LESS_OR_EQUALS, null) //
							.output(ESS_ID, SET_ACTIVE_POWER_EQUALS, null)) //
					.deactivate();
		}
	}

	@Test
	public void testSimpleEventCurtailsEvcs() throws Exception {
		try (var vtn = new FakeVtn()) {
			vtn.enqueuePollResponse(Fixtures.read(Fixtures.DISTRIBUTE_EVENT_SIMPLE));
			var sut = new ControllerApiOpenAdrImpl();
			var clock = clockAt(ACTIVE);
			final var test = prepare(sut, clock, vtn, MyConfig.create() //
					.setEvcsIds(EVCS_ID) //
					.setCurtailmentEvcsChargeLimitW(1400));
			vtn.awaitMessage("oadrCreatedEvent", AWAIT_MS);

			test //
					.next(new TestCase("active") //
							.output(CURTAILMENT_ACTIVE, true) //
							.output(ACTIVE_EVENT_SIGNAL_LEVEL, 2) //
							.output(COMMUNICATION_FAILED, false) //
							.output(EVCS_ID, SET_CHARGE_POWER_LIMIT, 1400)) //
					.next(new TestCase("after") //
							.timeleap(clock, 60, MINUTES) //
							.output(CURTAILMENT_ACTIVE, false) //
							.output(EVCS_ID, SET_CHARGE_POWER_LIMIT, null)) //
					.deactivate();
		}
	}

	@Test
	public void testPriceEventCurtailsAboveThreshold() throws Exception {
		try (var vtn = new FakeVtn()) {
			vtn.enqueuePollResponse(Fixtures.read(Fixtures.DISTRIBUTE_EVENT_PRICE));
			var sut = new ControllerApiOpenAdrImpl();
			// PRICE event 14:00 - 16:00: 50 USD/MWh, then 250 USD/MWh
			var clock = clockAt(Instant.parse("2026-09-15T14:30:00Z"));
			final var test = prepare(sut, clock, vtn, MyConfig.create() //
					.setEssId(ESS_ID) //
					.setEvcsIds(EVCS_ID) //
					.setCurtailmentEssDischargeLimitW(0) //
					.setCurtailmentEvcsChargeLimitW(0) //
					.setPriceSignalThresholdPerMwh(100));
			vtn.awaitMessage("oadrCreatedEvent", AWAIT_MS);

			test //
					.next(new TestCase("price below threshold") //
							.output(ACTIVE_EVENT_ID, "evt-price-1") //
							.output(ACTIVE_EVENT_PRICE, 50.0) //
							.output(ACTIVE_EVENT_SIGNAL_LEVEL, 0) //
							.output(CURTAILMENT_ACTIVE, false) //
							.output(ESS_ID, SET_ACTIVE_POWER_LESS_OR_EQUALS, null) //
							.output(EVCS_ID, SET_CHARGE_POWER_LIMIT, null)) //
					.next(new TestCase("price above threshold") //
							.timeleap(clock, 60, MINUTES) //
							.output(ACTIVE_EVENT_PRICE, 250.0) //
							.output(ACTIVE_EVENT_SIGNAL_LEVEL, 2) //
							.output(CURTAILMENT_ACTIVE, true) //
							.output(ESS_ID, SET_ACTIVE_POWER_GREATER_OR_EQUALS, 0) //
							.output(ESS_ID, SET_ACTIVE_POWER_LESS_OR_EQUALS, 0) //
							.output(EVCS_ID, SET_CHARGE_POWER_LIMIT, 0)) //
					.next(new TestCase("after") //
							.timeleap(clock, 60, MINUTES) //
							.output(ACTIVE_EVENT_PRICE, null) //
							.output(CURTAILMENT_ACTIVE, false)) //
					.deactivate();
		}
	}

	@Test
	public void testOptOutPreventsCurtailment() throws Exception {
		try (var vtn = new FakeVtn()) {
			vtn.enqueuePollResponse(Fixtures.read(Fixtures.DISTRIBUTE_EVENT_SIMPLE));
			var sut = new ControllerApiOpenAdrImpl();
			var clock = clockAt(ACTIVE);
			final var test = prepare(sut, clock, vtn, MyConfig.create() //
					.setAutoOptIn(false) //
					.setEssId(ESS_ID) //
					.setCurtailmentEssDischargeLimitW(500));
			var created = vtn.awaitMessage("oadrCreatedEvent", AWAIT_MS);
			assertEquals("optOut", OadrXml.descendantText(OadrXml.parse(created.body()), "optType").get());
			assertEquals(OptState.OPT_OUT, sut.getOptState("evt-simple-1"));

			test //
					.next(new TestCase("opted out: no curtailment") //
							.output(EVENT_COUNT, 1) //
							.output(ACTIVE_EVENT_ID, null) //
							.output(CURTAILMENT_ACTIVE, false) //
							.output(ESS_ID, SET_ACTIVE_POWER_LESS_OR_EQUALS, null));

			// opt in via JSON-RPC style API -> oadrCreateOpt is sent
			sut.setOptState("evt-simple-1", OptState.OPT_IN);
			var opt = vtn.awaitMessage("oadrCreateOpt", AWAIT_MS);
			assertEquals("EiOpt", opt.service());
			assertEquals("optIn", OadrXml.descendantText(OadrXml.parse(opt.body()), "optType").get());

			test //
					.next(new TestCase("opted in: curtailment") //
							.output(ACTIVE_EVENT_ID, "evt-simple-1") //
							.output(OPT_STATE, OptState.OPT_IN) //
							.output(CURTAILMENT_ACTIVE, true) //
							.output(ESS_ID, SET_ACTIVE_POWER_LESS_OR_EQUALS, 500)) //
					.next(new TestCase("opt out via write channel") //
							.input(SET_OPT_STATE, OptState.OPT_OUT) //
							.output(ACTIVE_EVENT_ID, null) //
							.output(OPT_STATE, OptState.UNDEFINED) //
							.output(CURTAILMENT_ACTIVE, false) //
							.output(ESS_ID, SET_ACTIVE_POWER_LESS_OR_EQUALS, null)) //
					.deactivate();
			assertEquals(OptState.OPT_OUT, sut.getOptState("evt-simple-1"));
			assertEquals(2, vtn.received("oadrCreateOpt").size());
		}
	}

	@Test
	public void testCancelledEventStopsCurtailment() throws Exception {
		try (var vtn = new FakeVtn()) {
			vtn.enqueuePollResponse(Fixtures.read(Fixtures.DISTRIBUTE_EVENT_SIMPLE));
			var sut = new ControllerApiOpenAdrImpl();
			var clock = clockAt(ACTIVE);
			final var test = prepare(sut, clock, vtn, MyConfig.create() //
					.setEssId(ESS_ID) //
					.setCurtailmentEssDischargeLimitW(500));
			vtn.awaitMessage("oadrCreatedEvent", AWAIT_MS);

			test.next(new TestCase("active") //
					.output(CURTAILMENT_ACTIVE, true) //
					.output(EVENT_COUNT, 1) //
					.output(ESS_ID, SET_ACTIVE_POWER_LESS_OR_EQUALS, 500));

			// cancellation of evt-simple-1 plus a second (later) event
			sut.handleDistributeEvent(Fixtures.parse(Fixtures.DISTRIBUTE_EVENT_TWO_EVENTS_CANCEL));
			assertEquals(2, vtn.received("oadrCreatedEvent").size());
			assertEquals(OptState.UNDEFINED, sut.getOptState("evt-simple-1"));

			test //
					.next(new TestCase("cancelled") //
							.output(CURTAILMENT_ACTIVE, false) //
							.output(EVENT_COUNT, 1) //
							.output(ACTIVE_EVENT_ID, null) //
							.output(ESS_ID, SET_ACTIVE_POWER_LESS_OR_EQUALS, null)) //
					.next(new TestCase("second event becomes active") //
							.timeleap(clock, 20, MINUTES) //
							.output(ACTIVE_EVENT_ID, "evt-simple-2") //
							.output(ACTIVE_EVENT_SIGNAL_LEVEL, 1) //
							.output(CURTAILMENT_ACTIVE, true) //
							.output(ESS_ID, SET_ACTIVE_POWER_GREATER_OR_EQUALS, 0)) //
					.deactivate();
		}
	}

	@Test
	public void testOlderModificationNumberIsIgnored() throws Exception {
		try (var vtn = new FakeVtn()) {
			var sut = new ControllerApiOpenAdrImpl();
			var clock = clockAt(ACTIVE);
			final var test = prepare(sut, clock, vtn, MyConfig.create().setEssId(ESS_ID));
			vtn.awaitMessage("oadrPoll", AWAIT_MS);

			// modificationNumber 1 (cancelled) first, then modificationNumber 0
			sut.handleDistributeEvent(Fixtures.parse(Fixtures.DISTRIBUTE_EVENT_TWO_EVENTS_CANCEL));
			sut.handleDistributeEvent(Fixtures.parse(Fixtures.DISTRIBUTE_EVENT_SIMPLE));
			var event = sut.getEvents().stream().filter(e -> e.eventId().equals("evt-simple-1")).findFirst()
					.get();
			assertEquals(1, event.modificationNumber());
			assertTrue(event.isCancelled());

			test //
					.next(new TestCase() //
							.output(EVENT_COUNT, 1) //
							.output(CURTAILMENT_ACTIVE, false)) //
					.deactivate();
		}
	}

	@Test
	public void testMissingComponentsDoNotThrow() throws Exception {
		try (var vtn = new FakeVtn()) {
			vtn.enqueuePollResponse(Fixtures.read(Fixtures.DISTRIBUTE_EVENT_SIMPLE));
			var sut = new ControllerApiOpenAdrImpl();
			var clock = clockAt(ACTIVE);
			final var test = prepare(sut, clock, vtn, MyConfig.create() //
					.setEssId("essMissing") //
					.setEvcsIds("evcsMissing") //
					.setEvseIds("evseMissing") //
					.setHeatPumpIds("heatPumpMissing"));
			vtn.awaitMessage("oadrCreatedEvent", AWAIT_MS);

			test //
					.next(new TestCase() //
							.output(CURTAILMENT_ACTIVE, true) //
							.output(COMMUNICATION_FAILED, true)) //
					.deactivate();
		}
	}

	@Test
	public void testEventOfOtherVenIsIgnored() throws Exception {
		try (var vtn = new FakeVtn()) {
			var sut = new ControllerApiOpenAdrImpl();
			var clock = clockAt(ACTIVE);
			final var test = prepare(sut, clock, vtn, MyConfig.create().setEssId(ESS_ID));
			vtn.awaitMessage("oadrPoll", AWAIT_MS);

			var xml = Fixtures.read(Fixtures.DISTRIBUTE_EVENT_SIMPLE) //
					.replace("<ei:venID>ven-openems-1</ei:venID>", "<ei:venID>ven-other</ei:venID>");
			sut.handleDistributeEvent(OadrXml.parse(xml));
			assertTrue(sut.getEvents().isEmpty());

			test //
					.next(new TestCase() //
							.output(EVENT_COUNT, 0) //
							.output(CURTAILMENT_ACTIVE, false)) //
					.deactivate();
		}
	}

	@Test
	public void testMarketContextFilter() throws Exception {
		try (var vtn = new FakeVtn()) {
			var sut = new ControllerApiOpenAdrImpl();
			var clock = clockAt(ACTIVE);
			final var test = prepare(sut, clock, vtn, MyConfig.create() //
					.setMarketContext("http://openems.io/marketcontext/other"));
			vtn.awaitMessage("oadrPoll", AWAIT_MS);

			sut.handleDistributeEvent(Fixtures.parse(Fixtures.DISTRIBUTE_EVENT_SIMPLE));
			assertTrue(sut.getEvents().isEmpty());
			test.deactivate();
		}
	}

	@Test
	public void testTelemetryReportContainsConfiguredDevices() throws Exception {
		try (var vtn = new FakeVtn()) {
			var sut = new ControllerApiOpenAdrImpl();
			var clock = clockAt(ACTIVE);
			final var test = prepare(sut, clock, vtn, MyConfig.create() //
					.setEssId(ESS_ID) //
					.setEvcsIds(EVCS_ID) //
					.setReportIntervalSeconds(60));
			var report = vtn.awaitMessage("oadrUpdateReport", AWAIT_MS);
			assertEquals("EiReport", report.service());
			var root = OadrXml.parse(report.body());
			var rids = OadrXml.children(OadrXml.descendant(root, "interval").get(), "oadrReportPayload") //
					.map(p -> OadrXml.childText(p, "rID").get()).toList();
			assertTrue(rids.contains(ControllerApiOpenAdrImpl.RID_ESS_POWER));
			assertTrue(rids.contains(ControllerApiOpenAdrImpl.RID_EVCS_POWER));
			assertFalse(rids.contains(ControllerApiOpenAdrImpl.RID_GRID_POWER));
			assertEquals("rr-1", OadrXml.descendantText(root, "reportRequestID").get());
			test.deactivate();
		}
	}

	@Test
	public void testCreatedEventResponseIsBuiltFromParsedFixture() throws Exception {
		// round trip: fixture -> parse -> oadrCreatedEvent -> parse
		var distribute = OadrMessages.parseDistributeEvent(Fixtures.parse(Fixtures.DISTRIBUTE_EVENT_SIMPLE));
		var event = distribute.events().get(0);
		var xml = OadrMessages.createdEvent("req-x", "ven-openems-1",
				List.of(new OadrMessages.EventResponse(event.eventId(), event.modificationNumber(),
						distribute.requestId(), OptState.OPT_OUT.getOadrOptType())));
		var root = OadrXml.parse(xml);
		assertEquals("oadrCreatedEvent", OadrMessages.messageName(root));
		assertEquals("optOut", OadrXml.descendantText(root, "optType").get());
		assertEquals("req-dist-1", OadrXml
				.descendantText(OadrXml.descendant(root, "eventResponse").get(), "requestID").get());
	}
}
