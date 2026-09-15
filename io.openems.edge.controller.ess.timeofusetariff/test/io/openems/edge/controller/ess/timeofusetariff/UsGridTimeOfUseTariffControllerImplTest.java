package io.openems.edge.controller.ess.timeofusetariff;

import static io.openems.common.utils.DateUtils.roundDownToQuarter;
import static io.openems.edge.controller.ess.timeofusetariff.ControlMode.CHARGE_CONSUMPTION;
import static io.openems.edge.controller.ess.timeofusetariff.Mode.AUTOMATIC;
import static io.openems.edge.controller.ess.timeofusetariff.StateMachine.BALANCING;
import static io.openems.edge.controller.ess.timeofusetariff.StateMachine.CHARGE_GRID;
import static io.openems.edge.controller.ess.timeofusetariff.StateMachine.DELAY_DISCHARGE;
import static io.openems.edge.controller.ess.timeofusetariff.TimeOfUseTariffController.ChannelId.QUARTERLY_PRICES;
import static io.openems.edge.controller.ess.timeofusetariff.TimeOfUseTariffController.ChannelId.STATE_MACHINE;
import static io.openems.edge.energy.api.EnergyUtils.filterEshsWithDifferentModes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;

import io.openems.common.jscalendar.JSCalendar;
import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.common.test.TimeLeapClock;
import io.openems.edge.common.currency.Currency;
import io.openems.edge.common.sum.DummySum;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.DummyMeta;
import io.openems.edge.controller.test.ControllerTest;
import io.openems.edge.energy.api.Environment;
import io.openems.edge.energy.api.Version;
import io.openems.edge.energy.api.handler.DifferentModes.Period.Transition;
import io.openems.edge.energy.api.handler.EnergyScheduleHandler;
import io.openems.edge.energy.api.simulation.GlobalOptimizationContext;
import io.openems.edge.energy.api.simulation.periods.PeriodDuration;
import io.openems.edge.energy.api.simulation.periods.Periods;
import io.openems.edge.ess.test.DummyManagedSymmetricEss;
import io.openems.edge.timeofusetariff.api.TimeOfUsePrices;
import io.openems.edge.timeofusetariff.test.DummyTariffManager;
import io.openems.edge.timeofusetariff.test.DummyTimeOfUseTariffProvider;

/**
 * Drives {@link TimeOfUseTariffControllerImpl} with US price shapes (PJM hourly
 * LMPs, ComEd 5-minute-averaged quarters) in USD and asserts the observable
 * {@link StateMachine} and QUARTERLY_PRICES channels.
 */
public class UsGridTimeOfUseTariffControllerImplTest {

	private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
	private static final LocalDate SUMMER_DAY = LocalDate.of(2026, 7, 15);
	private static final LocalDate DST_FALL_BACK = LocalDate.of(2026, 11, 1);

	private static final double[] PJM_HOURLY_LMP_USD_PER_MWH = { //
			18.42, -5.13, -12.87, -8.21, -2.04, 15.6, 28.9, 41.33, //
			38.75, 33.1, 30.02, 29.55, 31.8, 36.44, 45.9, 62.7, //
			98.31, 2000.0, 310.56, 120.4, 75.2, 52.6, 34.9, 22.15 //
	};

	/** ComEd 5-minute values [cent/kWh] of one hour, averaged per quarter. */
	private static final double[] COMED_QUARTER_AVG_USD_PER_MWH = { //
			(2.1 + 2.3 + 9.8) / 3 * 10, //
			(1.9 + 14.2 + 2.0) / 3 * 10, //
			(-0.4 - 1.1 + 0.2) / 3 * 10, //
			(3.3 + 3.1 + 3.4) / 3 * 10 //
	};

	@Test
	public void testChargeGridAtNegativePjmLmp() throws Exception {
		final var now = SUMMER_DAY.atStartOfDay(CHICAGO).withHour(2).withMinute(15);
		final var fixture = new Fixture(now, pjmHourlyPrices(now));
		fixture.scheduleCurrentPeriod(CHARGE_GRID);

		fixture.test //
				.next(new TestCase() //
						.input("_sum", "GridActivePower", 1000) //
						.input("_sum", "ProductionActivePower", 0) //
						.input("ess0", "ActivePower", 0) //
						.output(STATE_MACHINE, CHARGE_GRID) //
						.output(QUARTERLY_PRICES, -12.87)) //
				.deactivate();
	}

	@Test
	public void testDelayDischargeAtPjmSpike() throws Exception {
		final var now = SUMMER_DAY.atStartOfDay(CHICAGO).withHour(17).withMinute(30);
		final var fixture = new Fixture(now, pjmHourlyPrices(now));
		fixture.scheduleCurrentPeriod(DELAY_DISCHARGE);

		fixture.test //
				.next(new TestCase() //
						.input("_sum", "GridActivePower", 0) //
						.input("_sum", "ProductionActivePower", 0) //
						.input("ess0", "ActivePower", 1000) // currently discharging
						.output(STATE_MACHINE, DELAY_DISCHARGE) //
						.output(QUARTERLY_PRICES, 2000.0)) //
				.deactivate();
	}

	@Test
	public void testBalancingWithComEdQuarterPrices() throws Exception {
		final var midnight = SUMMER_DAY.atStartOfDay(CHICAGO);
		final var now = midnight.withMinute(30); // third quarter: negative average
		final var fixture = new Fixture(now, comEdQuarterPrices(midnight, 24));
		fixture.scheduleCurrentPeriod(BALANCING);

		fixture.test //
				.next(new TestCase() //
						.input("_sum", "GridActivePower", 500) //
						.input("_sum", "ProductionActivePower", 0) //
						.input("ess0", "ActivePower", 500) //
						.output(STATE_MACHINE, BALANCING) //
						.output(QUARTERLY_PRICES, COMED_QUARTER_AVG_USD_PER_MWH[2])) //
				.deactivate();
		assertEquals(-4.333, COMED_QUARTER_AVG_USD_PER_MWH[2], 0.001);
	}

	@Test
	public void testBalancingWithoutScheduleAndUsdMeta() throws Exception {
		final var now = SUMMER_DAY.atStartOfDay(CHICAGO).withHour(17);
		final var fixture = new Fixture(now, pjmHourlyPrices(now));
		assertEquals(Currency.USD, fixture.meta.getCurrency());

		// No schedule applied -> falls back to BALANCING, prices are still exposed
		fixture.test //
				.next(new TestCase() //
						.input("_sum", "GridActivePower", 0) //
						.input("_sum", "ProductionActivePower", 0) //
						.input("ess0", "ActivePower", 0) //
						.output(STATE_MACHINE, BALANCING) //
						.output(QUARTERLY_PRICES, 2000.0)) //
				.deactivate();
	}

	@Test
	public void testDstFallBackRepeatedHourUsesDistinctPrices() throws Exception {
		final var midnight = DST_FALL_BACK.atStartOfDay(CHICAGO);
		final var secondOneAm = midnight.plusHours(2); // 01:00 CST, after 01:00 CDT
		assertEquals(1, secondOneAm.getHour());
		final var fixture = new Fixture(secondOneAm, pjmHourlyPrices(midnight));
		fixture.scheduleCurrentPeriod(CHARGE_GRID);

		fixture.test //
				.next(new TestCase() //
						.input("_sum", "GridActivePower", 1000) //
						.input("_sum", "ProductionActivePower", 0) //
						.input("ess0", "ActivePower", 0) //
						.output(STATE_MACHINE, CHARGE_GRID) //
						// third hourly value of the 25-hour day
						.output(QUARTERLY_PRICES, PJM_HOURLY_LMP_USD_PER_MWH[2])) //
				.deactivate();
	}

	/**
	 * Controller under test with a USD {@link DummyMeta}, a
	 * {@link DummyTimeOfUseTariffProvider} and an initialized
	 * {@link EnergyScheduleHandler}.
	 */
	private static class Fixture {
		private final ZonedDateTime now;
		private final TimeLeapClock clock;
		private final TimeOfUsePrices prices;
		private final DummyMeta meta;
		private final TimeOfUseTariffControllerImpl sut;
		private final ControllerTest test;

		private Fixture(ZonedDateTime now, TimeOfUsePrices prices) throws Exception {
			this.now = now;
			this.prices = prices;
			this.clock = new TimeLeapClock(now.toInstant(), CHICAGO);
			this.meta = new DummyMeta() //
					.withCurrency(Currency.USD) //
					.withGridBuyHardLimit(20_000) //
					.withGridSellHardLimit(20_000) //
					.withGridSellHardLimitWithBuffer(19_000);
			this.sut = new TimeOfUseTariffControllerImpl();
			this.test = new ControllerTest(this.sut) //
					.addReference("cm", new DummyConfigurationAdmin()) //
					.addReference("componentManager", new DummyComponentManager(this.clock)) //
					.addReference("sum", new DummySum()) //
					.addReference("tariffManager", new DummyTariffManager() //
							.withTariffGridBuyProvider(new DummyTimeOfUseTariffProvider(this.clock, prices))) //
					.addReference("meta", this.meta) //
					.addReference("energyScheduler",
							new TimeOfUseTariffControllerImplTest.DummyEnergyScheduler(Version.V2_ENERGY_SCHEDULABLE)) //
					.addReference("ess", new DummyManagedSymmetricEss("ess0") //
							.withSoc(50) //
							.withCapacity(22_000)) //
					.activate(MyConfig.create() //
							.setId("ctrlEssTimeOfUseTariff0") //
							.setEssId("ess0") //
							.setEnabled(true) //
							.setMode(AUTOMATIC) //
							.setManualMode(BALANCING) //
							.setControlMode(CHARGE_CONSUMPTION) //
							.setEssMaxChargePower(5000) //
							.setMaxChargePowerFromGrid(10_000) //
							.build());
		}

		/**
		 * Initializes the ESH with a {@link GlobalOptimizationContext} built from the
		 * price fixture and applies a schedule with the given mode for the current
		 * quarter.
		 * 
		 * @param mode the {@link StateMachine} mode
		 */
		private void scheduleCurrentPeriod(StateMachine mode) {
			final var esh = this.sut.getEnergyScheduleHandler();
			final var eshs = ImmutableList.<EnergyScheduleHandler>of(esh);
			final var startTime = roundDownToQuarter(this.now);
			final var periods = Periods.builder(Environment.TEST);
			for (var time = startTime; time.toLocalDate().isBefore(startTime.toLocalDate().plusDays(2)); //
					time = time.plusMinutes(15)) {
				final var price = this.prices.getAt(time.toInstant());
				if (price == null) {
					break;
				}
				periods.addPeriodIfValid(time, null, 0, 1000, price, null);
			}
			final var goc = new GlobalOptimizationContext(//
					this.clock, Environment.TEST, startTime, //
					eshs, //
					filterEshsWithDifferentModes(eshs) //
							.collect(ImmutableList.toImmutableList()), //
					new GlobalOptimizationContext.Grid(20_000, 20_000, 19_000, JSCalendar.Tasks.empty()), //
					new GlobalOptimizationContext.Ess(11_000, 22_000, 5_000, 5_000), //
					periods.build());
			esh.initialize(goc);

			final var modeIndex = CHARGE_CONSUMPTION.modes.indexOf(mode);
			final var price = this.prices.getAt(startTime.toInstant());
			assertNotNull(price);
			esh.applySchedule(ImmutableSortedMap.of(startTime, //
					new Transition(PeriodDuration.QUARTER, modeIndex, price, null, null, 11_000)));
			assertEquals(mode, esh.getCurrentPeriod().mode());
		}
	}

	private static TimeOfUsePrices pjmHourlyPrices(ZonedDateTime anyTimeOfDay) {
		final var midnight = anyTimeOfDay.toLocalDate().atStartOfDay(CHICAGO);
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

	private static TimeOfUsePrices comEdQuarterPrices(ZonedDateTime midnight, int hours) {
		final var map = ImmutableSortedMap.<Instant, Double>naturalOrder();
		for (var h = 0; h < hours; h++) {
			for (var q = 0; q < 4; q++) {
				map.put(midnight.plusHours(h).plusMinutes(q * 15L).toInstant(), COMED_QUARTER_AVG_USD_PER_MWH[q]);
			}
		}
		return TimeOfUsePrices.from(map.buildOrThrow());
	}
}
