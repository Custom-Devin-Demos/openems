package io.openems.edge.controller.api.openadr;

import io.openems.common.types.OptionsEnum;

public enum RegistrationState implements OptionsEnum {
	UNDEFINED(-1, "Undefined"), //
	UNREGISTERED(0, "Unregistered"), //
	REGISTERING(1, "Registering"), //
	REGISTERED(2, "Registered"), //
	FAILED(3, "Failed");

	private final int value;
	private final String name;

	private RegistrationState(int value, String name) {
		this.value = value;
		this.name = name;
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
}
