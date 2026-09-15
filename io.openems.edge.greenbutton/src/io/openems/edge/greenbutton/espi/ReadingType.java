package io.openems.edge.greenbutton.espi;

/**
 * ESPI {@code ReadingType} describing the values of an {@code IntervalBlock}.
 *
 * @param uom                   the unit of measure
 * @param powerOfTenMultiplier  the multiplier exponent (e.g. 3 for kWh)
 * @param accumulationBehaviour ESPI AccumulationKind (4 = deltaData, 3 =
 *                              cumulative; 0 = unspecified)
 * @param flowDirection         ESPI FlowDirectionKind (1 = forward/delivered,
 *                              19 = reverse/received; 0 = unspecified)
 * @param commodity             ESPI CommodityKind (1 = electricity secondary
 *                              metered)
 * @param intervalLength        the interval length in seconds; 0 if unknown
 * @param currency              ISO 4217 numeric currency code; 0 if unknown
 */
public record ReadingType(//
		UnitOfMeasure uom, //
		int powerOfTenMultiplier, //
		int accumulationBehaviour, //
		int flowDirection, //
		int commodity, //
		int intervalLength, //
		int currency //
) {

	public static final int FLOW_DIRECTION_FORWARD = 1;
	public static final int FLOW_DIRECTION_REVERSE = 19;
	public static final int ACCUMULATION_CUMULATIVE = 3;
	public static final int ACCUMULATION_DELTA_DATA = 4;

	/** A ReadingType for Wh interval readings without further metadata. */
	public static final ReadingType DEFAULT = new ReadingType(UnitOfMeasure.WATT_HOURS, 0, 0, 0, 0, 0, 0);

	/**
	 * Applies the {@link #powerOfTenMultiplier()} and converts to the base unit
	 * (Wh for energy, W for power, otherwise the plain unit).
	 *
	 * @param rawValue the value as found in the document
	 * @return the scaled value
	 */
	public double scale(long rawValue) {
		var value = rawValue * Math.pow(10, this.powerOfTenMultiplier);
		if (this.uom.isEnergy()) {
			return this.uom.toWattHours(value);
		}
		return value;
	}

	/**
	 * Is this an energy reading (convertible to Wh)?.
	 *
	 * @return true for energy
	 */
	public boolean isEnergy() {
		return this.uom.isEnergy();
	}

	/**
	 * Is this a power reading in W?.
	 *
	 * @return true for power
	 */
	public boolean isPower() {
		return this.uom == UnitOfMeasure.WATT;
	}

	/**
	 * Is the flow direction forward (delivered to the customer) or unspecified?.
	 *
	 * @return true for consumption
	 */
	public boolean isConsumption() {
		return this.flowDirection == 0 || this.flowDirection == FLOW_DIRECTION_FORWARD;
	}
}
