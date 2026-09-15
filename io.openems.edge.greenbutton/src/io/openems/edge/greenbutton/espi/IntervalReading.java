package io.openems.edge.greenbutton.espi;

import java.time.Duration;
import java.time.Instant;

/**
 * One ESPI {@code IntervalReading} with the value already scaled by its
 * {@link ReadingType} (Wh for energy, W for power).
 *
 * @param start    start of the interval
 * @param duration length of the interval
 * @param value    the scaled value
 * @param cost     the cost in 1/100000 of the currency unit; null if absent
 * @param quality  ESPI QualityOfReading code; null if absent
 */
public record IntervalReading(//
		Instant start, //
		Duration duration, //
		double value, //
		Long cost, //
		Integer quality //
) {

	/**
	 * Gets the average power over the interval, assuming {@link #value()} is an
	 * energy in Wh.
	 *
	 * @return the average power in W; NaN for a zero duration
	 */
	public double averagePower() {
		var seconds = this.duration.toSeconds();
		if (seconds == 0) {
			return Double.NaN;
		}
		return this.value * 3600d / seconds;
	}
}
