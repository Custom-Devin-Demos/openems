package io.openems.edge.greenbutton.espi;

import java.time.Instant;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * All {@link IntervalReading}s of one ESPI {@code MeterReading}, i.e. all
 * IntervalBlocks that share the same {@link ReadingType}.
 *
 * @param usagePointId   the identifier (self link) of the UsagePoint; may be
 *                       empty
 * @param meterReadingId the identifier (self link) of the MeterReading; may be
 *                       empty
 * @param type           the {@link ReadingType}
 * @param readings       the readings by interval start
 */
public record IntervalSeries(//
		String usagePointId, //
		String meterReadingId, //
		ReadingType type, //
		SortedMap<Instant, IntervalReading> readings //
) {

	/**
	 * Gets the scaled values by interval start.
	 *
	 * @return the values (Wh for energy, W for power)
	 */
	public SortedMap<Instant, Double> values() {
		var result = new TreeMap<Instant, Double>();
		this.readings.forEach((start, reading) -> result.put(start, reading.value()));
		return result;
	}

	/**
	 * Is this an energy series (values in Wh)?.
	 *
	 * @return true for energy
	 */
	public boolean isEnergy() {
		return this.type.isEnergy();
	}

	/**
	 * Gets the energy per interval in Wh. If the document already contains
	 * cumulative register values, the differences between consecutive readings
	 * are returned (the first reading has no predecessor and is omitted).
	 *
	 * @return energy per interval by interval start
	 */
	public SortedMap<Instant, Double> perIntervalWh() {
		if (this.type.accumulationBehaviour() != ReadingType.ACCUMULATION_CUMULATIVE) {
			return this.values();
		}
		var result = new TreeMap<Instant, Double>();
		Double previous = null;
		for (var reading : this.readings.values()) {
			if (previous != null) {
				result.put(reading.start(), reading.value() - previous);
			}
			previous = reading.value();
		}
		return result;
	}

	/**
	 * Gets the cumulative energy in Wh by interval start, i.e. the sum of all
	 * intervals up to and including the given one. Cumulative register values
	 * are returned unchanged.
	 *
	 * @return cumulative energy by interval start
	 */
	public SortedMap<Instant, Double> cumulativeWh() {
		if (this.type.accumulationBehaviour() == ReadingType.ACCUMULATION_CUMULATIVE) {
			return this.values();
		}
		var result = new TreeMap<Instant, Double>();
		var sum = 0d;
		for (var reading : this.readings.values()) {
			sum += reading.value();
			result.put(reading.start(), sum);
		}
		return result;
	}

	/**
	 * Gets the costs by interval start for readings that carry a cost.
	 *
	 * @return the costs in 1/100000 of the currency unit
	 */
	public SortedMap<Instant, Long> costs() {
		var result = new TreeMap<Instant, Long>();
		this.readings.forEach((start, reading) -> {
			if (reading.cost() != null) {
				result.put(start, reading.cost());
			}
		});
		return result;
	}
}
