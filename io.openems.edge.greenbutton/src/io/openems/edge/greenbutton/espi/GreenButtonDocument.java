package io.openems.edge.greenbutton.espi;

import java.util.List;
import java.util.Optional;

/**
 * Parsed Green Button (ESPI) document.
 *
 * @param series the {@link IntervalSeries}, one per MeterReading
 */
public record GreenButtonDocument(List<IntervalSeries> series) {

	/**
	 * Gets the first energy series describing consumption (forward flow
	 * direction or unspecified). Series with the most readings are preferred.
	 *
	 * @return the series or empty
	 */
	public Optional<IntervalSeries> consumptionEnergySeries() {
		return this.series.stream() //
				.filter(s -> s.type().isEnergy() && s.type().isConsumption()) //
				.sorted((a, b) -> Integer.compare(b.readings().size(), a.readings().size())) //
				.findFirst();
	}

	/**
	 * Gets the total number of readings of all series.
	 *
	 * @return the count
	 */
	public int readingCount() {
		return this.series.stream().mapToInt(s -> s.readings().size()).sum();
	}
}
