package io.openems.backend.openadr.vtn;

import java.util.Collection;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.backend.common.component.AbstractOpenemsBackendComponent;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;

@Designate(ocd = Config.class, factory = true)
@Component(name = "Openadr.Vtn", immediate = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class OpenAdrVtnImpl extends AbstractOpenemsBackendComponent implements OpenAdrVtn {

	private final Logger log = LoggerFactory.getLogger(OpenAdrVtnImpl.class);
	private final VtnRegistry registry;
	private Server server;

	public OpenAdrVtnImpl() {
		super("Openadr.Vtn");
		this.registry = new VtnRegistry();
	}

	@Activate
	private void activate(Config config) throws OpenemsException {
		try {
			this.server = new Server();
			if (config.requireTls()) {
				var sslContextFactory = new SslContextFactory.Server();
				sslContextFactory.setKeyStorePath(config.keyStorePath());
				sslContextFactory.setKeyStorePassword(config.keyStorePassword());
				sslContextFactory.setKeyStoreType("PKCS12");
				var connector = new ServerConnector(this.server, sslContextFactory);
				connector.setPort(config.port());
				this.server.addConnector(connector);
			} else {
				var connector = new ServerConnector(this.server);
				connector.setPort(config.port());
				this.server.addConnector(connector);
			}
			this.server.setHandler(new OadrHandler(this.registry, config.path(), config.vtnId(),
					config.pollFrequencySeconds()));
			this.server.start();
			this.logInfo(this.log, "Openadr.Vtn started on port [" + config.port() + "].");
		} catch (Exception e) {
			throw new OpenemsException("Openadr.Vtn failed to start", e);
		}
	}

	@Deactivate
	private void deactivate() {
		if (this.server != null) {
			try {
				this.server.stop();
				this.logInfo(this.log, "Openadr.Vtn stopped.");
			} catch (Exception e) {
				this.logWarn(this.log, "Openadr.Vtn failed to stop: " + e.getMessage());
			}
		}
	}

	@Override
	public Collection<Ven> getVens() {
		return this.registry.getVens();
	}

	@Override
	public Collection<DrEvent> getEvents() {
		return this.registry.getEvents();
	}

	@Override
	public DrEvent createEvent(CreateEventRequest request) throws OpenemsNamedException {
		return this.registry.createEvent(request);
	}

	@Override
	public boolean cancelEvent(String eventId) {
		return this.registry.cancelEvent(eventId);
	}

	@Override
	public Collection<VenReport> getReports() {
		return this.registry.getReports();
	}
}
