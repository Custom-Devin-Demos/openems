package io.openems.edge.greenbutton;

import static io.openems.common.utils.ThreadPoolUtils.shutdownAndAwaitTermination;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import io.openems.common.bridge.http.api.BridgeHttp;
import io.openems.common.bridge.http.api.BridgeHttpFactory;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.greenbutton.espi.EspiParser;
import io.openems.edge.greenbutton.espi.GreenButtonDocument;
import io.openems.edge.timedata.api.Timedata;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "GreenButton.Import", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class GreenButtonImportImpl extends AbstractOpenemsComponent implements GreenButtonImport, OpenemsComponent {

	private final Logger log = LoggerFactory.getLogger(GreenButtonImportImpl.class);

	@Reference
	private ComponentManager componentManager;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	private Timedata timedata;

	@Reference
	private BridgeHttpFactory httpBridgeFactory;
	private BridgeHttp httpBridge;

	private ScheduledExecutorService executor;
	private Config config;
	private ChannelAddress targetChannel;
	private ChannelAddress powerChannel;

	public GreenButtonImportImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				GreenButtonImport.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsNamedException {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
		this.targetChannel = ChannelAddress.fromString(config.targetChannel());
		this.powerChannel = config.powerChannel() == null || config.powerChannel().isBlank() //
				? null //
				: ChannelAddress.fromString(config.powerChannel());
		this._setImportState(ImportState.IDLE);
		if (!config.enabled()) {
			return;
		}
		if (config.source() == Source.URL) {
			this.httpBridge = this.httpBridgeFactory.get();
		}
		this.executor = Executors.newSingleThreadScheduledExecutor();
		if (config.pollingIntervalHours() > 0) {
			this.executor.scheduleAtFixedRate(this::runImport, 0, config.pollingIntervalHours(), TimeUnit.HOURS);
		} else {
			this.executor.execute(this::runImport);
		}
	}

	@Override
	@Deactivate
	protected void deactivate() {
		shutdownAndAwaitTermination(this.executor, 5);
		this.executor = null;
		this.httpBridgeFactory.unget(this.httpBridge);
		this.httpBridge = null;
		super.deactivate();
	}

	/**
	 * Runs one import synchronously: read, parse and write the document.
	 *
	 * <p>
	 * Exposed for tests; the OSGi component calls this from its executor.
	 *
	 * @return the number of written interval readings, or -1 if the import failed
	 */
	public synchronized int runImport() {
		this._setImportState(ImportState.RUNNING);
		try {
			var xml = this.readSource();
			var document = EspiParser.parse(xml);
			var count = this.writeDocument(document);
			this._setImportedReadings(count);
			this._setLastImport(Instant.now(this.componentManager.getClock()).getEpochSecond());
			this._setLastError(null);
			this._setImportFailed(false);
			this._setImportState(ImportState.DONE);
			return count;

		} catch (Exception e) {
			this.logWarn(this.log, "Green Button import failed: " + e.getMessage());
			this._setLastError(e.getMessage());
			this._setImportFailed(true);
			this._setImportState(ImportState.FAILED);
			return -1;
		}
	}

	private String readSource() throws OpenemsNamedException {
		return switch (this.config.source()) {
		case FILE -> {
			if (this.config.path().isBlank()) {
				throw new OpenemsException("No file path configured");
			}
			try {
				yield Files.readString(Path.of(this.config.path()), StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new OpenemsException("Unable to read file [" + this.config.path() + "]: " + e.getMessage());
			}
		}
		case URL -> {
			if (this.config.url().isBlank()) {
				throw new OpenemsException("No URL configured");
			}
			var endpoint = BridgeHttp.create(this.config.url()) //
					.onlyIf(!this.config.authorizationHeader().isBlank(), //
							b -> b.setHeader("Authorization", this.config.authorizationHeader())) //
					.setHeader("Accept", "application/atom+xml, application/xml, text/xml") //
					.setConnectTimeout(15_000) //
					.setReadTimeout(60_000) //
					.build();
			try {
				yield this.httpBridge.request(endpoint).get().data();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new OpenemsException("Download interrupted");
			} catch (ExecutionException e) {
				var cause = e.getCause() != null ? e.getCause() : e;
				throw new OpenemsException("Unable to download [" + this.config.url() + "]: " + cause.getMessage());
			}
		}
		};
	}

	/**
	 * Writes all series of the document to the {@link Timedata} service.
	 *
	 * <p>
	 * Readings not newer than {@link ChannelId#LAST_READING} are skipped, so a
	 * periodically re-downloaded document is only imported incrementally.
	 *
	 * @param document the parsed {@link GreenButtonDocument}
	 * @return number of written readings
	 * @throws OpenemsNamedException on write error
	 */
	protected int writeDocument(GreenButtonDocument document) throws OpenemsNamedException {
		var lastReading = this.getLastReadingChannel().getNextValue().asOptional() //
				.map(Instant::ofEpochSecond) //
				.orElse(null);

		var energy = new TreeMap<ZonedDateTime, JsonElement>();
		var power = new TreeMap<ZonedDateTime, JsonElement>();
		Instant first = null;
		Instant last = lastReading;
		for (var series : document.series()) {
			if (!series.isEnergy() || !series.type().isConsumption()) {
				continue;
			}
			var wh = this.config.cumulative() ? series.cumulativeWh() : series.perIntervalWh();
			var perInterval = series.perIntervalWh();
			for (var entry : wh.entrySet()) {
				var start = entry.getKey();
				if (lastReading != null && !start.isAfter(lastReading)) {
					continue;
				}
				var timestamp = ZonedDateTime.ofInstant(start, ZoneId.of("UTC"));
				energy.merge(timestamp, new JsonPrimitive(Math.round(entry.getValue())), GreenButtonImportImpl::sum);
				var reading = series.readings().get(start);
				var intervalWh = perInterval.get(start);
				if (reading != null && intervalWh != null && !reading.duration().isZero()) {
					var averagePower = intervalWh * 3600d / reading.duration().toSeconds();
					power.merge(timestamp, new JsonPrimitive(Math.round(averagePower)), GreenButtonImportImpl::sum);
				}
				if (first == null || start.isBefore(first)) {
					first = start;
				}
				if (last == null || start.isAfter(last)) {
					last = start;
				}
			}
		}

		if (energy.isEmpty()) {
			return 0;
		}
		this.timedata.writeHistoricData(this.targetChannel, energy);
		if (this.powerChannel != null && !power.isEmpty()) {
			this.timedata.writeHistoricData(this.powerChannel, power);
		}
		if (this.getFirstReadingChannel().getNextValue().asOptional().isEmpty() && first != null) {
			this._setFirstReading(first.getEpochSecond());
		}
		if (last != null) {
			this._setLastReading(last.getEpochSecond());
		}
		return energy.size();
	}

	private static JsonElement sum(JsonElement a, JsonElement b) {
		return new JsonPrimitive(a.getAsLong() + b.getAsLong());
	}

	@Override
	public String debugLog() {
		return "State:" + this.getImportState().getName() //
				+ "|Readings:" + this.getImportedReadingsChannel().value().asString();
	}
}
