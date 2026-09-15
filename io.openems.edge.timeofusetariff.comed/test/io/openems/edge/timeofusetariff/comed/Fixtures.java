package io.openems.edge.timeofusetariff.comed;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Recorded responses of the ComEd Hourly Pricing API.
 */
public final class Fixtures {

	/** {@code api?type=5minutefeed}, recorded 2026-09-15. */
	public static final String FIVE_MINUTE_FEED = read("5minutefeed.json");
	/** {@code api?type=currenthouraverage}, recorded 2026-09-15. */
	public static final String CURRENT_HOUR_AVERAGE = read("currenthouraverage.json");
	/** {@code rrtp/ServletFeed?type=daynexttoday}, recorded 2026-09-15. */
	public static final String DAY_AHEAD = read("dayahead.txt");
	/** {@code api?type=5minutefeed&datestart=202503090000&dateend=202503092359}. */
	public static final String FIVE_MINUTE_FEED_SPRING_FORWARD = read("5minutefeed_2025-03-09.json");
	/** {@code api?type=5minutefeed&datestart=202511020000&dateend=202511022359}. */
	public static final String FIVE_MINUTE_FEED_FALL_BACK = read("5minutefeed_2025-11-02.json");
	/** {@code rrtp/ServletFeed?type=day&date=20250309}. */
	public static final String DAY_AHEAD_SPRING_FORWARD = read("dayahead_2025-03-09.txt");
	/** {@code rrtp/ServletFeed?type=day&date=20251102}. */
	public static final String DAY_AHEAD_FALL_BACK = read("dayahead_2025-11-02.txt");

	private Fixtures() {
	}

	private static String read(String name) {
		try (var stream = Fixtures.class.getResourceAsStream("fixtures/" + name)) {
			if (stream == null) {
				throw new IllegalStateException("Fixture not found: " + name);
			}
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
