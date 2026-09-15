package io.openems.edge.timeofusetariff.comed;

import static io.openems.edge.timeofusetariff.api.utils.TimeOfUseTariffUtils.generateDebugLog;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSortedMap;

import io.openems.common.bridge.http.api.BridgeHttp;
import io.openems.common.bridge.http.api.BridgeHttpFactory;
import io.openems.common.bridge.http.api.HttpError;
import io.openems.common.bridge.http.api.HttpResponse;
import io.openems.common.bridge.http.time.DefaultDelayTimeProvider;
import io.openems.common.bridge.http.time.DelayTimeProvider.Delay;
import io.openems.common.bridge.http.time.HttpBridgeTimeService;
import io.openems.common.bridge.http.time.HttpBridgeTimeServiceDefinition;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.meta.Meta;
import io.openems.edge.timeofusetariff.api.TimeOfUsePrices;
import io.openems.edge.timeofusetariff.api.TimeOfUseTariff;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "TimeOfUseTariff.ComEd", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class TimeOfUseTariffComEdImpl extends AbstractOpenemsComponent
		implements TimeOfUseTariff, OpenemsComponent, TimeOfUseTariffComEd {

	/**
	 * One HTTP request of a poll cycle. A poll cycle fetches all steps back-to-back
	 * and afterwards waits for the polling interval.
	 */
	protected enum Step {
		DAY_AHEAD_TODAY(ComEdApi.DAY_AHEAD_TODAY_URL), //
		DAY_AHEAD_TOMORROW(ComEdApi.DAY_AHEAD_TOMORROW_URL), //
		FIVE_MINUTE(ComEdApi.FIVE_MINUTE_FEED_URL), //
		CURRENT_HOUR_AVERAGE(ComEdApi.CURRENT_HOUR_AVERAGE_URL);

		public final String url;

		private Step(String url) {
			this.url = url;
		}
	}

	private final Logger log = LoggerFactory.getLogger(TimeOfUseTariffComEdImpl.class);

	private final AtomicReference<TimeOfUsePrices> prices = new AtomicReference<>(TimeOfUsePrices.EMPTY_PRICES);
	private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

	private ImmutableSortedMap<Instant, Double> dayAheadToday = ImmutableSortedMap.of();
	private ImmutableSortedMap<Instant, Double> dayAheadTomorrow = ImmutableSortedMap.of();
	private ImmutableSortedMap<Instant, Double> fiveMinute = ImmutableSortedMap.of();
	private ImmutableSortedMap<Instant, Double> currentHourAverage = ImmutableSortedMap.of();

	@Reference
	private Meta meta;

	@Reference
	private ComponentManager componentManager;

	@Reference
	private BridgeHttpFactory httpBridgeFactory;
	private BridgeHttp httpBridge;
	private HttpBridgeTimeService timeService;

	private Config config = null;
	private List<Step> steps = List.of();
	private int stepIndex = 0;

	public TimeOfUseTariffComEdImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				TimeOfUseTariffComEd.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
		this._setFeed(config.feed().name());
		this._setConsecutiveFailures(0);

		if (!config.enabled()) {
			return;
		}

		this.steps = stepsFor(config.feed());
		this.stepIndex = 0;
		this.httpBridge = this.httpBridgeFactory.get();
		this.timeService = this.httpBridge.createService(HttpBridgeTimeServiceDefinition.INSTANCE);

		final var delayProvider = new DefaultDelayTimeProvider<HttpResponse<String>>(//
				Delay::immediate, //
				this::onErrorDelay, //
				this::onSuccessDelay);
		this.timeService.subscribeTime(delayProvider, this::currentEndpoint, this::handleResponse,
				this::handleError);
	}

	@Override
	@Deactivate
	protected void deactivate() {
		if (this.timeService != null) {
			try {
				this.timeService.close();
			} catch (Exception e) {
				this.log.warn("Unable to close HTTP time service: " + e.getMessage());
			}
			this.timeService = null;
		}
		if (this.httpBridge != null) {
			this.httpBridgeFactory.unget(this.httpBridge);
			this.httpBridge = null;
		}
		super.deactivate();
	}

	/**
	 * The requests executed in one poll cycle for the given {@link Feed}.
	 *
	 * @param feed the feed
	 * @return the steps
	 */
	protected static List<Step> stepsFor(Feed feed) {
		return switch (feed) {
		case DAY_AHEAD -> List.of(Step.DAY_AHEAD_TODAY, Step.DAY_AHEAD_TOMORROW);
		case FIVE_MINUTE -> List.of(Step.DAY_AHEAD_TODAY, Step.DAY_AHEAD_TOMORROW, Step.FIVE_MINUTE);
		case CURRENT_HOUR_AVERAGE -> List.of(Step.DAY_AHEAD_TODAY, Step.DAY_AHEAD_TOMORROW,
				Step.CURRENT_HOUR_AVERAGE);
		};
	}

	private Step currentStep() {
		return this.steps.get(this.stepIndex);
	}

	private BridgeHttp.Endpoint currentEndpoint() {
		final var timeoutMs = this.config.httpTimeoutSeconds() * 1000;
		return BridgeHttp.create(this.currentStep().url) //
				.setConnectTimeout(timeoutMs) //
				.setReadTimeout(timeoutMs) //
				.build();
	}

	private void handleResponse(HttpResponse<String> response) throws Exception {
		final var step = this.currentStep();
		switch (step) {
		case DAY_AHEAD_TODAY -> this.dayAheadToday = ComEdApi.parseDayAhead(response.data());
		case DAY_AHEAD_TOMORROW -> this.dayAheadTomorrow = ComEdApi.parseDayAhead(response.data());
		case FIVE_MINUTE -> this.fiveMinute = ComEdApi.parseFeed(response.data());
		case CURRENT_HOUR_AVERAGE -> this.currentHourAverage = ComEdApi.parseFeed(response.data());
		}
		this._setHttpStatusCode(response.status().code());
		this.stepIndex++;
		if (this.stepIndex >= this.steps.size()) {
			this.stepIndex = 0;
			this.rebuildPrices();
			this.consecutiveFailures.set(0);
			this._setConsecutiveFailures(0);
			this._setLastSuccessfulUpdate(Instant.now(this.componentManager.getClock()).getEpochSecond());
		}
	}

	private void handleError(HttpError error) {
		final var failures = this.consecutiveFailures.incrementAndGet();
		this._setConsecutiveFailures(failures);
		if (error instanceof HttpError.ResponseError responseError) {
			this._setHttpStatusCode(responseError.status.code());
		} else {
			this._setHttpStatusCode(null);
		}
		this.stepIndex = 0;
		this.logWarn(this.log, "ComEd poll failed (" + failures + "x): " + error.getMessage());
	}

	private Delay onSuccessDelay(HttpResponse<String> response) {
		if (this.stepIndex != 0) {
			// still within a poll cycle
			return Delay.immediate();
		}
		return Delay.of(Duration.ofMinutes(this.config.pollingIntervalMinutes()));
	}

	private Delay onErrorDelay(HttpError error) {
		return Delay.of(ComEdApi.backoff(this.consecutiveFailures.get(), this.config.pollingIntervalMinutes(),
				this.config.maxRetries()));
	}

	private void rebuildPrices() {
		final var dayAhead = new TreeMap<Instant, Double>(this.dayAheadToday);
		dayAhead.putAll(this.dayAheadTomorrow);
		this.prices.set(ComEdApi.combine(dayAhead, this.fiveMinute, this.currentHourAverage,
				this.config.ancillaryCostsPerMwh()));
	}

	@Override
	public TimeOfUsePrices getPrices() {
		return TimeOfUsePrices.from(Instant.now(this.componentManager.getClock()), this.prices.get());
	}

	@Override
	public String debugLog() {
		return generateDebugLog(this, this.meta.getCurrency());
	}
}
