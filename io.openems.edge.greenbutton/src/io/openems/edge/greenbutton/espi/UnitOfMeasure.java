package io.openems.edge.greenbutton.espi;

/**
 * ESPI {@code UnitSymbolKind} (NAESB REQ.21 / IEC 61968-9) unit codes of a
 * {@code ReadingType/uom}.
 */
public enum UnitOfMeasure {
	NOT_APPLICABLE(0, "", false), //
	AMPERE(5, "A", false), //
	VOLT(29, "V", false), //
	JOULE(31, "J", true), //
	HERTZ(33, "Hz", false), //
	WATT(38, "W", false), //
	CUBIC_METER(42, "m³", false), //
	VOLT_AMPERE(61, "VA", false), //
	VAR(63, "var", false), //
	VOLT_AMPERE_HOURS(71, "VAh", false), //
	WATT_HOURS(72, "Wh", true), //
	VAR_HOURS(73, "varh", false), //
	AMPERE_HOURS(106, "Ah", false), //
	CUBIC_FEET(119, "ft³", false), //
	BTU(132, "BTU", true), //
	THERM(169, "therm", true), //
	UNKNOWN(-1, "?", false);

	private final int code;
	private final String symbol;
	private final boolean energy;

	private UnitOfMeasure(int code, String symbol, boolean energy) {
		this.code = code;
		this.symbol = symbol;
		this.energy = energy;
	}

	public int getCode() {
		return this.code;
	}

	public String getSymbol() {
		return this.symbol;
	}

	/**
	 * Is this unit an energy unit that can be converted to Wh?.
	 *
	 * @return true for energy units
	 */
	public boolean isEnergy() {
		return this.energy;
	}

	/**
	 * Converts a value of this unit to Wh.
	 *
	 * @param value the value in this unit
	 * @return the value in Wh; unchanged if this is not an energy unit
	 */
	public double toWattHours(double value) {
		return switch (this) {
		case WATT_HOURS -> value;
		case JOULE -> value / 3600d;
		case BTU -> value * 0.29307107;
		case THERM -> value * 29307.107;
		default -> value;
		};
	}

	/**
	 * Resolves the ESPI uom code.
	 *
	 * @param code the code
	 * @return the {@link UnitOfMeasure}; {@link #UNKNOWN} if not mapped
	 */
	public static UnitOfMeasure fromCode(int code) {
		for (var uom : values()) {
			if (uom.code == code) {
				return uom;
			}
		}
		return UNKNOWN;
	}
}
