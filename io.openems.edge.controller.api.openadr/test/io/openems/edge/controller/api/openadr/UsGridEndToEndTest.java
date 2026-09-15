package io.openems.edge.controller.api.openadr;

import static io.openems.common.utils.DateUtils.roundDownToQuarter;
import static io.openems.edge.controller.api.openadr.ControllerApiOpenAdr.ChannelId.ACTIVE_EVENT_ID;
import static io.openems.edge.controller.api.openadr.ControllerApiOpenAdr.ChannelId.ACTIVE_EVENT_SIGNAL_LEVEL;
import static io.openems.edge.controller.api.openadr.ControllerApiOpenAdr.ChannelId.COMMUNICATION_FAILED;
import static io.openems.edge.controller.api.openadr.ControllerApiOpenAdr.ChannelId.CURTAILMENT_ACTIVE;
import static io.openems.edge.controller.api.openadr.ControllerApiOpenAdr.ChannelId.REGISTRATION_STATE;
import static io.openems.edge.energy.api.EnergyConstants.SUM_PRODUCTION;
import static io.openems.edge.energy.api.EnergyConstants.SUM_UNMANAGED_CONSUMPTION;
import static io.openems.edge.ess.api.ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_GREATER_OR_EQUALS;
import static io.openems.edge.ess.api.ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_LESS_OR_EQUALS;
import static io.openems.edge.evcs.api.ManagedEvcs.ChannelId.SET_CHARGE_POWER_LIMIT;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;

import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.common.test.TimeLeapClock;
import io.openems.edge.common.currency.Currency;
import io.openems.edge.common.sum.DummySum;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.DummyMeta;
import io.openems.edge.controller.ess.timeofusetariff.ControlMode;
import io.openems.edge.controller.ess.timeofusetariff.EnergyScheduler;
import io.openems.edge.controller.ess.timeofusetariff.StateMachine;
import io.openems.edge.controller.test.ControllerTest;
import io.openems.edge.controller.test.DummyController;
import io.openems.edge.energy.api.Environment;
import io.openems.edge.energy.api.handler.EshWithDifferentModes;
import io.openems.edge.energy.api.simulation.GlobalOptimizationContext;
import io.openems.edge.energy.api.simulation.GlobalOptimizationContext.Period;
import io.openems.edge.energy.optimizer.ModeCombinations;
import io.openems.edge.energy.optimizer.SimulationResult;
import io.openems.edge.energy.optimizer.Simulator;
import io.openems.edge.ess.test.DummyManagedSymmetricEss;
import io.openems.edge.evcs.test.DummyManagedEvcs;
import io.openems.edge.predictor.api.prediction.Prediction;
import io.openems.edge.predictor.api.test.DummyPredictor;
import io.openems.edge.predictor.api.test.DummyPredictorManager;
import io.openems.edge.timeofusetariff.api.TimeOfUsePrices;
import io.openems.edge.timeofusetariff.comed.ComEdApi;
import io.openems.edge.timeofusetariff.test.DummyTariffManager;
import io.openems.edge.timeofusetariff.test.DummyTimeOfUseTariffProvider;

/**
 * End-to-end scenario of the US grid workstreams: ComEd day-ahead prices drive
 * the Controller.Ess.Time-Of-Use-Tariff schedule and an OpenADR VTN event
 * curtails ESS and EVCS through Controller.Api.OpenADR for the event window.
 */
public class UsGridEndToEndTest {

	private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
	private static final String CTRL_ID = "ctrlOpenAdr0";
	private static final String ESS_ID = "ess0";
	private static final String EVCS_ID = "evcs0";
	private static final String VEN_ID = "ven-openems-1";
	private static final int ESS_DISCHARGE_LIMIT_W = 500;
	private static final int EVCS_CHARGE_LIMIT_W = 1400;
	private static final long AWAIT_MS = 10_000;

	/** Local Chicago day of the recorded ComEd day-ahead fixture. */
	private static final ZonedDateTime FIXTURE_MIDNIGHT = ZonedDateTime.of(2026, 9, 15, 0, 0, 0, 0, CHICAGO);

	/**
	 * Recorded {@code rrtp/ServletFeed?type=daynexttoday} response for 2026-09-15
	 * in Cent/kWh; cheapest hours 01:00-05:00, peak 16:00-20:00.
	 */
	private static final String COMED_DAY_AHEAD = readFixture("comed_dayahead_2026-09-15.txt");

	@Test
	public void testComEdPricesScheduleChargeGridInCheapHours() throws Exception {
		final var prices = comEdDayAheadPrices();
		final var quarters = prices.asArray();
		assertEquals("24 hours expanded to quarters", 96, quarters.length);
		// 00:00 is 1.5 Cent/kWh -> 15 Currency/MWh
		assertEquals(15.0, quarters[0], 0.001);

		final var esh = tou();
		final var goc = buildGoc(FIXTURE_MIDNIGHT, prices, 10, esh);
		new Simulator(goc); // initializes ESH

		final var balancing = modeIndex(goc, StateMachine.BALANCING);
		final var chargeGrid = modeIndex(goc, StateMachine.CHARGE_GRID);

		final var chargeAtNight = fill(goc, balancing);
		for (var t = LocalTime.of(1, 0); t.isBefore(LocalTime.of(5, 0)); t = t.plusMinutes(15)) {
			chargeAtNight[indexOfPeriodAt(goc, t)] = chargeGrid;
		}
		final var chargeNight = SimulationResult.fromQuarters(goc, chargeAtNight, 0, 0);

		final var chargeAtPeak = fill(goc, balancing);
		for (var t = LocalTime.of(16, 0); t.isBefore(LocalTime.of(20, 0)); t = t.plusMinutes(15)) {
			chargeAtPeak[indexOfPeriodAt(goc, t)] = chargeGrid;
		}
		final var chargePeak = SimulationResult.fromQuarters(goc, chargeAtPeak, 0, 0);

		esh.applySchedule(chargeNight.schedules().get(esh));
		final var cheapHour = esh.getSchedule().get(FIXTURE_MIDNIGHT.withHour(2));
		assertEquals(StateMachine.CHARGE_GRID, cheapHour.mode());
		assertEquals(10.0 /* 1.0 Cent/kWh */, cheapHour.gridBuyPrice(), 0.001);
		final var nightPeriod = chargeNight.periods().get(FIXTURE_MIDNIGHT.withHour(2));
		assertTrue("ESS charges from grid in the cheap hour", nightPeriod.energyFlow().getEss() < 0);
		assertTrue(nightPeriod.energyFlow().getGrid() > 0);

		assertTrue("CHARGE_GRID in the cheap ComEd hours must beat CHARGE_GRID at the peak: "
				+ chargeNight.fitness().gridBuyCostScore() + " vs " + chargePeak.fitness().gridBuyCostScore(),
				chargeNight.fitness().gridBuyCostScore() < chargePeak.fitness().gridBuyCostScore());
		assertEquals(0, chargeNight.fitness().hardConstraintViolations());
	}

	@Test
	public void testVtnSimpleEventCurtailsEssAndEvcsAndRestores() throws Exception {
		// 07:15 local Chicago time = cheap-to-moderate ComEd hour; event covers the
		// current hour 07:00-08:00
		final var now = FIXTURE_MIDNIGHT.withHour(7).withMinute(15).toInstant();
		final var eventStart = FIXTURE_MIDNIGHT.withHour(7).toInstant();

		try (var vtn = new FakeVtn()) {
			vtn.enqueuePollResponse(distributeEvent("evt-us-grid-1", eventStart, 2));
			final var clock = new TimeLeapClock(now, CHICAGO);
			final var sut = new ControllerApiOpenAdrImpl();
			final var test = new ControllerTest(sut) //
					.addReference("componentManager", new DummyComponentManager(clock)) //
					.addReference("cm", new DummyConfigurationAdmin()) //
					.addReference("sum", new DummySum()) //
					.addComponent(new DummyManagedSymmetricEss(ESS_ID)) //
					.addComponent(DummyManagedEvcs.ofDisabled(EVCS_ID)) //
					.activate(MyConfig.create() //
							.setId(CTRL_ID) //
							.setVtnUrl(vtn.url()) //
							.setVenName("openems-ven") //
							.setTls(Tls.SYSTEM_TRUSTSTORE) //
							.setAutoOptIn(true) //
							.setEssId(ESS_ID) //
							.setEvcsIds(EVCS_ID) //
							.setCurtailmentEssDischargeLimitW(ESS_DISCHARGE_LIMIT_W) //
							.setCurtailmentEvcsChargeLimitW(EVCS_CHARGE_LIMIT_W) //
							.setPollIntervalSeconds(10) //
							.build());
			vtn.awaitMessage("oadrCreatedEvent", AWAIT_MS);

			test //
					.next(new TestCase("event active: ESS discharge capped, EVCS limited") //
							.output(REGISTRATION_STATE, RegistrationState.REGISTERED) //
							.output(COMMUNICATION_FAILED, false) //
							.output(CURTAILMENT_ACTIVE, true) //
							.output(ACTIVE_EVENT_ID, "evt-us-grid-1") //
							.output(ACTIVE_EVENT_SIGNAL_LEVEL, 2) //
							.output(ESS_ID, SET_ACTIVE_POWER_GREATER_OR_EQUALS, 0) //
							.output(ESS_ID, SET_ACTIVE_POWER_LESS_OR_EQUALS, ESS_DISCHARGE_LIMIT_W) //
							.output(EVCS_ID, SET_CHARGE_POWER_LIMIT, EVCS_CHARGE_LIMIT_W)) //
					.next(new TestCase("still active at 07:45") //
							.timeleap(clock, 30, MINUTES) //
							.output(CURTAILMENT_ACTIVE, true) //
							.output(ESS_ID, SET_ACTIVE_POWER_GREATER_OR_EQUALS, 0) //
							.output(ESS_ID, SET_ACTIVE_POWER_LESS_OR_EQUALS, ESS_DISCHARGE_LIMIT_W) //
							.output(EVCS_ID, SET_CHARGE_POWER_LIMIT, EVCS_CHARGE_LIMIT_W)) //
					.next(new TestCase("after the event: restored") //
							.timeleap(clock, 30, MINUTES) //
							.output(CURTAILMENT_ACTIVE, false) //
							.output(ACTIVE_EVENT_SIGNAL_LEVEL, -1) //
							.output(ESS_ID, SET_ACTIVE_POWER_GREATER_OR_EQUALS, null) //
							.output(ESS_ID, SET_ACTIVE_POWER_LESS_OR_EQUALS, null) //
							.output(EVCS_ID, SET_CHARGE_POWER_LIMIT, null)) //
					.deactivate();
		}
	}

	/*
	 * Helpers
	 */

	private static TimeOfUsePrices comEdDayAheadPrices() throws Exception {
		final var hourly = ComEdApi.parseDayAhead(COMED_DAY_AHEAD);
		assertEquals(FIXTURE_MIDNIGHT.toInstant(), hourly.firstKey());
		return ComEdApi.combine(hourly, ImmutableSortedMap.of(), ImmutableSortedMap.of(), 0);
	}

	private static EshWithDifferentModes<StateMachine, EnergyScheduler.OptimizationContext, Void> tou() {
		return EnergyScheduler.buildEnergyScheduleHandler(new DummyController("ctrlEssTimeOfUseTariff0"),
				() -> new EnergyScheduler.Config(ControlMode.CHARGE_CONSUMPTION.modes, null, null));
	}

	private static GlobalOptimizationContext buildGoc(ZonedDateTime now, TimeOfUsePrices prices, int essSoc,
			EshWithDifferentModes<StateMachine, EnergyScheduler.OptimizationContext, Void> esh) throws Exception {
		final var clock = new TimeLeapClock(now.toInstant(), CHICAGO);
		final var componentManager = new DummyComponentManager(clock);
		final var sum = new DummySum() //
				.withEssCapacity(22_000) //
				.withEssSoc(essSoc) //
				.withEssMinDischargePower(-8_000) //
				.withEssMaxDischargePower(8_000);
		final var predictionStart = roundDownToQuarter(now.toInstant());
		final var consumption = new Integer[48 * 4];
		Arrays.fill(consumption, 1000 /* W */);
		final var production = new Integer[48 * 4];
		Arrays.fill(production, 0);
		final var predictorManager = new DummyPredictorManager(//
				new DummyPredictor("predictor0", componentManager, Prediction.from(predictionStart, production),
						SUM_PRODUCTION), //
				new DummyPredictor("predictor1", componentManager, Prediction.from(predictionStart, consumption),
						SUM_UNMANAGED_CONSUMPTION));
		final var tariffManager = new DummyTariffManager() //
				.withTariffGridBuyProvider(new DummyTimeOfUseTariffProvider(clock, prices));

		return GlobalOptimizationContext.builder() //
				.setComponentManager(componentManager) //
				.setMeta(new DummyMeta() //
						.withCurrency(Currency.USD) //
						.withGridBuyHardLimit(20_000) //
						.withGridSellHardLimit(20_000) //
						.withGridSellHardLimitWithBuffer(19_000)) //
				.setEnvironment(Environment.TEST) //
				.setEnergyScheduleHandlers(ImmutableList.of(esh)) //
				.setSum(sum) //
				.setPredictorManager(predictorManager) //
				.setTariffManager(tariffManager) //
				.build();
	}

	private static int indexOfPeriodAt(GlobalOptimizationContext goc, LocalTime time) {
		return goc.periods().stream() //
				.filter(p -> p.time().toLocalTime().equals(time) //
						|| (p instanceof Period.Hour h && h.quarterPeriods().stream() //
								.anyMatch(q -> q.time().toLocalTime().equals(time)))) //
				.mapToInt(Period::index) //
				.findFirst() //
				.orElseThrow(() -> new IllegalStateException("No period at " + time));
	}

	private static int modeIndex(GlobalOptimizationContext goc, StateMachine mode) {
		return ModeCombinations.fromGlobalOptimizationContext(goc).combinations().stream() //
				.filter(c -> c.modes().stream().anyMatch(m -> mode.name().equals(m.name()))) //
				.mapToInt(ModeCombinations.ModeCombination::index) //
				.findFirst() //
				.orElseThrow(() -> new IllegalStateException("Mode not found: " + mode));
	}

	private static int[] fill(GlobalOptimizationContext goc, int modeIndex) {
		final var schedule = new int[goc.periods().size()];
		Arrays.fill(schedule, modeIndex);
		return schedule;
	}

	private static String readFixture(String name) {
		try (var stream = UsGridEndToEndTest.class.getResourceAsStream("fixtures/" + name)) {
			if (stream == null) {
				throw new IllegalStateException("Fixture not found: " + name);
			}
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static final String OADR_NAMESPACES = """
			xmlns:oadr="http://openadr.org/oadr-2.0b/2012/07"
			xmlns:ei="http://docs.oasis-open.org/ns/energyinterop/201110"
			xmlns:pyld="http://docs.oasis-open.org/ns/energyinterop/201110/payloads"
			xmlns:xcal="urn:ietf:params:xml:ns:icalendar-2.0"
			xmlns:emix="http://docs.oasis-open.org/ns/emix/2011/06"
			xmlns:strm="urn:ietf:params:xml:ns:icalendar-2.0:stream\"""";

	/**
	 * Builds an oadrDistributeEvent with one SIMPLE signal of one hour.
	 * 
	 * @param eventId the event ID
	 * @param start   start of the event
	 * @param level   the SIMPLE level (0-3)
	 * @return the XML
	 */
	private static String distributeEvent(String eventId, Instant start, int level) {
		return """
				<?xml version="1.0" encoding="UTF-8"?>
				<oadr:oadrPayload %s>
				  <oadr:oadrSignedObject>
				    <oadr:oadrDistributeEvent ei:schemaVersion="2.0b">
				      <ei:eiResponse>
				        <ei:responseCode>200</ei:responseCode>
				        <ei:responseDescription>OK</ei:responseDescription>
				        <pyld:requestID></pyld:requestID>
				      </ei:eiResponse>
				      <pyld:requestID>req-dist-1</pyld:requestID>
				      <ei:vtnID>openems-vtn</ei:vtnID>
				      <oadr:oadrEvent>
				        <ei:eiEvent>
				          <ei:eventDescriptor>
				            <ei:eventID>%s</ei:eventID>
				            <ei:modificationNumber>0</ei:modificationNumber>
				            <ei:priority>1</ei:priority>
				            <ei:eiMarketContext>
				              <emix:marketContext>http://openems.io/marketcontext/us-grid</emix:marketContext>
				            </ei:eiMarketContext>
				            <ei:createdDateTime>%s</ei:createdDateTime>
				            <ei:eventStatus>active</ei:eventStatus>
				            <ei:testEvent>false</ei:testEvent>
				          </ei:eventDescriptor>
				          <ei:eiActivePeriod>
				            <xcal:properties>
				              <xcal:dtstart><xcal:date-time>%s</xcal:date-time></xcal:dtstart>
				              <xcal:duration><xcal:duration>PT1H</xcal:duration></xcal:duration>
				            </xcal:properties>
				          </ei:eiActivePeriod>
				          <ei:eiEventSignals>
				            <ei:eiEventSignal>
				              <strm:intervals>
				                <ei:interval>
				                  <xcal:dtstart><xcal:date-time>%s</xcal:date-time></xcal:dtstart>
				                  <xcal:duration><xcal:duration>PT1H</xcal:duration></xcal:duration>
				                  <xcal:uid><xcal:text>0</xcal:text></xcal:uid>
				                  <ei:signalPayload><ei:payloadFloat><ei:value>%d</ei:value></ei:payloadFloat></ei:signalPayload>
				                </ei:interval>
				              </strm:intervals>
				              <ei:signalName>simple</ei:signalName>
				              <ei:signalType>level</ei:signalType>
				              <ei:signalID>sig-1</ei:signalID>
				              <ei:currentValue><ei:payloadFloat><ei:value>%d</ei:value></ei:payloadFloat></ei:currentValue>
				            </ei:eiEventSignal>
				          </ei:eiEventSignals>
				          <ei:eiTarget><ei:venID>%s</ei:venID></ei:eiTarget>
				        </ei:eiEvent>
				        <oadr:oadrResponseRequired>always</oadr:oadrResponseRequired>
				      </oadr:oadrEvent>
				    </oadr:oadrDistributeEvent>
				  </oadr:oadrSignedObject>
				</oadr:oadrPayload>
				""".formatted(OADR_NAMESPACES, eventId, start.minusSeconds(300), start, start, level, level, VEN_ID);
	}
}
