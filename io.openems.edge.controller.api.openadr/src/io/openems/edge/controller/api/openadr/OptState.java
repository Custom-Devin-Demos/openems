package io.openems.edge.controller.api.openadr;

import io.openems.common.types.OptionsEnum;

public enum OptState implements OptionsEnum {
	UNDEFINED(-1, "Undefined", "optIn"), //
	OPT_IN(0, "Opt-In", "optIn"), //
	OPT_OUT(1, "Opt-Out", "optOut");

	private final int value;
	private final String name;
	private final String oadrOptType;

	private OptState(int value, String name, String oadrOptType) {
		this.value = value;
		this.name = name;
		this.oadrOptType = oadrOptType;
	}

	@Override
	public int getValue() {
		return this.value;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public OptionsEnum getUndefined() {
		return UNDEFINED;
	}

	/**
	 * Gets the OpenADR {@code optType} value.
	 * 
	 * @return "optIn" or "optOut"
	 */
	public String getOadrOptType() {
		return this.oadrOptType;
	}
}
