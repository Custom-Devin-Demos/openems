package io.openems.edge.controller.api.openadr.oadr;

public enum EventPhase {
	BEFORE, RAMP_UP, ACTIVE, RECOVERY, AFTER;

	/**
	 * Is curtailment applied in this phase?.
	 * 
	 * @return true for RAMP_UP, ACTIVE and RECOVERY
	 */
	public boolean isCurtailing() {
		return this == RAMP_UP || this == ACTIVE || this == RECOVERY;
	}
}
