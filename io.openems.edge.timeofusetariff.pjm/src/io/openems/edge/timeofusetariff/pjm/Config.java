package io.openems.edge.timeofusetariff.pjm;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Time-Of-Use Tariff PJM", //
		description = "Time-Of-Use Tariff implementation for PJM day-ahead hourly LMPs (Data Miner 2).")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "timeOfUseTariff0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "API key", description = "PJM Data Miner 2 subscription key (Ocp-Apim-Subscription-Key)", type = AttributeType.PASSWORD)
	String apiKey() default "";

	@AttributeDefinition(name = "Pnode-ID", description = "PJM pricing node ID; default 33092371 is the COMED zone")
	long pnodeId() default 33092371L;

	@AttributeDefinition(name = "Zone", description = "Name of the pricing node (pnode_name), used to match items when no Pnode-ID is configured")
	String zone() default "COMED";

	@AttributeDefinition(name = "Polling interval [min]", description = "Interval in minutes between price updates", min = "1")
	int pollingIntervalMinutes() default 60;

	@AttributeDefinition(name = "Ancillary costs [Currency/MWh]", description = "Additional costs added to every LMP price")
	double ancillaryCostsPerMwh() default 0;

	@AttributeDefinition(name = "HTTP timeout [s]", description = "Connect and read timeout for HTTP requests", min = "1")
	int httpTimeoutSeconds() default 15;

	@AttributeDefinition(name = "Max retries", description = "Number of fast retries with exponential backoff after a failed request before falling back to the polling interval", min = "0")
	int maxRetries() default 3;

	@AttributeDefinition(name = "Fixture file", description = "Optional path to a recorded Data Miner 2 JSON response. If set, prices are read from this file instead of the PJM API (offline/simulator mode)")
	String useFixtureFile() default "";

	String webconsole_configurationFactory_nameHint() default "Time-Of-Use Tariff PJM [{id}]";
}
