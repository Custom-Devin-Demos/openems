package io.openems.edge.timeofusetariff.comed;

import static io.openems.common.utils.JsonUtils.getAsString;
import static io.openems.common.utils.JsonUtils.parseToJsonArray;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableSortedMap;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.utils.DateUtils;
import io.openems.edge.timeofusetariff.api.TimeOfUsePrices;

/**
 * Stateless helpers for the ComEd Hourly Pricing API.
 *
 * <p>
 * All ComEd prices are published in US-Cent/kWh. OpenEMS expects
 * Currency/MWh, hence every value is multiplied by
 * {@link #CENTS_PER_KWH_TO_CURRENCY_PER_MWH}.
 */
public final class ComEdApi {

	public static final ZoneId ZONE = ZoneId.of("America/Chicago");
	public static final double CENTS_PER_KWH_TO_CURRENCY_PER_MWH = 10d;

	public static final String API_URL = "https://hourlypricing.comed.com/api";
	public static final String FIVE_MINUTE_FEED_URL = API_URL + "?type=5minutefeed";
	public static final String CURRENT_HOUR_AVERAGE_URL = API_URL + "?type=currenthouraverage";
	/** Day-ahead hourly prices of the current day (America/Chicago). */
	public static final String DAY_AHEAD_TODAY_URL = "https://hourlypricing.comed.com/rrtp/ServletFeed?type=daynexttoday";
	/**
	 * Day-ahead hourly prices of the next day; published around 16:30
	 * America/Chicago, returns an empty array before.
	 */
	public static final String DAY_AHEAD_TOMORROW_URL = "https://hourlypricing.comed.com/rrtp/ServletFeed?type=daynexttomorrow";

	private static final DateTimeFormatter RANGE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
	private static final Duration QUARTER = Duration.ofMinutes(15);

	/**
	 * Matches one entry of the day-ahead feed, e.g.
	 * {@code [Date.UTC(2025,10,2,13,0,0), 2.2]}. Despite the name, the fields are
	 * the local America/Chicago date and hour (month is zero-based).
	 */
	private static final Pattern DAY_AHEAD_ENTRY = Pattern.compile(
			"\\[\\s*Date\\.UTC\\(\\s*(\\d{4})\\s*,\\s*(\\d{1,2})\\s*,\\s*(\\d{1,2})\\s*,\\s*(\\d{1,2})\\s*,\\s*\\d{1,2}\\s*,\\s*\\d{1,2}\\s*\\)\\s*,\\s*(-?[0-9.]+)\\s*\\]");

	private ComEdApi() {
	}

	/**
	 * Builds the 5-minute feed URL with a {@code datestart}/{@code dateend} back-fill
	 * range.
	 *
	 * @param start the inclusive start, converted to America/Chicago
	 * @param end   the inclusive end, converted to America/Chicago
	 * @return the URL
	 */
	public static String fiveMinuteFeedUrl(ZonedDateTime start, ZonedDateTime end) {
		return FIVE_MINUTE_FEED_URL //
				+ "&datestart=" + formatRange(start) //
				+ "&dateend=" + formatRange(end);
	}

	/**
	 * Formats a timestamp as {@code yyyyMMddHHmm} in America/Chicago.
	 *
	 * @param time the time
	 * @return the formatted string
	 */
	public static String formatRange(ZonedDateTime time) {
		return time.withZoneSameInstant(ZONE).format(RANGE_FORMATTER);
	}

	/**
	 * Parses the JSON of the {@code 5minutefeed} or {@code currenthouraverage}
	 * feeds: an array of {@code {"millisUTC":"…","price":"…"}}.
	 *
	 * @param json the response body
	 * @return the raw prices in Cent/kWh keyed by the UTC instant
	 * @throws OpenemsNamedException on invalid or empty JSON
	 */
	public static ImmutableSortedMap<Instant, Double> parseFeed(String json) throws OpenemsNamedException {
		if (json == null || json.isBlank()) {
			throw new OpenemsException("ComEd feed is empty");
		}
		final var array = parseToJsonArray(json);
		if (array.isEmpty()) {
			throw new OpenemsException("ComEd feed contains no entries");
		}
		final var result = ImmutableSortedMap.<Instant, Double>naturalOrder();
		try {
			for (var element : array) {
				final var millis = Long.parseLong(getAsString(element, "millisUTC"));
				final var price = Double.parseDouble(getAsString(element, "price"));
				result.put(Instant.ofEpochMilli(millis), price);
			}
			return result.buildOrThrow();
		} catch (IllegalArgumentException e) {
			throw new OpenemsException("ComEd feed contains invalid entries: " + e.getMessage());
		}
	}

	/**
	 * Parses the day-ahead feed. The body is a JavaScript-like array of
	 * {@code [Date.UTC(year,month0,day,hour,0,0), price]} pairs whose fields are
	 * local America/Chicago hour starts.
	 *
	 * @param body the response body
	 * @return the raw hourly prices in Cent/kWh keyed by the hour start; empty if
	 *         the feed is not published yet ({@code []})
	 * @throws OpenemsNamedException on invalid content
	 */
	public static ImmutableSortedMap<Instant, Double> parseDayAhead(String body) throws OpenemsNamedException {
		if (body == null) {
			throw new OpenemsException("ComEd day-ahead feed is empty");
		}
		final var trimmed = body.trim();
		if (trimmed.equals("[]")) {
			return ImmutableSortedMap.of();
		}
		if (!trimmed.startsWith("[")) {
			throw new OpenemsException("ComEd day-ahead feed has unexpected content: " + abbreviate(trimmed));
		}
		final var result = new TreeMap<Instant, Double>();
		final var matcher = DAY_AHEAD_ENTRY.matcher(trimmed);
		while (matcher.find()) {
			final var date = LocalDate.of(//
					Integer.parseInt(matcher.group(1)), //
					Integer.parseInt(matcher.group(2)) + 1, //
					Integer.parseInt(matcher.group(3)));
			final var hour = Integer.parseInt(matcher.group(4));
			final var price = Double.parseDouble(matcher.group(5));
			// ZonedDateTime.of resolves non-existing (spring-forward) and ambiguous
			// (fall-back) local hours deterministically
			final var start = ZonedDateTime.of(LocalDateTime.of(date, LocalTime.of(hour, 0)), ZONE);
			result.merge(start.toInstant(), price, (a, b) -> (a + b) / 2);
		}
		if (result.isEmpty()) {
			throw new OpenemsException("ComEd day-ahead feed contains no entries: " + abbreviate(trimmed));
		}
		return ImmutableSortedMap.copyOf(result);
	}

	/**
	 * Converts a ComEd price in Cent/kWh to Currency/MWh, adds the ancillary costs
	 * and rounds to four decimals.
	 *
	 * @param centsPerKwh          the price in Cent/kWh
	 * @param ancillaryCostsPerMwh costs added in Currency/MWh
	 * @return the price in Currency/MWh
	 */
	public static double toCurrencyPerMwh(double centsPerKwh, double ancillaryCostsPerMwh) {
		return Math.round((centsPerKwh * CENTS_PER_KWH_TO_CURRENCY_PER_MWH + ancillaryCostsPerMwh) * 10_000d)
				/ 10_000d;
	}

	/**
	 * Averages 5-minute values into 15-minute quarters. Each value is assigned to
	 * the quarter that contains its timestamp.
	 *
	 * @param fiveMinuteValues raw values keyed by UTC instant
	 * @return the mean per quarter start
	 */
	public static ImmutableSortedMap<Instant, Double> aggregateToQuarters(SortedMap<Instant, Double> fiveMinuteValues) {
		final var sums = new TreeMap<Instant, double[]>();
		for (var entry : fiveMinuteValues.entrySet()) {
			final var quarter = roundDownToQuarter(entry.getKey());
			final var acc = sums.computeIfAbsent(quarter, k -> new double[2]);
			acc[0] += entry.getValue();
			acc[1]++;
		}
		final var result = ImmutableSortedMap.<Instant, Double>naturalOrder();
		for (var entry : sums.entrySet()) {
			result.put(entry.getKey(), entry.getValue()[0] / entry.getValue()[1]);
		}
		return result.buildOrThrow();
	}

	/**
	 * Expands hourly values into four quarter values each.
	 *
	 * @param hourlyValues raw values keyed by hour start
	 * @return the quarter values
	 */
	public static ImmutableSortedMap<Instant, Double> expandHourlyToQuarters(SortedMap<Instant, Double> hourlyValues) {
		final var result = ImmutableSortedMap.<Instant, Double>naturalOrder();
		for (var entry : hourlyValues.entrySet()) {
			var start = entry.getKey();
			for (var i = 0; i < 4; i++) {
				result.put(start, entry.getValue());
				start = start.plus(QUARTER);
			}
		}
		return result.buildOrThrow();
	}

	/**
	 * Builds the final {@link TimeOfUsePrices} in Currency/MWh from raw day-ahead
	 * hourly prices and optional real-time actuals, both in Cent/kWh.
	 *
	 * <p>
	 * Day-ahead values are the baseline. Actuals of the 5-minute feed replace the
	 * quarters they cover; a current-hour-average value replaces the whole hour it
	 * belongs to.
	 *
	 * @param dayAheadHourly       raw hourly day-ahead prices; may be empty
	 * @param fiveMinuteActuals    raw 5-minute prices; may be empty
	 * @param currentHourAverage   raw current-hour-average entries; may be empty
	 * @param ancillaryCostsPerMwh costs added to every price
	 * @return the prices
	 */
	public static TimeOfUsePrices combine(//
			SortedMap<Instant, Double> dayAheadHourly, //
			SortedMap<Instant, Double> fiveMinuteActuals, //
			SortedMap<Instant, Double> currentHourAverage, //
			double ancillaryCostsPerMwh) {
		final var raw = new TreeMap<Instant, Double>(expandHourlyToQuarters(dayAheadHourly));
		raw.putAll(aggregateToQuarters(fiveMinuteActuals));
		for (var entry : currentHourAverage.entrySet()) {
			final var hourStart = hourStart(entry.getKey());
			raw.putAll(expandHourlyToQuarters(new TreeMap<>(Map.of(hourStart, entry.getValue()))));
		}
		if (raw.isEmpty()) {
			return TimeOfUsePrices.EMPTY_PRICES;
		}
		final var result = ImmutableSortedMap.<Instant, Double>naturalOrder();
		for (var entry : raw.entrySet()) {
			result.put(entry.getKey(), toCurrencyPerMwh(entry.getValue(), ancillaryCostsPerMwh));
		}
		return TimeOfUsePrices.from(result.buildOrThrow());
	}

	/**
	 * Calculates the delay until the next poll after a failure: exponential
	 * backoff starting at the polling interval, capped at 30 minutes. After more
	 * than {@code maxRetries} consecutive failures the cap is used.
	 *
	 * @param consecutiveFailures    the number of consecutive failures (>= 1)
	 * @param pollingIntervalMinutes the configured polling interval
	 * @param maxRetries             the configured number of retries
	 * @return the delay
	 */
	public static Duration backoff(int consecutiveFailures, int pollingIntervalMinutes, int maxRetries) {
		final var cap = Duration.ofMinutes(30);
		if (consecutiveFailures > maxRetries) {
			return cap;
		}
		final var exponent = Math.min(Math.max(consecutiveFailures - 1, 0), 10);
		final var delay = Duration.ofMinutes((long) pollingIntervalMinutes << exponent);
		return delay.compareTo(cap) > 0 ? cap : delay;
	}

	/**
	 * Returns the start of the hour containing the given instant in
	 * America/Chicago.
	 *
	 * @param instant the instant
	 * @return the hour start
	 */
	public static Instant hourStart(Instant instant) {
		return instant.atZone(ZONE).truncatedTo(ChronoUnit.HOURS).toInstant();
	}

	/**
	 * Returns the number of quarters per local day.
	 *
	 * @param date the date
	 * @return 92 on spring-forward days, 100 on fall-back days, 96 otherwise
	 */
	public static long quartersOfDay(LocalDate date) {
		final var start = date.atStartOfDay(ZONE);
		final var end = date.plusDays(1).atStartOfDay(ZONE);
		return Duration.between(start, end).dividedBy(QUARTER);
	}

	private static Instant roundDownToQuarter(Instant instant) {
		return DateUtils.roundDownToQuarter(instant);
	}

	private static String abbreviate(String s) {
		return s.length() > 60 ? s.substring(0, 60) + "..." : s;
	}
}
