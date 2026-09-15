package io.openems.edge.timeofusetariff.pjm;

import static io.openems.edge.timeofusetariff.api.utils.TimeOfUseTariffUtils.generateDebugLog;
import static io.openems.edge.timeofusetariff.pjm.PjmParser.EPT;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.bridge.http.api.BridgeHttp;
import io.openems.common.bridge.http.api.BridgeHttp.Endpoint;
import io.openems.common.bridge.http.api.BridgeHttpFactory;
import io.openems.common.bridge.http.api.HttpError;
import io.openems.common.bridge.http.api.HttpMethod;
import io.openems.common.bridge.http.api.HttpResponse;
import io.openems.common.bridge.http.api.UrlBuilder;
import io.openems.common.bridge.http.time.DelayTimeProvider;
import io.openems.common.bridge.http.time.HttpBridgeTimeService;
import io.openems.common.bridge.http.time.HttpBridgeTimeServiceDefinition;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.meta.Meta;
import io.openems.edge.timeofusetariff.api.TimeOfUsePrices;
import io.openems.edge.timeofusetariff.api.TimeOfUseTariff;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "TimeOfUseTariff.PJM", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class TimeOfUseTariffPjmImpl extends AbstractOpenemsComponent
		implements TimeOfUseTariff, OpenemsComponent, TimeOfUseTariffPjm {

	public static final String API_URL = "https://api.pjm.com/api/v1/da_hrl_lmps";
	public static final String API_KEY_HEADER = "Ocp-Apim-Subscription-Key";
	public static final String FIELDS = "datetime_beginning_utc,datetime_beginning_ept,pnode_id,pnode_name,"
			+ "total_lmp_da,system_energy_price_da,congestion_price_da,marginal_loss_price_da";

	/** Two operating days have at most 49 hours (fall DST day has 25). */
	protected static final int ROW_COUNT = 100;
	protected static final int MAX_PAGES = 10;
	protected static final Duration RETRY_BASE_DELAY = Duration.ofSeconds(30);
	protected static final int INTERNAL_ERROR = -1;

	private static final DateTimeFormatter EPT_QUERY_DATE = DateTimeFormatter.ofPattern("M/d/yyyy");

	private final Logger log = LoggerFactory.getLogger(TimeOfUseTariffPjmImpl.class);
	private final AtomicReference<TimeOfUsePrices> prices = new AtomicReference<>(TimeOfUsePrices.EMPTY_PRICES);
	private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

	@Reference
	private Meta meta;

	@Reference
	private ComponentManager componentManager;

	@Reference
	private BridgeHttpFactory httpBridgeFactory;
	private BridgeHttp httpBridge;
	private HttpBridgeTimeService timeService;

	private Config config = null;

	public TimeOfUseTariffPjmImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				TimeOfUseTariffPjm.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
		this._setPnodeId(config.pnodeId());
		this._setConsecutiveFailures(0);

		if (!config.enabled()) {
			return;
		}

		if (config.useFixtureFile() != null && !config.useFixtureFile().isBlank()) {
			this.loadFixtureFile(config.useFixtureFile());
			return;
		}

		if (config.apiKey() == null || config.apiKey().isBlank()) {
			this.logError(this.log, "Please configure a PJM Data Miner 2 API key");
			return;
		}

		this.httpBridge = this.httpBridgeFactory.get();
		this.timeService = this.httpBridge.createService(HttpBridgeTimeServiceDefinition.INSTANCE);
		this.timeService.subscribeTime(//
				new PjmDelayTimeProvider(config.pollingIntervalMinutes(), config.maxRetries(),
						this.consecutiveFailures::get), //
				this::createEndpoint, //
				this::handleEndpointResponse, //
				this::handleEndpointError);
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
		if (this.httpBridge != null) {
			this.httpBridgeFactory.unget(this.httpBridge);
			this.httpBridge = null;
		}
	}

	private void loadFixtureFile(String path) {
		try {
			var json = Files.readString(Paths.get(path), StandardCharsets.UTF_8);
			this.prices.set(PjmParser.parsePrices(json, this.config.pnodeId(), this.config.zone(),
					this.config.ancillaryCostsPerMwh()));
			this.setSuccess(200);
		} catch (IOException | OpenemsNamedException | RuntimeException e) {
			this.logError(this.log, "Unable to read PJM fixture file [" + path + "]: " + e.getMessage());
			this.setFailure(INTERNAL_ERROR);
		}
	}

	/**
	 * Creates the {@link Endpoint} for today and tomorrow (operating days in EPT).
	 * 
	 * @return the {@link Endpoint}
	 */
	protected Endpoint createEndpoint() {
		var today = LocalDate.ofInstant(Instant.now(this.componentManager.getClock()), EPT);
		var tomorrow = today.plusDays(1);
		var range = today.format(EPT_QUERY_DATE) + " 00:00 to " + tomorrow.format(EPT_QUERY_DATE) + " 23:59";
		var url = UrlBuilder.parse(API_URL) //
				.withQueryParam("rowCount", String.valueOf(ROW_COUNT)) //
				.withQueryParam("startRow", "1") //
				.withQueryParam("datetime_beginning_ept", range) //
				.withQueryParam("pnode_id", String.valueOf(this.config.pnodeId())) //
				.withQueryParam("fields", FIELDS) //
				.withQueryParam("format", "json");
		return this.createEndpoint(url.toEncodedString());
	}

	private Endpoint createEndpoint(String url) {
		var timeout = this.config.httpTimeoutSeconds() * 1000;
		return new Endpoint(url, //
				HttpMethod.GET, //
				timeout, //
				timeout, //
				null, //
				Map.of(API_KEY_HEADER, this.config.apiKey()));
	}

	/**
	 * Handles a response. Never throws, because an exception would stop the
	 * scheduling of the {@link HttpBridgeTimeService}; failures are reported via
	 * the channels instead and the last successful prices are kept.
	 * 
	 * @param response the {@link HttpResponse}
	 */
	private void handleEndpointResponse(HttpResponse<String> response) {
		var statusCode = response.status().code();
		if (response.status().isError()) {
			this.setFailure(statusCode);
			this.logWarn(this.log, "Unable to update PJM prices (HTTP " + statusCode + ", "
					+ this.consecutiveFailures.get() + " consecutive failures)");
			return;
		}
		try {
			var page = PjmParser.parsePage(response.data());
			var items = new ArrayList<PjmParser.Item>(page.items());
			var nextLink = page.nextLink();
			var pages = 1;
			while (nextLink != null && pages < MAX_PAGES) {
				var next = this.fetchPage(nextLink);
				items.addAll(next.items());
				nextLink = next.nextLink();
				pages++;
			}
			this.prices.set(PjmParser.toPrices(items, this.config.pnodeId(), this.config.zone(),
					this.config.ancillaryCostsPerMwh()));
			this.setSuccess(statusCode);
		} catch (OpenemsNamedException | RuntimeException e) {
			this.setFailure(INTERNAL_ERROR);
			this.logWarn(this.log, "Unable to parse PJM prices (" + this.consecutiveFailures.get()
					+ " consecutive failures): " + e.getMessage());
		}
	}

	private PjmParser.Page fetchPage(String url) throws OpenemsNamedException {
		try {
			var response = this.httpBridge.request(this.createEndpoint(url)) //
					.get(this.config.httpTimeoutSeconds() * 2L, TimeUnit.SECONDS);
			return PjmParser.parsePage(response.data());
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			throw new OpenemsException("Unable to fetch PJM page [" + url + "]: " + e.getMessage());
		}
	}

	private void handleEndpointError(HttpError error) {
		var statusCode = error instanceof HttpError.ResponseError re //
				? re.status.code() //
				: INTERNAL_ERROR;
		this.setFailure(statusCode);
		this.logWarn(this.log, "Unable to update PJM prices (HTTP " + statusCode + ", "
				+ this.consecutiveFailures.get() + " consecutive failures): " + error.getMessage());
	}

	private void setSuccess(int statusCode) {
		this.consecutiveFailures.set(0);
		this._setHttpStatusCode(statusCode);
		this._setConsecutiveFailures(0);
		this._setLastSuccessfulUpdate(Instant.now(this.componentManager.getClock()).getEpochSecond());
	}

	private void setFailure(int statusCode) {
		this._setHttpStatusCode(statusCode);
		this._setConsecutiveFailures(this.consecutiveFailures.incrementAndGet());
	}

	@Override
	public TimeOfUsePrices getPrices() {
		return TimeOfUsePrices.from(Instant.now(this.componentManager.getClock()), this.prices.get());
	}

	@Override
	public String debugLog() {
		return generateDebugLog(this, this.meta.getCurrency());
	}

	/**
	 * Polls at the configured interval; after a failure retries with exponential
	 * backoff (30s, 60s, 120s, ...) up to {@code maxRetries} times before falling
	 * back to the polling interval.
	 */
	protected static class PjmDelayTimeProvider implements DelayTimeProvider<HttpResponse<String>> {

		private final Duration pollingInterval;
		private final int maxRetries;
		private final IntSupplier consecutiveFailures;

		public PjmDelayTimeProvider(int pollingIntervalMinutes, int maxRetries,
				IntSupplier consecutiveFailures) {
			this.pollingInterval = Duration.ofMinutes(pollingIntervalMinutes);
			this.maxRetries = maxRetries;
			this.consecutiveFailures = consecutiveFailures;
		}

		@Override
		public Delay onFirstRunDelay() {
			return Delay.immediate();
		}

		@Override
		public Delay onErrorRunDelay(HttpError error) {
			return this.nextDelay();
		}

		@Override
		public Delay onSuccessRunDelay(HttpResponse<String> result) {
			return this.nextDelay();
		}

		private Delay nextDelay() {
			var failures = this.consecutiveFailures.getAsInt();
			if (failures == 0 || failures > this.maxRetries) {
				return Delay.of(this.pollingInterval);
			}
			var backoff = RETRY_BASE_DELAY.multipliedBy(1L << Math.max(0, Math.min(failures - 1, 20)));
			return Delay.of(backoff.compareTo(this.pollingInterval) < 0 ? backoff : this.pollingInterval);
		}
	}
}
