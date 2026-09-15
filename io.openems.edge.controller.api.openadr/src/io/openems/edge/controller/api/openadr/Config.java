package io.openems.edge.controller.api.openadr;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Controller Api OpenADR", //
		description = "OpenADR 2.0b Virtual End Node (VEN). Polls a VTN for demand response events and curtails ESS and EVCS.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "ctrlOpenAdr0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "VTN URL", description = "Base URL of the VTN, e.g. https://vtn.example.com:8443/OpenADR2/Simple/2.0b")
	String vtnUrl() default "http://localhost:8082/OpenADR2/Simple/2.0b";

	@AttributeDefinition(name = "VEN Name", description = "Name of this VEN, sent with the party registration")
	String venName() default "openems-ven";

	@AttributeDefinition(name = "VEN-ID", description = "Optional VEN-ID; usually assigned by the VTN during registration", required = false)
	String venId() default "";

	@AttributeDefinition(name = "Registration-ID", description = "Optional Registration-ID of a previous registration", required = false)
	String registrationId() default "";

	@AttributeDefinition(name = "Poll interval [s]", description = "Interval for oadrPoll requests; the VTN may request a different frequency")
	int pollIntervalSeconds() default 10;

	@AttributeDefinition(name = "TLS", description = "Trust configuration for https connections")
	Tls tls() default Tls.SYSTEM_TRUSTSTORE;

	@AttributeDefinition(name = "Trust-Store path", description = "Path to a PKCS12 trust store (TLS = CUSTOM)", required = false)
	String trustStorePath() default "";

	@AttributeDefinition(name = "Trust-Store password", description = "Password of the trust store", type = org.osgi.service.metatype.annotations.AttributeType.PASSWORD, required = false)
	String trustStorePassword() default "";

	@AttributeDefinition(name = "Client certificate path", description = "Path to a PKCS12 key store with the VEN client certificate (optional)", required = false)
	String clientCertPath() default "";

	@AttributeDefinition(name = "Client key password", description = "Password of the client key store", type = org.osgi.service.metatype.annotations.AttributeType.PASSWORD, required = false)
	String clientKeyPassword() default "";

	@AttributeDefinition(name = "Market context", description = "Only events of this market context are handled; empty accepts all", required = false)
	String marketContext() default "";

	@AttributeDefinition(name = "Auto Opt-In", description = "Automatically opt-in to every received event")
	boolean autoOptIn() default true;

	@AttributeDefinition(name = "Ess-ID", description = "ID of the ManagedSymmetricEss to curtail (optional)", required = false)
	String ess_id() default "";

	@AttributeDefinition(name = "Evcs-IDs", description = "IDs of ManagedEvcs to curtail", required = false)
	String[] evcs_ids() default {};

	@AttributeDefinition(name = "Evse-IDs", description = "IDs of EVSE Single-Controllers to curtail", required = false)
	String[] evse_ids() default {};

	@AttributeDefinition(name = "Heat-Pump-IDs", description = "IDs of SG-Ready Heat-Pump Controllers to lock", required = false)
	String[] heatPump_ids() default {};

	@AttributeDefinition(name = "Curtailment ESS discharge limit [W]", description = "Maximum ESS discharge power during an event with level >= 2; 0 blocks discharging")
	int curtailmentEssDischargeLimitW() default 0;

	@AttributeDefinition(name = "Curtailment EVCS charge limit [W]", description = "Charge power limit for every EVCS during an event; 0 pauses charging")
	int curtailmentEvcsChargeLimitW() default 0;

	@AttributeDefinition(name = "Price signal threshold [Currency/MWh]", description = "PRICE signals above this value trigger curtailment")
	double priceSignalThresholdPerMwh() default 100;

	@AttributeDefinition(name = "Report interval [s]", description = "Interval for TELEMETRY_USAGE reports (oadrUpdateReport); the VTN may request a different interval")
	int reportIntervalSeconds() default 60;

	String webconsole_configurationFactory_nameHint() default "Controller Api OpenADR [{id}]";

}
