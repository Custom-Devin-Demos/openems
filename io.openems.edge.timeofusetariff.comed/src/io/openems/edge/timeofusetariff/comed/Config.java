package io.openems.edge.timeofusetariff.comed;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(//
		name = "Time-Of-Use Tariff ComEd", //
		description = "Time-Of-Use Tariff implementation for the ComEd Hourly Pricing program (Illinois, USA).")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "timeOfUseTariff0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Feed", description = "Real-time feed overlaid on the day-ahead prices for the current hour")
	Feed feed() default Feed.DAY_AHEAD;

	@AttributeDefinition(name = "Polling interval [min]", description = "Interval between two polls of the ComEd API in minutes", min = "1")
	int pollingIntervalMinutes() default 5;

	@AttributeDefinition(name = "Ancillary costs [Currency/MWh]", description = "Fixed costs added to every price, e.g. delivery charges")
	double ancillaryCostsPerMwh() default 0;

	@AttributeDefinition(name = "HTTP timeout [s]", description = "Connect and read timeout for HTTP requests in seconds", min = "1")
	int httpTimeoutSeconds() default 15;

	@AttributeDefinition(name = "Max retries", description = "Number of exponentially delayed retries after a failed poll before falling back to the maximum backoff", min = "0")
	int maxRetries() default 3;

	String webconsole_configurationFactory_nameHint() default "Time-Of-Use Tariff ComEd [{id}]";
}
