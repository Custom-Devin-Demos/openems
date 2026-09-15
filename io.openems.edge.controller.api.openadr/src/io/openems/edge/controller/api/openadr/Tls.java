package io.openems.edge.controller.api.openadr;

public enum Tls {
	/**
	 * Use the JVM default trust store.
	 */
	SYSTEM_TRUSTSTORE,
	/**
	 * Accept every server certificate. Only for testing.
	 */
	TRUST_ALL,
	/**
	 * Use the configured PKCS12 trust store.
	 */
	CUSTOM;
}
