package io.openems.edge.controller.api.openadr.oadr;

public enum SignalType {
	/**
	 * signalName "simple" or "SIMPLE"; levels 0 (normal), 1 (moderate), 2 (high),
	 * 3 (special).
	 */
	SIMPLE,
	/**
	 * signalName "ELECTRICITY_PRICE", signalType "price"; value normalised to
	 * Currency/MWh.
	 */
	PRICE,
	/**
	 * Any other signal; ignored for curtailment.
	 */
	UNKNOWN;

	/**
	 * Determines the {@link SignalType} from OpenADR signalName and signalType.
	 * 
	 * @param signalName the eiEventSignal signalName
	 * @param signalType the eiEventSignal signalType
	 * @return the {@link SignalType}
	 */
	public static SignalType from(String signalName, String signalType) {
		if (signalName == null) {
			return UNKNOWN;
		}
		if (signalName.equalsIgnoreCase("simple")) {
			return SIMPLE;
		}
		if (signalName.equalsIgnoreCase("ELECTRICITY_PRICE") || "price".equalsIgnoreCase(signalType)) {
			return PRICE;
		}
		return UNKNOWN;
	}
}
