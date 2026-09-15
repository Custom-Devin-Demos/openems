package io.openems.backend.openadr.vtn;

/**
 * OpenADR event lifecycle status.
 */
public enum EventStatus {
	FAR("far"), NEAR("near"), ACTIVE("active"), COMPLETED("completed"), CANCELLED("cancelled");

	private final String oadr;

	EventStatus(String oadr) {
		this.oadr = oadr;
	}

	/**
	 * Gets the OpenADR representation.
	 *
	 * @return OpenADR value
	 */
	public String toOadr() {
		return this.oadr;
	}
}
