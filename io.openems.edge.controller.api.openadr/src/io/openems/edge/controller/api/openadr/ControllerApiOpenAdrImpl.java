package io.openems.edge.controller.api.openadr;

import static io.openems.common.utils.ThreadPoolUtils.shutdownAndAwaitTermination;
import static java.util.Comparator.comparingInt;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.jsonrpc.base.GenericJsonrpcResponseSuccess;
import io.openems.common.types.OptionsEnum;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.jsonapi.ComponentJsonApi;
import io.openems.edge.common.jsonapi.JsonApiBuilder;
import io.openems.edge.common.sum.Sum;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.controller.api.openadr.jsonrpc.GetActiveEventsRequest;
import io.openems.edge.controller.api.openadr.jsonrpc.GetActiveEventsResponse;
import io.openems.edge.controller.api.openadr.jsonrpc.SetOptStateRequest;
import io.openems.edge.controller.api.openadr.oadr.EventPhase;
import io.openems.edge.controller.api.openadr.oadr.OadrClient;
import io.openems.edge.controller.api.openadr.oadr.OadrClient.Response;
import io.openems.edge.controller.api.openadr.oadr.OadrClient.TlsConfig;
import io.openems.edge.controller.api.openadr.oadr.OadrClient.TrustMode;
import io.openems.edge.controller.api.openadr.oadr.OadrEvent;
import io.openems.edge.controller.api.openadr.oadr.OadrMessages;
import io.openems.edge.controller.api.openadr.oadr.OadrMessages.EventResponse;
import io.openems.edge.controller.api.openadr.oadr.OadrMessages.ReportPoint;
import io.openems.edge.controller.api.openadr.oadr.OadrMessages.ReportRequest;
import io.openems.edge.controller.api.openadr.oadr.OadrXml;
import io.openems.edge.controller.api.openadr.oadr.PartyRegistration;
import io.openems.edge.controller.api.openadr.oadr.SignalType;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.evcs.api.ManagedEvcs;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.Api.OpenADR", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class ControllerApiOpenAdrImpl extends AbstractOpenemsComponent
		implements ControllerApiOpenAdr, Controller, ComponentJsonApi, OpenemsComponent {

	public static final String RID_ESS_POWER = "ess_active_power";
	public static final String RID_EVCS_POWER = "evcs_charge_power";
	public static final String RID_GRID_POWER = "grid_active_power";
	public static final String REPORT_SPECIFIER_ID = "openems_telemetry_usage";

	protected static final Duration MAX_BACKOFF = Duration.ofMinutes(5);
	private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);
	private static final String PROPERTY_MODE = "mode";
	private static final String PROPERTY_MANUAL_STATE = "manualState";
	private static final String SG_READY_MANUAL = "MANUAL";
	private static final String SG_READY_LOCK = "LOCK";
	private static final String EVSE_ZERO = "ZERO";

	private final Logger log = LoggerFactory.getLogger(ControllerApiOpenAdrImpl.class);

	private final Map<String, OadrEvent> events = new ConcurrentHashMap<>();
	private final Map<String, OptState> optStates = new ConcurrentHashMap<>();
	private final Map<String, Map<String, Object>> restoreProperties = new HashMap<>();
	private final AtomicBoolean communicating = new AtomicBoolean(false);

	@Reference
	private ComponentManager componentManager;

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private Sum sum;

	private Config config;
	private OadrClient client;
	private ScheduledExecutorService executor;

	private volatile PartyRegistration registration;
	private volatile Duration pollInterval;
	private volatile Instant nextPoll = Instant.MIN;
	private volatile Instant nextReport = Instant.MIN;
	private volatile int consecutiveFailures = 0;
	private volatile ReportRequest reportRequest;
	private volatile String pollService = OadrClient.SERVICE_POLL;
	private boolean curtailmentApplied = false;

	public ControllerApiOpenAdrImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				ControllerApiOpenAdr.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
		this.pollInterval = Duration.ofSeconds(Math.max(1, config.pollIntervalSeconds()));
		this._setRegistrationState(RegistrationState.UNREGISTERED);
		this.channel(ControllerApiOpenAdr.ChannelId.ACTIVE_EVENT_SIGNAL_LEVEL).setNextValue(-1);
		this.channel(ControllerApiOpenAdr.ChannelId.EVENT_COUNT).setNextValue(0);
		this.channel(ControllerApiOpenAdr.ChannelId.CURTAILMENT_ACTIVE).setNextValue(false);
		this.getSetOptStateChannel().onSetNextWrite(value -> {
			if (value != null) {
				this.setOptState(null, OptionsEnum.getOptionOrUndefined(OptState.class, value));
			}
		});

		if (!config.enabled()) {
			return;
		}
		this.client = new OadrClient(config.vtnUrl(), new TlsConfig(TrustMode.valueOf(config.tls().name()),
				config.trustStorePath(), config.trustStorePassword(), config.clientCertPath(),
				config.clientKeyPassword()), HTTP_TIMEOUT);
		if (config.venId() != null && !config.venId().isBlank()) {
			this.channel(ControllerApiOpenAdr.ChannelId.VEN_ID).setNextValue(config.venId());
		}
		this.executor = Executors.newSingleThreadScheduledExecutor();
		this.executor.scheduleWithFixedDelay(this::communicateIfDue, 0, 1, TimeUnit.SECONDS);
	}

	@Override
	@Deactivate
	protected void deactivate() {
		if (this.executor != null) {
			shutdownAndAwaitTermination(this.executor, 5);
		}
		this.restoreConfiguredComponents();
		if (this.client != null && this.registration != null && this.registration.isRegistered()) {
			try {
				this.post(OadrClient.SERVICE_REGISTER_PARTY, OadrMessages.cancelPartyRegistration(newRequestId(),
						this.registration.registrationId(), this.registration.venId()));
			} catch (OpenemsException e) {
				this.logWarn(this.log, "Unable to cancel party registration: " + e.getMessage());
			}
		}
		super.deactivate();
	}

	/*
	 * Communication with the VTN (runs on the executor thread).
	 */

	private void communicateIfDue() {
		var now = Instant.now(this.componentManager.getClock());
		if (now.isBefore(this.nextPoll)) {
			return;
		}
		this.communicate(now);
	}

	/**
	 * Performs one communication cycle: register if required, poll, report.
	 * 
	 * <p>
	 * Package-private for tests.
	 * 
	 * @param now the current time
	 */
	void communicate(Instant now) {
		if (!this.communicating.compareAndSet(false, true)) {
			return;
		}
		try {
			if (this.registration == null || !this.registration.isRegistered()) {
				this.register();
			}
			if (this.registration != null && this.registration.isRegistered()) {
				this.poll();
				if (!now.isBefore(this.nextReport)) {
					this.sendReport(now);
					this.nextReport = now.plusSeconds(Math.max(1, this.config.reportIntervalSeconds()));
				}
			}
			this.consecutiveFailures = 0;
			this._setCommunicationFailed(false);
			this.nextPoll = now.plus(this.pollInterval);

		} catch (OpenemsException e) {
			this.consecutiveFailures++;
			this._setCommunicationFailed(true);
			var backoff = backoff(this.pollInterval, this.consecutiveFailures);
			this.nextPoll = now.plus(backoff);
			this.logWarn(this.log, "VTN communication failed (" + this.consecutiveFailures + "x): " + e.getMessage()
					+ "; next attempt in " + backoff.toSeconds() + "s");

		} finally {
			this.channel(ControllerApiOpenAdr.ChannelId.LAST_POLL).setNextValue(now.getEpochSecond());
			this.communicating.set(false);
		}
	}

	/**
	 * Calculates the exponential backoff delay.
	 * 
	 * @param base     the base poll interval
	 * @param failures number of consecutive failures (>= 1)
	 * @return the delay
	 */
	protected static Duration backoff(Duration base, int failures) {
		var multiplier = 1L << Math.min(Math.max(failures - 1, 0), 10);
		var result = base.multipliedBy(multiplier);
		return result.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : result;
	}

	private void register() throws OpenemsException {
		this._setRegistrationState(RegistrationState.REGISTERING);
		try {
			var venId = this.config.venId();
			var registrationId = this.config.registrationId();
			if (registrationId != null && !registrationId.isBlank()) {
				var query = this.postAndParse(OadrClient.SERVICE_REGISTER_PARTY,
						OadrMessages.queryRegistration(newRequestId()));
				var response = OadrMessages.parseResponse(query);
				if (!response.isOk()) {
					throw new OpenemsException("oadrQueryRegistration rejected: " + response.responseCode() + " "
							+ response.responseDescription());
				}
			}
			var created = this.postAndParse(OadrClient.SERVICE_REGISTER_PARTY,
					OadrMessages.createPartyRegistration(newRequestId(), this.config.venName(), venId,
							registrationId));
			var registration = OadrMessages.parseCreatedPartyRegistration(created);
			if (!registration.isRegistered()) {
				throw new OpenemsException("oadrCreatePartyRegistration rejected: " + registration.responseCode());
			}
			this.registration = registration;
			if (registration.requestedPollFrequency() != null && !registration.requestedPollFrequency().isZero()) {
				this.pollInterval = registration.requestedPollFrequency();
			}
			this.channel(ControllerApiOpenAdr.ChannelId.VEN_ID).setNextValue(registration.venId());
			this._setRegistrationState(RegistrationState.REGISTERED);
			this.registerReport();

		} catch (OpenemsException e) {
			this._setRegistrationState(RegistrationState.FAILED);
			throw e;
		}
	}

	private void registerReport() throws OpenemsException {
		var points = List.of(//
				new ReportPoint(RID_ESS_POWER, "ESS active power [W]"), //
				new ReportPoint(RID_EVCS_POWER, "EVCS charge power [W]"), //
				new ReportPoint(RID_GRID_POWER, "Grid active power [W]"));
		var root = this.postAndParse(OadrClient.SERVICE_REPORT, OadrMessages.registerReport(newRequestId(),
				this.registration.venId(), REPORT_SPECIFIER_ID, Instant.now(this.componentManager.getClock()),
				points, Duration.ofSeconds(Math.max(1, this.config.reportIntervalSeconds()))));
		this.handleReportRequests(root);
	}

	private void handleReportRequests(Element root) throws OpenemsException {
		var requests = OadrMessages.parseCreateReport(root);
		if (requests.isEmpty()) {
			return;
		}
		this.reportRequest = requests.get(0);
		if (this.reportRequest.reportBackDuration() != null && !this.reportRequest.reportBackDuration().isZero()) {
			this.nextReport = Instant.now(this.componentManager.getClock());
		}
		if (OadrMessages.messageName(root).equals("oadrCreateReport")) {
			this.post(OadrClient.SERVICE_REPORT, OadrMessages.createdReport(newRequestId(),
					this.registration.venId(), requests.stream().map(ReportRequest::reportRequestId).toList()));
		}
	}

	private void poll() throws OpenemsException {
		var response = this.client.post(this.pollService, OadrMessages.poll(this.registration.venId()));
		if (response.statusCode() == 404 && this.pollService.equals(OadrClient.SERVICE_POLL)) {
			this.pollService = OadrClient.SERVICE_EVENT;
			response = this.client.post(this.pollService, OadrMessages.poll(this.registration.venId()));
		}
		this.channel(ControllerApiOpenAdr.ChannelId.HTTP_STATUS_CODE).setNextValue(response.statusCode());
		if (!response.isOk()) {
			throw new OpenemsException("oadrPoll failed with HTTP " + response.statusCode());
		}
		if (response.body() == null || response.body().isBlank()) {
			return;
		}
		var root = OadrXml.parse(response.body());
		switch (OadrMessages.messageName(root)) {
		case "oadrDistributeEvent" -> this.handleDistributeEvent(root);
		case "oadrCreateReport" -> this.handleReportRequests(root);
		case "oadrCancelPartyRegistration" -> {
			this.registration = null;
			this._setRegistrationState(RegistrationState.UNREGISTERED);
		}
		case "oadrResponse" -> {
			var r = OadrMessages.parseResponse(root);
			if (!r.isOk()) {
				throw new OpenemsException("VTN responded " + r.responseCode() + " " + r.responseDescription());
			}
		}
		default -> {
			// oadrCancelReport, oadrRegisterReport, ... are acknowledged only
			var requestId = OadrMessages.requestId(root).orElse(newRequestId());
			this.post(OadrClient.SERVICE_EVENT,
					OadrMessages.response(requestId, this.registration.venId(), 200, "OK"));
		}
		}
	}

	/**
	 * Handles an oadrDistributeEvent message: updates the local event store and
	 * responds with oadrCreatedEvent.
	 * 
	 * <p>
	 * Package-private for tests.
	 * 
	 * @param root the parsed message root
	 * @throws OpenemsException on error
	 */
	void handleDistributeEvent(Element root) throws OpenemsException {
		var distribute = OadrMessages.parseDistributeEvent(root);
		var responses = new ArrayList<EventResponse>();
		for (var event : distribute.events()) {
			if (!this.matchesMarketContext(event)
					|| !event.targets(this.registration != null ? this.registration.venId() : null)) {
				continue;
			}
			var previous = this.events.get(event.eventId());
			if (previous != null && previous.modificationNumber() > event.modificationNumber()) {
				continue;
			}
			this.events.put(event.eventId(), event);
			if (event.isCancelled()) {
				this.optStates.remove(event.eventId());
			} else {
				this.optStates.computeIfAbsent(event.eventId(),
						id -> this.config.autoOptIn() ? OptState.OPT_IN : OptState.OPT_OUT);
			}
			if (event.responseRequired() == null || !event.responseRequired().equalsIgnoreCase("never")) {
				var optState = this.optStates.getOrDefault(event.eventId(), OptState.OPT_IN);
				responses.add(new EventResponse(event.eventId(), event.modificationNumber(),
						distribute.requestId(), optState.getOadrOptType()));
			}
		}
		this.updateEventCount();
		if (!responses.isEmpty() && this.registration != null) {
			this.post(OadrClient.SERVICE_EVENT,
					OadrMessages.createdEvent(newRequestId(), this.registration.venId(), responses));
		}
	}

	private boolean matchesMarketContext(OadrEvent event) {
		var configured = this.config.marketContext();
		if (configured == null || configured.isBlank() || event.marketContext() == null
				|| event.marketContext().isBlank()) {
			return true;
		}
		return configured.equals(event.marketContext());
	}

	private void sendReport(Instant now) throws OpenemsException {
		if (this.reportRequest == null) {
			return;
		}
		var values = new HashMap<String, Double>();
		this.optionalComponent(this.config.ess_id(), ManagedSymmetricEss.class)
				.ifPresent(ess -> values.put(RID_ESS_POWER, ess.getActivePower().orElse(0).doubleValue()));
		var evcsPower = 0.0;
		var anyEvcs = false;
		for (var id : this.config.evcs_ids()) {
			var evcs = this.optionalComponent(id, ManagedEvcs.class);
			if (evcs.isPresent()) {
				anyEvcs = true;
				evcsPower += evcs.get().getActivePower().orElse(0);
			}
		}
		if (anyEvcs) {
			values.put(RID_EVCS_POWER, evcsPower);
		}
		if (this.sum != null && this.sum.getGridActivePower().isDefined()) {
			values.put(RID_GRID_POWER, this.sum.getGridActivePower().get().doubleValue());
		}
		var interval = Duration.ofSeconds(Math.max(1, this.config.reportIntervalSeconds()));
		this.post(OadrClient.SERVICE_REPORT,
				OadrMessages.updateReport(newRequestId(), this.registration.venId(),
						this.reportRequest.reportRequestId(), REPORT_SPECIFIER_ID, now.minus(interval), interval,
						values));
	}

	private Element postAndParse(String service, String xml) throws OpenemsException {
		var response = this.post(service, xml);
		return OadrXml.parse(response.body());
	}

	private Response post(String service, String xml) throws OpenemsException {
		var response = this.client.post(service, xml);
		this.channel(ControllerApiOpenAdr.ChannelId.HTTP_STATUS_CODE).setNextValue(response.statusCode());
		if (!response.isOk()) {
			throw new OpenemsException("HTTP " + response.statusCode() + " from " + this.client.url(service));
		}
		return response;
	}

	private static String newRequestId() {
		return UUID.randomUUID().toString();
	}

	/*
	 * Controller cycle
	 */

	@Override
	public void run() throws OpenemsNamedException {
		var now = Instant.now(this.componentManager.getClock());
		this.events.values().removeIf(e -> e.recoveryEnd().plus(Duration.ofHours(1)).isBefore(now));
		this.updateEventCount();

		var active = this.activeEvent(now);
		var level = active.map(e -> this.effectiveLevel(e, now)).orElse(-1);
		var price = active.filter(e -> e.signalType() == SignalType.PRICE).map(e -> e.valueAt(now)).orElse(null);

		this.channel(ControllerApiOpenAdr.ChannelId.ACTIVE_EVENT_ID).setNextValue(active.map(OadrEvent::eventId)
				.orElse(null));
		this.channel(ControllerApiOpenAdr.ChannelId.ACTIVE_EVENT_SIGNAL_LEVEL).setNextValue(level);
		this.channel(ControllerApiOpenAdr.ChannelId.ACTIVE_EVENT_PRICE).setNextValue(price);
		this.channel(ControllerApiOpenAdr.ChannelId.ACTIVE_EVENT_START)
				.setNextValue(active.map(e -> e.start().getEpochSecond()).orElse(null));
		this.channel(ControllerApiOpenAdr.ChannelId.ACTIVE_EVENT_END)
				.setNextValue(active.map(e -> e.end().getEpochSecond()).orElse(null));
		this.channel(ControllerApiOpenAdr.ChannelId.OPT_STATE).setNextValue(
				active.map(e -> this.optStates.getOrDefault(e.eventId(), OptState.OPT_IN)).orElse(OptState.UNDEFINED));

		var curtail = level >= 1;
		this.channel(ControllerApiOpenAdr.ChannelId.CURTAILMENT_ACTIVE).setNextValue(curtail);
		if (curtail) {
			this.applyCurtailment(level);
			this.curtailmentApplied = true;
		} else if (this.curtailmentApplied) {
			this.restoreConfiguredComponents();
			this.curtailmentApplied = false;
		}
	}

	/**
	 * Selects the event to act on: opted-in, in a curtailing phase, highest
	 * priority first, then earliest start.
	 * 
	 * @param now the current time
	 * @return the event
	 */
	private Optional<OadrEvent> activeEvent(Instant now) {
		return this.events.values().stream() //
				.filter(e -> !e.isCancelled()) //
				.filter(e -> e.phaseAt(now).isCurtailing()) //
				.filter(e -> this.optStates.getOrDefault(e.eventId(), OptState.OPT_IN) == OptState.OPT_IN) //
				.min(Comparator.<OadrEvent>comparingInt(e -> -e.priority()) //
						.thenComparing(OadrEvent::start));
	}

	/**
	 * Maps the signal of an event to a curtailment level (0 = none .. 3).
	 * 
	 * <p>
	 * During ramp-up and recovery a moderate level (1) is used to pre-position
	 * devices without hard curtailment.
	 * 
	 * @param event the event
	 * @param now   the current time
	 * @return the level
	 */
	private int effectiveLevel(OadrEvent event, Instant now) {
		var phase = event.phaseAt(now);
		int level = switch (event.signalType()) {
		case SIMPLE -> (int) Math.round(event.valueAt(now));
		case PRICE -> event.valueAt(now) > this.config.priceSignalThresholdPerMwh() ? 2 : 0;
		case UNKNOWN -> 0;
		};
		level = Math.max(0, Math.min(3, level));
		if (level > 0 && (phase == EventPhase.RAMP_UP || phase == EventPhase.RECOVERY)) {
			return 1;
		}
		return level;
	}

	private void applyCurtailment(int level) {
		var anyMissing = false;

		var ess = this.optionalComponent(this.config.ess_id(), ManagedSymmetricEss.class);
		if (isConfigured(this.config.ess_id())) {
			if (ess.isPresent()) {
				try {
					var e = ess.get();
					if (level >= 3) {
						e.setActivePowerEqualsWithoutFilter(0);
					} else {
						e.setActivePowerGreaterOrEquals(0);
						if (level >= 2) {
							e.setActivePowerLessOrEquals(Math.max(0, this.config.curtailmentEssDischargeLimitW()));
						}
					}
				} catch (OpenemsNamedException ex) {
					this.logWarn(this.log, "Unable to constrain ESS [" + this.config.ess_id() + "]: " + ex.getMessage());
				}
			} else {
				anyMissing = true;
			}
		}

		for (var id : this.config.evcs_ids()) {
			var evcs = this.optionalComponent(id, ManagedEvcs.class);
			if (evcs.isPresent()) {
				try {
					evcs.get().setChargePowerLimit(Math.max(0, this.config.curtailmentEvcsChargeLimitW()));
				} catch (OpenemsNamedException ex) {
					this.logWarn(this.log, "Unable to limit EVCS [" + id + "]: " + ex.getMessage());
				}
			} else {
				anyMissing = true;
			}
		}

		for (var id : this.config.evse_ids()) {
			anyMissing |= !this.applyConfiguration(id, Map.of(PROPERTY_MODE, EVSE_ZERO));
		}
		for (var id : this.config.heatPump_ids()) {
			anyMissing |= !this.applyConfiguration(id,
					Map.of(PROPERTY_MODE, SG_READY_MANUAL, PROPERTY_MANUAL_STATE, SG_READY_LOCK));
		}

		this._setCommunicationFailed(anyMissing || this.consecutiveFailures > 0);
	}

	/**
	 * Applies configuration properties to a component and remembers the previous
	 * values for restoring after the event.
	 * 
	 * @param componentId the component id
	 * @param properties  the properties to set
	 * @return false if the component is missing
	 */
	private boolean applyConfiguration(String componentId, Map<String, String> properties) {
		var component = this.optionalComponent(componentId, OpenemsComponent.class);
		if (component.isEmpty()) {
			return false;
		}
		var context = component.get().getComponentContext();
		if (context == null) {
			return false;
		}
		var current = context.getProperties();
		var restore = this.restoreProperties.computeIfAbsent(componentId, id -> new HashMap<>());
		for (var entry : properties.entrySet()) {
			var currentValue = current.get(entry.getKey());
			if (Objects.equals(String.valueOf(currentValue), entry.getValue())) {
				continue;
			}
			restore.putIfAbsent(entry.getKey(), currentValue);
			OpenemsComponent.updateConfigurationProperty(this.cm, component.get().servicePid(), entry.getKey(),
					entry.getValue());
		}
		return true;
	}

	private void restoreConfiguredComponents() {
		for (var entry : this.restoreProperties.entrySet()) {
			var component = this.optionalComponent(entry.getKey(), OpenemsComponent.class);
			if (component.isEmpty()) {
				continue;
			}
			for (var property : entry.getValue().entrySet()) {
				if (property.getValue() == null) {
					continue;
				}
				OpenemsComponent.updateConfigurationProperty(this.cm, component.get().servicePid(),
						property.getKey(), property.getValue());
			}
		}
		this.restoreProperties.clear();
	}

	private static boolean isConfigured(String id) {
		return id != null && !id.isBlank();
	}

	private <T extends OpenemsComponent> Optional<T> optionalComponent(String id, Class<T> type) {
		if (!isConfigured(id)) {
			return Optional.empty();
		}
		try {
			var component = this.componentManager.getComponent(id);
			if (type.isInstance(component)) {
				return Optional.of(type.cast(component));
			}
			this.logWarn(this.log, "Component [" + id + "] is not a " + type.getSimpleName());
			return Optional.empty();
		} catch (OpenemsNamedException e) {
			this.logWarn(this.log, "Component [" + id + "] is missing: " + e.getMessage());
			return Optional.empty();
		}
	}

	private void updateEventCount() {
		this.channel(ControllerApiOpenAdr.ChannelId.EVENT_COUNT)
				.setNextValue((int) this.events.values().stream().filter(e -> !e.isCancelled()).count());
	}

	/*
	 * Opt-in / Opt-out
	 */

	/**
	 * Sets the opt state of an event.
	 * 
	 * @param eventId  the eventID; null for the currently active event
	 * @param optState the {@link OptState}
	 */
	public void setOptState(String eventId, OptState optState) {
		if (optState == null || optState == OptState.UNDEFINED) {
			return;
		}
		var now = Instant.now(this.componentManager.getClock());
		var target = eventId != null //
				? Optional.ofNullable(this.events.get(eventId)) //
				: this.events.values().stream() //
						.filter(e -> !e.isCancelled() && e.phaseAt(now) != EventPhase.AFTER) //
						.min(comparingInt(OadrEvent::priority).reversed().thenComparing(OadrEvent::start));
		if (target.isEmpty()) {
			this.logWarn(this.log, "No event found for setOptState [" + eventId + "]");
			return;
		}
		var event = target.get();
		var previous = this.optStates.put(event.eventId(), optState);
		if (previous == optState) {
			return;
		}
		this.channel(ControllerApiOpenAdr.ChannelId.OPT_STATE).setNextValue(optState);
		if (this.executor != null && this.registration != null && this.registration.isRegistered()) {
			this.executor.execute(() -> this.sendOpt(event, optState));
		}
	}

	private void sendOpt(OadrEvent event, OptState optState) {
		try {
			this.post(OadrClient.SERVICE_OPT, OadrMessages.createOpt(newRequestId(), this.registration.venId(),
					UUID.randomUUID().toString(), optState.getOadrOptType(), event,
					Instant.now(this.componentManager.getClock())));
		} catch (OpenemsException e) {
			this.logWarn(this.log, "Unable to send oadrCreateOpt: " + e.getMessage());
		}
	}

	/**
	 * Gets the known events.
	 * 
	 * @return the events
	 */
	public List<OadrEvent> getEvents() {
		return List.copyOf(this.events.values());
	}

	/**
	 * Gets the opt state of an event.
	 * 
	 * @param eventId the eventID
	 * @return the {@link OptState}
	 */
	public OptState getOptState(String eventId) {
		return this.optStates.getOrDefault(eventId, OptState.UNDEFINED);
	}

	/*
	 * JSON-RPC
	 */

	@Override
	public void buildJsonApiRoutes(JsonApiBuilder builder) {
		builder.handleRequest(GetActiveEventsRequest.METHOD, call -> {
			var now = Instant.now(this.componentManager.getClock());
			var list = this.events.values().stream() //
					.filter(e -> !e.isCancelled() && e.phaseAt(now) != EventPhase.AFTER) //
					.sorted(Comparator.comparing(OadrEvent::start)) //
					.map(e -> new GetActiveEventsResponse.Event(e.eventId(), e.signalType().name(),
							e.signalType() == SignalType.SIMPLE ? (int) Math.round(e.valueAt(now)) : -1,
							e.signalType() == SignalType.PRICE ? e.valueAt(now) : null, e.start().getEpochSecond(),
							e.end().getEpochSecond(), this.optStates.getOrDefault(e.eventId(), OptState.OPT_IN).name(),
							e.status(), e.phaseAt(now).name()))
					.toList();
			return new GetActiveEventsResponse(call.getRequest().getId(), list);
		});

		builder.handleRequest(SetOptStateRequest.METHOD, call -> {
			var request = SetOptStateRequest.from(call.getRequest());
			this.setOptState(request.getEventId(), request.getOptState());
			return new GenericJsonrpcResponseSuccess(call.getRequest().getId());
		});
	}

	@Override
	public String debugLog() {
		return "Registration:" + this.getRegistrationStateChannel().value().asOptionString() //
				+ "|Events:" + this.channel(ControllerApiOpenAdr.ChannelId.EVENT_COUNT).value().asString() //
				+ "|Active:" + this.channel(ControllerApiOpenAdr.ChannelId.ACTIVE_EVENT_ID).value().asString() //
				+ "|Level:" + this.channel(ControllerApiOpenAdr.ChannelId.ACTIVE_EVENT_SIGNAL_LEVEL).value().asString();
	}

}
