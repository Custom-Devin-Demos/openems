package io.openems.backend.openadr.vtn;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Openadr.Vtn", description = "Minimal OpenADR 2.0b Virtual Top Node (VTN) simulator")
@interface Config {

	@AttributeDefinition(name = "Port")
	int port() default 8082;

	@AttributeDefinition(name = "Path")
	String path() default "/OpenADR2/Simple/2.0b";

	@AttributeDefinition(name = "VTN ID")
	String vtnId() default "openems-vtn";

	@AttributeDefinition(name = "Poll frequency (seconds)")
	int pollFrequencySeconds() default 10;

	@AttributeDefinition(name = "Require TLS")
	boolean requireTls() default false;

	@AttributeDefinition(name = "Key store path")
	String keyStorePath() default "";

	@AttributeDefinition(name = "Key store password", type = AttributeType.PASSWORD)
	String keyStorePassword() default "";

	String webconsole_configurationFactory_nameHint() default "OpenADR VTN Simulator";
}
