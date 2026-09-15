package io.openems.edge.timeofusetariff.comed;

import static io.openems.edge.timeofusetariff.comed.ComEdApi.ZONE;
import static io.openems.edge.timeofusetariff.comed.ComEdApi.aggregateToQuarters;
import static io.openems.edge.timeofusetariff.comed.ComEdApi.backoff;
import static io.openems.edge.timeofusetariff.comed.ComEdApi.combine;
import static io.openems.edge.timeofusetariff.comed.ComEdApi.expandHourlyToQuarters;
import static io.openems.edge.timeofusetariff.comed.ComEdApi.parseDayAhead;
import static io.openems.edge.timeofusetariff.comed.ComEdApi.parseFeed;
import static io.openems.edge.timeofusetariff.comed.ComEdApi.toCurrencyPerMwh;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.TreeMap;

import org.junit.Test;

import com.google.common.collect.ImmutableSortedMap;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.timeofusetariff.api.TimeOfUsePrices;

public class ComEdApiTest {

	private static final double DELTA = 0.00001;

	private static ZonedDateTime chicago(int year, int month, int day, int hour, int minute) {
		return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE);
	}

	@Test
	public void testParseFiveMinuteFeedFixture() throws OpenemsNamedException {
		final var prices = parseFeed(Fixtures.FIVE_MINUTE_FEED);
		assertEquals(284, prices.size());
		assertEquals(2.8, prices.firstEntry().getValue(), DELTA);
		assertEquals(Instant.ofEpochMilli(1789396200000L), prices.firstKey());
		assertEquals(2.3, prices.lastEntry().getValue(), DELTA);
		assertEquals(Instant.ofEpochMilli(1789482000000L), prices.lastKey());
	}

	@Test
	public void testParseCurrentHourAverageFixture() throws OpenemsNamedException {
		final var prices = parseFeed(Fixtures.CURRENT_HOUR_AVERAGE);
		assertEquals(1, prices.size());
		assertEquals(Instant.parse("2026-09-15T14:20:00Z"), prices.firstKey());
		assertEquals(2.3, prices.firstEntry().getValue(), DELTA);
	}

	@Test
	public void testParseDayAheadFixture() throws OpenemsNamedException {
		final var prices = parseDayAhead(Fixtures.DAY_AHEAD);
		assertEquals(24, prices.size());
		// Hour 0 of 2026-09-15 America/Chicago (CDT, UTC-5)
		assertEquals(Instant.parse("2026-09-15T05:00:00Z"), prices.firstKey());
		assertEquals(1.5, prices.firstEntry().getValue(), DELTA);
		assertEquals(23.1, prices.get(chicago(2026, 9, 15, 19, 0).toInstant()), DELTA);
		assertEquals(5.2, prices.lastEntry().getValue(), DELTA);
	}

	@Test
	public void testParseDayAheadNotYetPublished() throws OpenemsNamedException {
		assertTrue(parseDayAhead("[]").isEmpty());
		assertTrue(parseDayAhead(" [] \n").isEmpty());
	}

	@Test
	public void testParseDayAheadInvalidContent() {
		// Actual response of api?type=day-ahead, which is not a valid feed type
		assertThrows(OpenemsNamedException.class, () -> parseDayAhead("Servlet Feed 2.95-SGalertsSync"));
		assertThrows(OpenemsNamedException.class, () -> parseDayAhead("[[foo, 1.0]]"));
		assertThrows(OpenemsNamedException.class, () -> parseDayAhead(null));
	}

	@Test
	public void testParseFeedEmpty() {
		assertThrows(OpenemsNamedException.class, () -> parseFeed(""));
		assertThrows(OpenemsNamedException.class, () -> parseFeed(null));
		assertThrows(OpenemsNamedException.class, () -> parseFeed("[]"));
	}

	@Test
	public void testParseFeedInvalidJson() {
		assertThrows(OpenemsNamedException.class, () -> parseFeed("Servlet Feed 2.95-SGalertsSync"));
		assertThrows(OpenemsNamedException.class, () -> parseFeed("{\"millisUTC\":\"1\",\"price\":\"2\"}"));
		assertThrows(OpenemsNamedException.class, () -> parseFeed("[{\"millisUTC\":\"1789482000000\"}]"));
		assertThrows(OpenemsNamedException.class,
				() -> parseFeed("[{\"millisUTC\":\"1789482000000\",\"price\":\"n/a\"}]"));
	}

	@Test
	public void testConversionCentsPerKwhToCurrencyPerMwh() {
		assertEquals(23.0, toCurrencyPerMwh(2.3, 0), DELTA);
		assertEquals(-17.0, toCurrencyPerMwh(-1.7, 0), DELTA);
		assertEquals(12.3457, toCurrencyPerMwh(1.234567, 0), DELTA);
		assertEquals(0.0, toCurrencyPerMwh(0, 0), DELTA);
	}

	@Test
	public void testConversionAddsAncillaryCosts() {
		assertEquals(73.0, toCurrencyPerMwh(2.3, 50), DELTA);
		assertEquals(51.2346, toCurrencyPerMwh(0.1, 50.23456), DELTA);
	}

	@Test
	public void testAggregateToQuartersMean() throws OpenemsNamedException {
		final var start = Instant.parse("2026-09-15T14:00:00Z");
		final var raw = new TreeMap<Instant, Double>();
		raw.put(start, 1.0);
		raw.put(start.plus(Duration.ofMinutes(5)), 2.0);
		raw.put(start.plus(Duration.ofMinutes(10)), 3.0);
		raw.put(start.plus(Duration.ofMinutes(15)), 4.0);
		raw.put(start.plus(Duration.ofMinutes(20)), 6.0);
		raw.put(start.plus(Duration.ofMinutes(35)), 9.0); // single value in quarter

		final var quarters = aggregateToQuarters(raw);
		assertEquals(3, quarters.size());
		assertEquals(2.0, quarters.get(start), DELTA);
		assertEquals(5.0, quarters.get(start.plus(Duration.ofMinutes(15))), DELTA);
		assertEquals(9.0, quarters.get(start.plus(Duration.ofMinutes(30))), DELTA);
	}

	@Test
	public void testAggregateFixtureToQuarters() throws OpenemsNamedException {
		final var quarters = aggregateToQuarters(parseFeed(Fixtures.FIVE_MINUTE_FEED));
		// 2026-09-15T14:30:00Z: 2.8, 1.9, 2.5
		assertEquals(2.4, quarters.get(Instant.ofEpochMilli(1789396200000L)), DELTA);
		// 2026-09-15T14:45:00Z: 1.9, 2.0, 2.9
		assertEquals(2.2666667, quarters.get(Instant.ofEpochMilli(1789397100000L)), DELTA);
	}

	@Test
	public void testExpandHourlyToQuarters() {
		final var start = chicago(2026, 9, 15, 10, 0).toInstant();
		final var hourly = ImmutableSortedMap.of(start, 3.0, start.plus(Duration.ofHours(1)), 3.3);

		final var quarters = expandHourlyToQuarters(hourly);
		assertEquals(8, quarters.size());
		assertEquals(start, quarters.firstKey());
		for (var i = 0; i < 4; i++) {
			assertEquals(3.0, quarters.get(start.plus(Duration.ofMinutes(15 * i))), DELTA);
			assertEquals(3.3, quarters.get(start.plus(Duration.ofMinutes(60 + 15 * i))), DELTA);
		}
	}

	@Test
	public void testCombineDayAheadOnly() throws OpenemsNamedException {
		final var prices = combine(parseDayAhead(Fixtures.DAY_AHEAD), ImmutableSortedMap.of(),
				ImmutableSortedMap.of(), 0);
		assertEquals(96, prices.getRawValues().size());
		assertEquals(Instant.parse("2026-09-15T05:00:00Z"), prices.getFirstTime());
		assertEquals(15.0, prices.getFirst(), DELTA);
		assertEquals(231.0, prices.getAt(chicago(2026, 9, 15, 19, 45).toInstant()), DELTA);
	}

	@Test
	public void testCombineOverlaysCurrentHourAverage() throws OpenemsNamedException {
		final var dayAhead = parseDayAhead(Fixtures.DAY_AHEAD);
		final var currentHour = parseFeed(Fixtures.CURRENT_HOUR_AVERAGE); // 2.3 at 09:20 CDT

		final var prices = combine(dayAhead, ImmutableSortedMap.of(), currentHour, 0);
		assertEquals(96, prices.getRawValues().size());
		// Whole hour 09:00-10:00 CDT replaced by the current-hour average
		for (var minute = 0; minute < 60; minute += 15) {
			assertEquals(23.0, prices.getAt(chicago(2026, 9, 15, 9, minute).toInstant()), DELTA);
		}
		// Neighbouring hours untouched (day-ahead 1.9 and 3.0)
		assertEquals(19.0, prices.getAt(chicago(2026, 9, 15, 8, 45).toInstant()), DELTA);
		assertEquals(30.0, prices.getAt(chicago(2026, 9, 15, 10, 0).toInstant()), DELTA);
	}

	@Test
	public void testCombineOverlaysFiveMinuteActuals() throws OpenemsNamedException {
		final var dayAhead = parseDayAhead(Fixtures.DAY_AHEAD);
		final var fiveMinute = parseFeed(Fixtures.FIVE_MINUTE_FEED);

		final var prices = combine(dayAhead, fiveMinute, ImmutableSortedMap.of(), 10);
		// 5-minute quarter 09:15 CDT (2.2, 2.3 -> 2.25 Cent/kWh) + 10 ancillary
		assertEquals(32.5, prices.getAt(Instant.parse("2026-09-15T14:15:00Z")), DELTA);
		// Quarter 09:30 CDT not yet covered by actuals: day-ahead 2.3 Cent/kWh + 10
		assertEquals(33.0, prices.getAt(Instant.parse("2026-09-15T14:30:00Z")), DELTA);
		// Future hour still from day-ahead (23.1 Cent/kWh) + 10 ancillary
		assertEquals(241.0, prices.getAt(chicago(2026, 9, 15, 19, 0).toInstant()), DELTA);
		// Fixture reaches back to 2026-09-14 09:30 CDT
		assertEquals(Instant.parse("2026-09-14T14:30:00Z"), prices.getFirstTime());
	}

	@Test
	public void testCombineEmpty() {
		assertEquals(TimeOfUsePrices.EMPTY_PRICES,
				combine(ImmutableSortedMap.of(), ImmutableSortedMap.of(), ImmutableSortedMap.of(), 0));
	}

	@Test
	public void testSpringForwardFiveMinuteFeed() throws OpenemsNamedException {
		// 2025-03-09: 02:00 CST -> 03:00 CDT; the day has only 23 hours
		final var quarters = aggregateToQuarters(parseFeed(Fixtures.FIVE_MINUTE_FEED_SPRING_FORWARD));
		assertEquals(92, quarters.size());
		assertEquals(chicago(2025, 3, 9, 0, 0).toInstant(), quarters.firstKey());
		assertEquals(chicago(2025, 3, 9, 23, 45).toInstant(), quarters.lastKey());
		assertEquals(3.3666667, quarters.firstEntry().getValue(), DELTA);
		assertEquals(-2.4333333, quarters.get(chicago(2025, 3, 9, 0, 15).toInstant()), DELTA);

		final var beforeGap = ZonedDateTime.of(2025, 3, 9, 1, 45, 0, 0, ZONE);
		assertEquals(ZoneOffset.ofHours(-6), beforeGap.getOffset());
		final var afterGap = quarters.higherKey(beforeGap.toInstant());
		assertEquals(beforeGap.plusMinutes(15).toInstant(), afterGap);
		assertEquals(ZoneOffset.ofHours(-5), afterGap.atZone(ZONE).getOffset());
		assertEquals(3, afterGap.atZone(ZONE).getHour());
	}

	@Test
	public void testSpringForwardDayAhead() throws OpenemsNamedException {
		final var hourly = parseDayAhead(Fixtures.DAY_AHEAD_SPRING_FORWARD);
		// The recorded feed contains 21 hours (local hours 1 and 7 are missing, hour 2
		// does not exist)
		assertEquals(21, hourly.size());
		assertEquals(Instant.parse("2025-03-09T06:00:00Z"), hourly.firstKey());
		assertEquals(-0.4, hourly.firstEntry().getValue(), DELTA);
		// 03:00 CDT == 08:00Z
		assertEquals(1.4, hourly.get(Instant.parse("2025-03-09T08:00:00Z")), DELTA);
		// 23:00 CDT == 04:00Z next day
		assertEquals(2.7, hourly.get(Instant.parse("2025-03-10T04:00:00Z")), DELTA);
		assertNull(hourly.get(Instant.parse("2025-03-09T07:00:00Z")));

		final var prices = combine(hourly, ImmutableSortedMap.of(), ImmutableSortedMap.of(), 0);
		assertEquals(23, Duration.between(prices.getFirstTime(), prices.getLastTime().plus(Duration.ofMinutes(15)))
				.toHours());
		assertEquals(92, ComEdApi.quartersOfDay(LocalDate.of(2025, 3, 9)));
	}

	@Test
	public void testFallBackFiveMinuteFeed() throws OpenemsNamedException {
		// 2025-11-02: 02:00 CDT -> 01:00 CST; the day has 25 hours
		final var raw = parseFeed(Fixtures.FIVE_MINUTE_FEED_FALL_BACK);
		final var quarters = aggregateToQuarters(raw);
		assertEquals(92, quarters.size());
		// The recorded back-fill starts at the transition instant (01:00 CST)
		assertEquals(Instant.parse("2025-11-02T07:00:00Z"), quarters.firstKey());
		assertEquals(ZoneOffset.ofHours(-6), quarters.firstKey().atZone(ZONE).getOffset());
		assertEquals(1, quarters.firstKey().atZone(ZONE).getHour());
		assertEquals(3.9666667, quarters.firstEntry().getValue(), DELTA);
		assertEquals(chicago(2025, 11, 2, 23, 45).toInstant(), quarters.lastKey());
		// Quarter keys are strictly increasing instants even though local times repeat
		Instant previous = null;
		for (var key : quarters.keySet()) {
			if (previous != null) {
				assertTrue(key.isAfter(previous));
			}
			previous = key;
		}
		assertEquals(100, ComEdApi.quartersOfDay(LocalDate.of(2025, 11, 2)));
	}

	@Test
	public void testFallBackDayAhead() throws OpenemsNamedException {
		final var hourly = parseDayAhead(Fixtures.DAY_AHEAD_FALL_BACK);
		assertEquals(24, hourly.size());
		// 00:00 CDT == 05:00Z
		assertEquals(Instant.parse("2025-11-02T05:00:00Z"), hourly.firstKey());
		assertEquals(4.3, hourly.firstEntry().getValue(), DELTA);
		// Ambiguous 01:00 resolves to the earlier offset (CDT) == 06:00Z
		assertEquals(4.2, hourly.get(Instant.parse("2025-11-02T06:00:00Z")), DELTA);
		// 03:00 CST == 09:00Z
		assertEquals(4.2, hourly.get(Instant.parse("2025-11-02T09:00:00Z")), DELTA);
		// 23:00 CST == 05:00Z next day
		assertEquals(0.8, hourly.get(Instant.parse("2025-11-03T05:00:00Z")), DELTA);

		final var prices = combine(hourly, ImmutableSortedMap.of(), ImmutableSortedMap.of(), 0);
		assertEquals(96, prices.getRawValues().size());
		assertFalse(prices.isEmpty());
	}

	@Test
	public void testQuartersOfDay() {
		assertEquals(92, ComEdApi.quartersOfDay(LocalDate.of(2025, 3, 9)));
		assertEquals(100, ComEdApi.quartersOfDay(LocalDate.of(2025, 11, 2)));
		assertEquals(96, ComEdApi.quartersOfDay(LocalDate.of(2025, 7, 1)));
	}

	@Test
	public void testBackoff() {
		assertEquals(Duration.ofMinutes(5), backoff(1, 5, 3));
		assertEquals(Duration.ofMinutes(10), backoff(2, 5, 3));
		assertEquals(Duration.ofMinutes(20), backoff(3, 5, 3));
		// beyond maxRetries -> cap
		assertEquals(Duration.ofMinutes(30), backoff(4, 5, 3));
		assertEquals(Duration.ofMinutes(30), backoff(100, 5, 3));
		// cap also applies within retries
		assertEquals(Duration.ofMinutes(30), backoff(3, 10, 5));
		assertEquals(Duration.ofMinutes(30), backoff(1, 60, 5));
		// small interval
		assertEquals(Duration.ofMinutes(1), backoff(1, 1, 10));
		assertEquals(Duration.ofMinutes(16), backoff(5, 1, 10));
		assertEquals(Duration.ofMinutes(30), backoff(6, 1, 10));
		// maxRetries = 0 -> cap immediately
		assertEquals(Duration.ofMinutes(30), backoff(1, 5, 0));
	}

	@Test
	public void testBackFillUrl() {
		final var start = ZonedDateTime.of(2025, 3, 9, 6, 0, 0, 0, ZoneOffset.UTC); // 00:00 CST
		final var end = ZonedDateTime.of(2025, 3, 10, 4, 55, 0, 0, ZoneOffset.UTC); // 23:55 CDT
		assertEquals("202503090000", ComEdApi.formatRange(start));
		assertEquals("202503092355", ComEdApi.formatRange(end));
		assertEquals("https://hourlypricing.comed.com/api?type=5minutefeed&datestart=202503090000&dateend=202503092355",
				ComEdApi.fiveMinuteFeedUrl(start, end));
	}
}
