package io.openems.edge.timeofusetariff.comed;

/**
 * Selects which ComEd Hourly Pricing feed is overlaid on the day-ahead prices
 * for the current hour.
 */
public enum Feed {
	/**
	 * Day-ahead hourly prices only.
	 */
	DAY_AHEAD,
	/**
	 * Day-ahead prices plus the real-time 5-minute feed, averaged into quarters.
	 */
	FIVE_MINUTE,
	/**
	 * Day-ahead prices plus the running average of the current hour.
	 */
	CURRENT_HOUR_AVERAGE;
}
