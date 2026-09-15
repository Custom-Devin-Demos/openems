package io.openems.edge.energy.optimizer;

import static io.jenetics.engine.Limits.byExecutionTime;
import static io.jenetics.engine.Limits.byFixedGeneration;
import static io.openems.common.utils.DateUtils.roundDownToQuarter;
import static io.openems.edge.controller.ess.timeofusetariff.Utils.ESS_CHARGE_C_RATE;
import static io.openems.edge.energy.api.EnergyConstants.SUM_PRODUCTION;
import static io.openems.edge.energy.api.EnergyConstants.SUM_UNMANAGED_CONSUMPTION;
import static io.openems.edge.energy.optimizer.SimulationResult.EMPTY_SIMULATION_RESULT;
import static java.lang.Math.round;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;

import io.jenetics.util.RandomRegistry;
import io.openems.common.test.TimeLeapClock;
import io.openems.edge.common.currency.Currency;
import io.openems.edge.common.sum.DummySum;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.DummyMeta;
import io.openems.edge.controller.ess.timeofusetariff.ControlMode;
import io.openems.edge.controller.ess.timeofusetariff.EnergyScheduler;
import io.openems.edge.controller.ess.timeofusetariff.StateMachine;
import io.openems.edge.controller.test.DummyController;
import io.openems.edge.energy.api.Environment;
import io.openems.edge.energy.api.handler.EnergyScheduleHandler;
import io.openems.edge.energy.api.handler.EshWithDifferentModes;
import io.openems.edge.energy.api.simulation.GlobalOptimizationContext;
import io.openems.edge.energy.api.simulation.GlobalOptimizationContext.Period;
import io.openems.edge.energy.api.simulation.periods.PeriodData.Price;
import io.openems.edge.predictor.api.prediction.Prediction;
import io.openems.edge.predictor.api.test.DummyPredictor;
import io.openems.edge.predictor.api.test.DummyPredictorManager;
import io.openems.edge.timeofusetariff.api.TimeOfUsePrices;
import io.openems.edge.timeofusetariff.test.DummyTariffManager;
import io.openems.edge.timeofusetariff.test.DummyTimeOfUseTariffProvider;

/**
 * Integration tests for US price shapes (PJM hourly day-ahead LMPs and ComEd
 * 5-minute real-time prices) driving the Time-Of-Use-Tariff Controller via
 * {@link GlobalOptimizationContext} and the {@link Simulator}.
 *
 * <p>
 * Data path under test: {@code TimeOfUseTariff.getPrices()} (provided here by
 * {@link DummyTimeOfUseTariffProvider} with provider-shaped fixtures) →
 * {@code TariffManager} → {@code GocBuilder} → {@link GlobalOptimizationContext}
 * → {@code Controller.Ess.Time-Of-Use-Tariff} {@link EnergyScheduler} →
 * {@link Simulator} → schedule with {@link StateMachine} modes.
 */
public class UsGridTariffIntegrationTest {

	private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
	private static final LocalDate SUMMER_DAY = LocalDate.of(2026, 7, 15);
	private static final LocalDate DST_SPRING_FORWARD = LocalDate.of(2026, 3, 8); // 23 hours
	private static final LocalDate DST_FALL_BACK = LocalDate.of(2026, 11, 1); // 25 hours

	private static final Duration RUNTIME_LIMIT = Duration.ofSeconds(20);
	private static final EnumSet<StateMachine> CHARGE_CONSUMPTION_STATES = EnumSet.of(//
			StateMachine.BALANCING, StateMachine.DELAY_DISCHARGE, StateMachine.CHARGE_GRID);

	/**
	 * PJM Data Miner 2 {@code da_hrl_lmps} shaped day-ahead LMPs in USD/MWh for
	 * one local day: negative overnight prices (wind surplus), a moderate morning
	 * and a scarcity spike of 2000 USD/MWh in the evening peak hour.
	 */
	private static final double[] PJM_HOURLY_LMP_USD_PER_MWH = { //
			18.42, -5.13, -12.87, -8.21, -2.04, 15.6, 28.9, 41.33, // 00:00 - 07:00
			38.75, 33.1, 30.02, 29.55, 31.8, 36.44, 45.9, 62.7, // 08:00 - 15:00
			98.31, 2000.0, 310.56, 120.4, 75.2, 52.6, 34.9, 22.15 // 16:00 - 23:00
	};

	/**
	 * ComEd Hourly Pricing 5-minute feed values in cent/kWh for one hour (12
	 * values), showing strong intra-hour volatility.
	 */
	private static final double[] COMED_VOLATILE_HOUR_CENTS_PER_KWH = { //
			2.1, 2.3, 9.8, /* quarter 0 avg 4.733 */ //
			1.9, 14.2, 2.0, /* quarter 1 avg 6.033 */ //
			-0.4, -1.1, 0.2, /* quarter 2 avg -0.433 */ //
			3.3, 3.1, 3.4 /* quarter 3 avg 3.267 */ //
	};

	@Before
	public void before() {
		// Make reproducible results
		System.setProperty("io.jenetics.util.defaultRandomGenerator", "Random");
		RandomRegistry.random(new Random(123));
	}

	/*
	 * (a) PJM-shaped hourly LMP series in USD/MWh
	 */

	@Test
	public void testPjmHourlyLmpExpandsToQuarterPeriods() throws Exception {
		final var midnight = SUMMER_DAY.atStartOfDay(CHICAGO);
		final var prices = pjmHourlyPrices(midnight);
		final var goc = buildGoc(midnight, prices, 50, Currency.USD);

		assertEquals(96, countQuarters(goc));
		// first period is the first hour at 18.42 USD/MWh
		assertEquals(18.42, goc.periods().get(0).data().gridBuyPrice().map(Price::actual).orElseThrow(), 0.001);
		// quarter 4 (01:00) is the first negative hour
		assertEquals(-5.13, goc.periods().get(4).data().gridBuyPrice().map(Price::actual).orElseThrow(), 0.001);
	}

	@Test
	public void testPjmNegativePricesArePositiveShiftedForCostEvaluation() throws Exception {
		final var midnight = SUMMER_DAY.atStartOfDay(CHICAGO);
		final var goc = buildGoc(midnight, pjmHourlyPrices(midnight), 50, Currency.USD);

		for (var period : goc.periods().stream().toList()) {
			final var price = period.data().gridBuyPrice().orElseThrow();
			assertTrue("positiveShifted must be > 0 even for negative LMPs; was " + price,
					price.positiveShifted() > 0);
			assertTrue("normalized must be within [0;1]; was " + price,
					price.normalized() >= 0 && price.normalized() <= 1);
		}
		// negative actual values are preserved
		final var minActual = goc.periods().stream() //
				.mapToDouble(p -> p.data().gridBuyPrice().orElseThrow().actual()) //
				.min().orElseThrow();
		assertEquals(-12.87, minActual, 0.001);
	}

	@Test
	public void testPjmSpikeDrivesDischargeFromEss() throws Exception {
		final var midnight = SUMMER_DAY.atStartOfDay(CHICAGO);
		final var esh = tou();
		final var goc = buildGoc(midnight, pjmHourlyPrices(midnight), 80, Currency.USD, esh);
		new Simulator(goc); // initializes ESH

		final var spikeIndex = indexOfPeriodAt(goc, LocalTime.of(17, 0));
		final var balancing = modeIndex(goc, StateMachine.BALANCING);
		final var delayDischarge = modeIndex(goc, StateMachine.DELAY_DISCHARGE);

		final var balancingResult = SimulationResult.fromQuarters(goc, fill(goc, balancing), 0, 0);
		final var delayAtSpike = fill(goc, balancing);
		delayAtSpike[spikeIndex] = delayDischarge;
		final var delayResult = SimulationResult.fromQuarters(goc, delayAtSpike, 0, 0);

		// During the 2000 USD/MWh spike, BALANCING discharges the ESS and avoids
		// grid-buy; DELAY_DISCHARGE would be more expensive.
		final var spikeTime = midnight.withHour(17);
		final var balancingSpikePeriod = balancingResult.periods().get(spikeTime);
		assertNotNull(balancingSpikePeriod);
		assertEquals(2000.0, balancingSpikePeriod.period().data().gridBuyPrice().orElseThrow().actual(), 0.001);
		assertTrue("ESS should discharge during the spike", balancingSpikePeriod.energyFlow().getEss() > 0);
		assertEquals(0, balancingSpikePeriod.energyFlow().getGrid());
		assertTrue(delayResult.fitness().gridBuyCostScore() > balancingResult.fitness().gridBuyCostScore());
	}

	@Test
	public void testPjmNegativeNightPricesMakeChargeGridCheaperOverHorizon() throws Exception {
		final var midnight = SUMMER_DAY.atStartOfDay(CHICAGO);
		final var esh = tou();
		final var goc = buildGoc(midnight, pjmHourlyPrices(midnight), 10, Currency.USD, esh);
		new Simulator(goc); // initializes ESH

		final var balancing = modeIndex(goc, StateMachine.BALANCING);
		final var chargeGrid = modeIndex(goc, StateMachine.CHARGE_GRID);

		final var allBalancing = SimulationResult.fromQuarters(goc, fill(goc, balancing), 0, 0);

		// CHARGE_GRID during the negative-priced hours 01:00-04:00
		final var chargeAtNight = fill(goc, balancing);
		for (var t = LocalTime.of(1, 0); t.isBefore(LocalTime.of(5, 0)); t = t.plusMinutes(15)) {
			chargeAtNight[indexOfPeriodAt(goc, t)] = chargeGrid;
		}
		final var chargeNight = SimulationResult.fromQuarters(goc, chargeAtNight, 0, 0);

		final var nightPeriod = chargeNight.periods().get(midnight.withHour(2));
		assertTrue("ESS should charge from grid at -12.87 USD/MWh", nightPeriod.energyFlow().getEss() < 0);
		assertTrue(nightPeriod.energyFlow().getGrid() > 0);
		assertEquals(StateMachine.CHARGE_GRID, modeAt(chargeNight, esh, midnight.withHour(2)));

		// Same CHARGE_GRID window during the expensive evening hours 16:00-20:00
		final var chargeAtPeak = fill(goc, balancing);
		for (var t = LocalTime.of(16, 0); t.isBefore(LocalTime.of(20, 0)); t = t.plusMinutes(15)) {
			chargeAtPeak[indexOfPeriodAt(goc, t)] = chargeGrid;
		}
		final var chargePeak = SimulationResult.fromQuarters(goc, chargeAtPeak, 0, 0);

		// Cheap hours are the right place to charge; expensive hours are not
		assertTrue("Charging at negative night prices must be cheaper than charging at the spike: " //
				+ chargeNight.fitness().gridBuyCostScore() + " vs " + chargePeak.fitness().gridBuyCostScore(),
				chargeNight.fitness().gridBuyCostScore() < chargePeak.fitness().gridBuyCostScore());
		assertTrue(allBalancing.fitness().gridBuyCostScore() < chargePeak.fitness().gridBuyCostScore());
		assertEquals(0, chargeNight.fitness().hardConstraintViolations());
	}

	@Test
	public void testPjmOptimizerProducesValidScheduleWithinRuntimeBound() throws Exception {
		final var midnight = SUMMER_DAY.atStartOfDay(CHICAGO);
		final var esh = tou();
		final var goc = buildGoc(midnight, pjmHourlyPrices(midnight), 20, Currency.USD, esh);

		final var start = Instant.now();
		final var result = runBoundedOptimization(goc);
		final var elapsed = Duration.between(start, Instant.now());

		assertTrue("Optimizer runtime exceeded bound: " + elapsed, elapsed.compareTo(RUNTIME_LIMIT.plusSeconds(15)) < 0);
		assertFalse(result.periods().isEmpty());
		assertEquals(96, result.periods().size());
		assertOnlyStates(result, esh, CHARGE_CONSUMPTION_STATES);
		assertTrue(result.schedules().get(esh).values().stream() //
				.anyMatch(t -> t.gridBuyPrice() != null && t.gridBuyPrice() == 2000.0));
	}

	/*
	 * (b) ComEd-shaped 5-minute-averaged quarter series
	 */

	@Test
	public void testComEdFiveMinuteFeedAveragesIntoQuarterPeriods() throws Exception {
		final var midnight = SUMMER_DAY.atStartOfDay(CHICAGO);
		final var prices = comEdFiveMinutePrices(midnight, 24);
		final var goc = buildGoc(midnight, prices, 50, Currency.USD);

		assertEquals(96, countQuarters(goc));
		// first hour is resolved in quarters: averages of 3 five-minute values each
		final var q = goc.periods();
		assertEquals(47.333, q.get(0).data().gridBuyPrice().orElseThrow().actual(), 0.001);
		assertEquals(60.333, q.get(1).data().gridBuyPrice().orElseThrow().actual(), 0.001);
		assertEquals(-4.333, q.get(2).data().gridBuyPrice().orElseThrow().actual(), 0.001);
		assertEquals(32.667, q.get(3).data().gridBuyPrice().orElseThrow().actual(), 0.001);
	}

	@Test
	public void testComEdIntraHourVolatilityIsAveragedInHourPeriods() throws Exception {
		final var midnight = SUMMER_DAY.atStartOfDay(CHICAGO);
		final var goc = buildGoc(midnight, comEdFiveMinutePrices(midnight, 24), 50, Currency.USD);

		// After the first 6 hours periods are aggregated to hours
		final var hourPeriod = goc.periods().stream() //
				.filter(Period.Hour.class::isInstance) //
				.map(Period.Hour.class::cast) //
				.findFirst().orElseThrow();
		assertEquals(4, hourPeriod.quarterPeriods().size());
		final var expectedAvg = hourPeriod.quarterPeriods().stream() //
				.mapToDouble(p -> p.data().gridBuyPrice().orElseThrow().actual()) //
				.average().orElseThrow();
		assertEquals(expectedAvg, hourPeriod.data().gridBuyPrice().orElseThrow().actual(), 0.001);
		assertEquals(34.0, expectedAvg, 0.001); // (47.333 + 60.333 - 4.333 + 32.667) / 4
		// volatility is preserved in the quarter periods
		assertEquals(-4.333, hourPeriod.quarterPeriods().get(2).data().gridBuyPrice().orElseThrow().actual(), 0.001);
	}

	@Test
	public void testComEdVolatileQuartersOptimizeWithoutExceptions() throws Exception {
		final var midnight = SUMMER_DAY.atStartOfDay(CHICAGO);
		final var esh = tou();
		final var goc = buildGoc(midnight, comEdFiveMinutePrices(midnight, 24), 30, Currency.USD, esh);

		final var result = runBoundedOptimization(goc);

		assertEquals(96, result.periods().size());
		assertOnlyStates(result, esh, CHARGE_CONSUMPTION_STATES);
		// Applying the schedule to the ESH yields a current period with a mode
		result.schedules().forEach((e, s) -> e.applySchedule(s));
		final var current = esh.getCurrentPeriod();
		assertNotNull(current);
		assertTrue(CHARGE_CONSUMPTION_STATES.contains(current.mode()));
		assertEquals(47.333, current.gridBuyPrice(), 0.001);
	}

	/*
	 * (c) DST days in America/Chicago
	 */

	@Test
	public void testPjmDstSpringForwardDayHas23Hours() throws Exception {
		final var midnight = DST_SPRING_FORWARD.atStartOfDay(CHICAGO);
		final var esh = tou();
		final var goc = buildGoc(midnight, pjmHourlyPrices(midnight), 50, Currency.USD, esh);

		assertEquals(23 * 4, countQuarters(goc));
		assertNoDuplicateOrMissingQuarters(goc);
		final var result = runBoundedOptimization(goc);
		assertEquals(23 * 4, result.periods().size());
		assertOnlyStates(result, esh, CHARGE_CONSUMPTION_STATES);
	}

	@Test
	public void testPjmDstFallBackDayHas25Hours() throws Exception {
		final var midnight = DST_FALL_BACK.atStartOfDay(CHICAGO);
		final var esh = tou();
		final var goc = buildGoc(midnight, pjmHourlyPrices(midnight), 50, Currency.USD, esh);

		assertEquals(25 * 4, countQuarters(goc));
		assertNoDuplicateOrMissingQuarters(goc);
		// 01:00 CDT and 01:00 CST are two distinct hours with different prices
		final var oneAmCdt = midnight.plusHours(1);
		final var oneAmCst = midnight.plusHours(2);
		assertEquals(oneAmCdt.toLocalTime(), oneAmCst.toLocalTime());
		final var result = runBoundedOptimization(goc);
		assertEquals(25 * 4, result.periods().size());
		assertNotNull(result.periods().get(oneAmCdt));
		assertNotNull(result.periods().get(oneAmCst));
		assertOnlyStates(result, esh, CHARGE_CONSUMPTION_STATES);
	}

	@Test
	public void testComEdDstSpringForwardDayHas23Hours() throws Exception {
		final var midnight = DST_SPRING_FORWARD.atStartOfDay(CHICAGO);
		final var goc = buildGoc(midnight, comEdFiveMinutePrices(midnight, 23), 50, Currency.USD);

		assertEquals(23 * 4, countQuarters(goc));
		assertNoDuplicateOrMissingQuarters(goc);
		assertEquals(midnight.plusDays(1).minusMinutes(15).toInstant(), lastQuarter(goc).toInstant());
	}

	@Test
	public void testComEdDstFallBackDayHas25Hours() throws Exception {
		final var midnight = DST_FALL_BACK.atStartOfDay(CHICAGO);
		final var goc = buildGoc(midnight, comEdFiveMinutePrices(midnight, 25), 50, Currency.USD);

		assertEquals(25 * 4, countQuarters(goc));
		assertNoDuplicateOrMissingQuarters(goc);
		assertEquals(midnight.plusDays(1).minusMinutes(15).toInstant(), lastQuarter(goc).toInstant());
	}

	/*
	 * (d) Day-ahead prices only until 24:00 local -> shorter horizon
	 */

	@Test
	public void testDayAheadPricesEndingAtLocalMidnightShortenHorizon() throws Exception {
		final var midnight = SUMMER_DAY.atStartOfDay(CHICAGO);
		final var now = midnight.withHour(16).withMinute(30);
		final var esh = tou();
		// Prices are published for today only; consumption/production predictions
		// cover the next 48 hours.
		final var goc = buildGoc(now, pjmHourlyPrices(midnight), 50, Currency.USD, esh);

		assertEquals(now, goc.startTime());
		assertEquals(30, countQuarters(goc)); // 16:30 .. 23:45
		assertEquals(midnight.plusDays(1).minusMinutes(15).toInstant(), lastQuarter(goc).toInstant());

		final var result = runBoundedOptimization(goc);
		assertEquals(30, result.periods().size());
		assertOnlyStates(result, esh, CHARGE_CONSUMPTION_STATES);
	}

	@Test
	public void testDayAheadPricesEndingAtLocalMidnightKeepAllRemainingQuarters() throws Exception {
		final var midnight = SUMMER_DAY.atStartOfDay(CHICAGO);
		final var now = midnight.withHour(23).withMinute(45);
		final var goc = buildGoc(now, comEdFiveMinutePrices(midnight, 24), 50, Currency.USD);

		assertEquals(1, countQuarters(goc));
		assertEquals(now.toInstant(), goc.periods().get(0).time().toInstant());
	}

	/*
	 * (e) Currency USD in _meta
	 */

	@Test
	public void testUsdCurrencyInMeta() throws Exception {
		final var midnight = SUMMER_DAY.atStartOfDay(CHICAGO);
		final var meta = new DummyMeta() //
				.withCurrency(Currency.USD) //
				.withGridBuyHardLimit(20_000) //
				.withGridSellHardLimit(20_000) //
				.withGridSellHardLimitWithBuffer(19_000);
		assertEquals(Currency.USD, meta.getCurrency());
		assertEquals("USD", meta.getCurrency().getName());

		final var goc = buildGoc(midnight, pjmHourlyPrices(midnight), 50, meta, tou());
		// Prices are unit-less (Currency/MWh) inside the optimizer; USD values pass
		// through unchanged
		assertEquals(2000.0, goc.periods().stream() //
				.mapToDouble(p -> p.data().gridBuyPrice().orElseThrow().actual()) //
				.max().orElseThrow(), 0.001);
	}

	/*
	 * Grid-Optimized-Charge limits
	 */

	@Test
	public void testGridOptimizedChargeLimitsAreSensible() throws Exception {
		final var midnight = SUMMER_DAY.atStartOfDay(CHICAGO);
		final var esh = tou();
		final var goc = buildGoc(midnight, pjmHourlyPrices(midnight), 20, Currency.USD, esh);
		new Simulator(goc); // initializes ESH

		final var result = SimulationResult.fromQuarters(goc, fill(goc, modeIndex(goc, StateMachine.BALANCING)), 0,
				0);
		result.schedules().forEach((e, s) -> e.applySchedule(s));
		final var coc = esh.getCurrentPeriod().coc();

		assertTrue(coc.maxSocInChargeGrid() > 0 && coc.maxSocInChargeGrid() <= 100);
		assertTrue(coc.maxEnergyInChargeGrid() > 0);
		assertTrue(coc.maxEnergyInChargeGrid() <= goc.ess().totalEnergy());
		assertTrue(coc.essChargePowerInChargeGrid() > 0);
		// reference power is derived from the chargeable energy via the C-rate
		assertTrue("charge power must not exceed C-rate of chargeable energy",
				coc.essChargePowerInChargeGrid() <= round(coc.maxEnergyInChargeGrid() * ESS_CHARGE_C_RATE));
	}

	/*
	 * Helpers
	 */

	/**
	 * Builds the ESH of Controller.Ess.Time-Of-Use-Tariff in
	 * {@link ControlMode#CHARGE_CONSUMPTION}.
	 * 
	 * @return the ESH
	 */
	private static EshWithDifferentModes<StateMachine, EnergyScheduler.OptimizationContext, Void> tou() {
		return EnergyScheduler.buildEnergyScheduleHandler(new DummyController("ctrlEssTimeOfUseTariff0"),
				() -> new EnergyScheduler.Config(ControlMode.CHARGE_CONSUMPTION.modes, null, null));
	}

	/**
	 * PJM-shaped prices: one hourly value per hour of the local day (23, 24 or 25
	 * hours), expanded to four quarters each, as the PJM provider does.
	 * 
	 * @param midnight start of the local day
	 * @return the {@link TimeOfUsePrices}
	 */
	private static TimeOfUsePrices pjmHourlyPrices(ZonedDateTime midnight) {
		final var map = ImmutableSortedMap.<Instant, Double>naturalOrder();
		var hour = midnight;
		var i = 0;
		while (hour.toLocalDate().equals(midnight.toLocalDate())) {
			final var lmp = PJM_HOURLY_LMP_USD_PER_MWH[i % PJM_HOURLY_LMP_USD_PER_MWH.length];
			for (var q = 0; q < 4; q++) {
				map.put(hour.plusMinutes(q * 15L).toInstant(), lmp);
			}
			hour = hour.plusHours(1);
			i++;
		}
		return TimeOfUsePrices.from(map.buildOrThrow());
	}

	/**
	 * ComEd-shaped prices: 5-minute feed values in cent/kWh, converted to
	 * Currency/MWh (x10) and averaged per quarter, as the ComEd provider does.
	 * 
	 * @param midnight start of the local day
	 * @param hours    number of hours to generate
	 * @return the {@link TimeOfUsePrices}
	 */
	private static TimeOfUsePrices comEdFiveMinutePrices(ZonedDateTime midnight, int hours) {
		final var map = ImmutableSortedMap.<Instant, Double>naturalOrder();
		for (var h = 0; h < hours; h++) {
			final var hourStart = midnight.plusHours(h);
			for (var q = 0; q < 4; q++) {
				final var offset = q * 3;
				final var sum = IntStream.range(0, 3) //
						.mapToDouble(m -> COMED_VOLATILE_HOUR_CENTS_PER_KWH[offset + m] * 10 /* cent/kWh -> $/MWh */) //
						.sum();
				map.put(hourStart.plusMinutes(q * 15L).toInstant(), sum / 3);
			}
		}
		return TimeOfUsePrices.from(map.buildOrThrow());
	}

	private static GlobalOptimizationContext buildGoc(ZonedDateTime now, TimeOfUsePrices prices, int essSoc,
			Currency currency, EnergyScheduleHandler... eshs) throws Exception {
		return buildGoc(now, prices, essSoc, new DummyMeta() //
				.withCurrency(currency) //
				.withGridBuyHardLimit(20_000) //
				.withGridSellHardLimit(20_000) //
				.withGridSellHardLimitWithBuffer(19_000), eshs);
	}

	private static GlobalOptimizationContext buildGoc(ZonedDateTime now, TimeOfUsePrices prices, int essSoc,
			DummyMeta meta, EnergyScheduleHandler... eshs) throws Exception {
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
				.setMeta(meta) //
				.setEnvironment(Environment.TEST) //
				.setEnergyScheduleHandlers(ImmutableList.copyOf(eshs)) //
				.setSum(sum) //
				.setPredictorManager(predictorManager) //
				.setTariffManager(tariffManager) //
				.build();
	}

	private static SimulationResult runBoundedOptimization(GlobalOptimizationContext goc) {
		final var simulator = new Simulator(goc);
		simulator.setEarliestCallbackDelay(Duration.ZERO);
		final var result = new AtomicReference<SimulationResult>(EMPTY_SIMULATION_RESULT);
		simulator.runOptimization(//
				() -> EMPTY_SIMULATION_RESULT, //
				false /* optimizeCurrentPeriod */, //
				engine -> engine //
						.populationSize(20), //
				stream -> stream //
						.limit(byFixedGeneration(10)) //
						.limit(byExecutionTime(RUNTIME_LIMIT)), //
				result::set);
		return result.get();
	}

	private static int countQuarters(GlobalOptimizationContext goc) {
		return goc.periods().stream() //
				.mapToInt(p -> switch (p) {
				case Period.Quarter q -> 1;
				case Period.Hour h -> h.quarterPeriods().size();
				}) //
				.sum();
	}

	private static ZonedDateTime lastQuarter(GlobalOptimizationContext goc) {
		return switch (goc.periods().getLast()) {
		case Period.Quarter q -> q.time();
		case Period.Hour h -> h.quarterPeriods().getLast().time();
		};
	}

	private static void assertNoDuplicateOrMissingQuarters(GlobalOptimizationContext goc) {
		var expected = goc.startTime();
		for (var period : goc.periods().stream().toList()) {
			final var quarters = switch (period) {
			case Period.Quarter q -> ImmutableList.of(q);
			case Period.Hour h -> h.quarterPeriods();
			};
			for (var q : quarters) {
				assertEquals("Unexpected quarter timestamp", expected.toInstant(), q.time().toInstant());
				expected = expected.plusMinutes(15);
			}
		}
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

	/**
	 * Applies the schedule of the {@link SimulationResult} to the ESH and returns
	 * the {@link StateMachine} mode at the given time.
	 * 
	 * @param result the {@link SimulationResult}
	 * @param esh    the ESH
	 * @param time   the time
	 * @return the mode
	 */
	private static StateMachine modeAt(SimulationResult result,
			EshWithDifferentModes<StateMachine, EnergyScheduler.OptimizationContext, Void> esh, ZonedDateTime time) {
		esh.applySchedule(result.schedules().get(esh));
		final var period = esh.getSchedule().get(time);
		assertNotNull("No schedule entry at " + time, period);
		return period.mode();
	}

	private static void assertOnlyStates(SimulationResult result,
			EshWithDifferentModes<StateMachine, EnergyScheduler.OptimizationContext, Void> esh,
			EnumSet<StateMachine> allowed) {
		final var transitions = result.schedules().get(esh);
		assertNotNull(transitions);
		assertEquals(result.periods().size(), transitions.size());
		esh.applySchedule(transitions);
		final var schedule = esh.getSchedule();
		assertEquals(transitions.size(), schedule.size());
		schedule.values().forEach(p -> {
			assertTrue("Unexpected state " + p.mode(), allowed.contains(p.mode()));
		});
	}
}
