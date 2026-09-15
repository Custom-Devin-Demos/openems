package io.openems.backend.openadr.vtn;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;

/**
 * Thread-safe in-memory OpenADR VTN state.
 */
public final class VtnRegistry implements OpenAdrVtn {

	private final Clock clock;
	private final ConcurrentMap<String, Ven> vens = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, DrEvent> events = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, VenReport> reports = new ConcurrentHashMap<>();

	/**
	 * Creates a registry using the system clock.
	 */
	public VtnRegistry() {
		this(Clock.systemUTC());
	}

	/**
	 * Creates a registry.
	 *
	 * @param clock clock used for generated timestamps
	 */
	public VtnRegistry(Clock clock) {
		this.clock = clock;
	}

	/**
	 * Registers or reuses a VEN.
	 *
	 * @param venName VEN name
	 * @param requestedVenId requested ID
	 * @return registered VEN
	 */
	public Ven register(String venName, String requestedVenId) {
		if (requestedVenId != null) {
			var existing = this.vens.get(requestedVenId);
			if (existing != null) {
				return existing;
			}
		}
		var id = requestedVenId == null || requestedVenId.isBlank() ? "ven-"
				+ UUID.randomUUID().toString().replace("-", "").substring(0, 8) : requestedVenId;
		var ven = new Ven(id, "reg-" + UUID.randomUUID(), venName == null ? "" : venName, this.clock.instant(),
				new AtomicReference<>());
		this.vens.put(id, ven);
		return ven;
	}

	/**
	 * Finds a VEN.
	 *
	 * @param venId VEN ID
	 * @return VEN if known
	 */
	public Optional<Ven> getVen(String venId) {
		return venId == null ? Optional.empty() : Optional.ofNullable(this.vens.get(venId));
	}

	/**
	 * Cancels a registration.
	 *
	 * @param registrationId registration ID
	 * @return whether a registration was removed
	 */
	public boolean cancelRegistration(String registrationId) {
		return this.vens.entrySet().removeIf(entry -> entry.getValue().registrationId().equals(registrationId));
	}

	/**
	 * Records a poll.
	 *
	 * @param venId VEN ID
	 */
	public void recordPoll(String venId) {
		if (venId == null) {
			return;
		}
		var ven = this.vens.get(venId);
		if (ven != null) {
			ven.lastPoll().set(this.clock.instant());
		}
	}

	/**
	 * Gets events available to a VEN.
	 *
	 * @param venId VEN ID
	 * @param now current time
	 * @return events
	 */
	public List<DrEvent> getEventsForVen(String venId, Instant now) {
		var normalizedVenId = venId == null ? "" : venId;
		var result = new ArrayList<DrEvent>();
		for (var event : this.events.values()) {
			if (event.venId() != null && !event.venId().equals(normalizedVenId)) {
				continue;
			}
			if (event.cancelled()) {
				if (!event.cancelDeliveredTo().contains(normalizedVenId)) {
					event.cancelDeliveredTo().add(normalizedVenId);
					result.add(event);
				}
			} else if (switch (event.getStatus(now)) {
				case FAR, NEAR, ACTIVE -> true;
				default -> false;
			}) {
				result.add(event);
			}
		}
		return result;
	}

	/**
	 * Creates and stores an event.
	 *
	 * @param request event request
	 * @return event
	 * @throws OpenemsNamedException on invalid input
	 */
	@Override
	public DrEvent createEvent(CreateEventRequest request) throws OpenemsNamedException {
		var event = new DrEvent(request.venId(), request.signalType(), request.level() == null ? 0 : request.level(),
				request.price() == null ? 0 : request.price(), request.start(),
				Duration.ofMinutes(request.durationMinutes()), request.marketContext(), request.priority(),
				this.clock.instant());
		this.events.put(event.eventId(), event);
		return event;
	}

	/**
	 * Cancels an event.
	 *
	 * @param eventId event ID
	 * @return whether event existed
	 */
	@Override
	public boolean cancelEvent(String eventId) {
		var event = this.events.get(eventId);
		if (event == null) {
			return false;
		}
		event.cancel();
		return true;
	}

	/**
	 * Records an opt response.
	 *
	 * @param eventId event ID
	 * @param venId VEN ID
	 * @param opt option
	 */
	public void recordOptResponse(String eventId, String venId, OptType opt) {
		var event = this.events.get(eventId);
		if (event != null) {
			event.recordOptResponse(venId, opt);
		}
	}

	/**
	 * Records latest report values.
	 *
	 * @param venId VEN ID
	 * @param reportRequestId request ID
	 * @param reportSpecifierId specifier ID
	 * @param latestValues values
	 * @param receivedAt received time
	 */
	public void recordReport(String venId, String reportRequestId, String reportSpecifierId,
			Map<String, Double> latestValues, Instant receivedAt) {
		this.reports.put(venId, new VenReport(venId, reportRequestId, reportSpecifierId, Map.copyOf(latestValues),
				receivedAt));
	}

	/**
	 * Gets all VENs.
	 *
	 * @return VENs
	 */
	@Override
	public List<Ven> getVens() {
		return List.copyOf(this.vens.values());
	}

	/**
	 * Gets all events.
	 *
	 * @return events
	 */
	@Override
	public List<DrEvent> getEvents() {
		return List.copyOf(this.events.values());
	}

	/**
	 * Gets all reports.
	 *
	 * @return reports
	 */
	@Override
	public List<VenReport> getReports() {
		return List.copyOf(this.reports.values());
	}

	void clear() {
		this.vens.clear();
		this.events.clear();
		this.reports.clear();
	}
}
