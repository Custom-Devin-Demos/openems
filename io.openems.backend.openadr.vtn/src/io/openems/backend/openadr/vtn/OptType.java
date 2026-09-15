package io.openems.backend.openadr.vtn;

/**
 * OpenADR event opt response.
 */
public enum OptType {
	OPT_IN("optIn"), OPT_OUT("optOut");

	private final String oadr;

	OptType(String oadr) {
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

	/**
	 * Parses an OpenADR value.
	 *
	 * @param value OpenADR value
	 * @return parsed option
	 */
	public static OptType fromOadr(String value) {
		for (var type : values()) {
			if (type.oadr.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
				return type;
			}
		}
		return null;
	}
}
