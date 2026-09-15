package io.openems.backend.openadr.vtn;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.google.gson.JsonObject;

/**
 * In-memory demand response event.
 */
public final class DrEvent {

	private final String eventId;
	private final String venId;
	private final SignalType signalType;
	private final int level;
	private final double price;
	private final Instant start;
	private final Duration duration;
	private final String marketContext;
	private final int priority;
	private final Instant createdAt;
	private final ConcurrentMap<String, OptType> optResponses = new ConcurrentHashMap<>();
	private final java.util.Set<String> cancelDeliveredTo = ConcurrentHashMap.newKeySet();
	private volatile int modificationNumber;
	private volatile boolean cancelled;

	/**
	 * Creates an event.
	 *
	 * @param venId target VEN or null
	 * @param signalType signal type
	 * @param level simple signal level
	 * @param price price signal
	 * @param start start time
	 * @param duration event duration
	 * @param marketContext market context
	 * @param priority event priority
	 * @param createdAt creation time
	 */
	public DrEvent(String venId, SignalType signalType, int level, double price, Instant start, Duration duration,
			String marketContext, int priority, Instant createdAt) {
		this.eventId = "evt-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		this.venId = venId;
		this.signalType = signalType;
		this.level = level;
		this.price = price;
		this.start = start;
		this.duration = duration;
		this.marketContext = marketContext == null || marketContext.isBlank() ? "http://openems.io/openadr"
				: marketContext;
		this.priority = priority;
		this.createdAt = createdAt;
	}

	/**
	 * Gets the event ID.
	 *
	 * @return event ID
	 */
	public String eventId() {
		return this.eventId;
	}

	/**
	 * Gets the target VEN.
	 *
	 * @return target VEN or null
	 */
	public String venId() {
		return this.venId;
	}

	/**
	 * Gets the signal type.
	 *
	 * @return signal type
	 */
	public SignalType signalType() {
		return this.signalType;
	}

	/**
	 * Gets the simple signal level.
	 *
	 * @return level
	 */
	public int level() {
		return this.level;
	}

	/**
	 * Gets the price signal.
	 *
	 * @return price
	 */
	public double price() {
		return this.price;
	}

	/**
	 * Gets the start time.
	 *
	 * @return start time
	 */
	public Instant start() {
		return this.start;
	}

	/**
	 * Gets the duration.
	 *
	 * @return duration
	 */
	public Duration duration() {
		return this.duration;
	}

	/**
	 * Gets the event priority.
	 *
	 * @return priority
	 */
	public int priority() {
		return this.priority;
	}

	/**
	 * Gets the market context.
	 *
	 * @return market context
	 */
	public String marketContext() {
		return this.marketContext;
	}

	/**
	 * Gets the modification number.
	 *
	 * @return modification number
	 */
	public int modificationNumber() {
		return this.modificationNumber;
	}

	/**
	 * Gets whether the event is cancelled.
	 *
	 * @return true when cancelled
	 */
	public boolean cancelled() {
		return this.cancelled;
	}

	/**
	 * Gets the event status.
	 *
	 * @param now current time
	 * @return status
	 */
	public EventStatus getStatus(Instant now) {
		if (this.cancelled) {
			return EventStatus.CANCELLED;
		}
		if (!now.isBefore(this.start.plus(this.duration))) {
			return EventStatus.COMPLETED;
		}
		if (!now.isBefore(this.start)) {
			return EventStatus.ACTIVE;
		}
		if (!now.isBefore(this.start.minus(Duration.ofMinutes(15)))) {
			return EventStatus.NEAR;
		}
		return EventStatus.FAR;
	}

	/**
	 * Cancels this event.
	 */
	public synchronized void cancel() {
		if (!this.cancelled) {
			this.cancelled = true;
			this.modificationNumber++;
		}
	}

	/**
	 * Records an opt response.
	 *
	 * @param venId VEN ID
	 * @param opt option
	 */
	public void recordOptResponse(String venId, OptType opt) {
		if (venId != null && opt != null) {
			this.optResponses.put(venId, opt);
		}
	}

	/**
	 * Gets cancellation delivery tracking.
	 *
	 * @return VEN IDs that received cancellation
	 */
	public java.util.Set<String> cancelDeliveredTo() {
		return this.cancelDeliveredTo;
	}

	/**
	 * Gets opt responses.
	 *
	 * @return opt responses
	 */
	public Map<String, OptType> optResponses() {
		return Map.copyOf(this.optResponses);
	}

	/**
	 * Serializes this event.
	 *
	 * @return JSON representation
	 */
	public JsonObject toJson() {
		var json = new JsonObject();
		json.addProperty("eventId", this.eventId);
		if (this.venId == null) {
			json.add("venId", com.google.gson.JsonNull.INSTANCE);
		} else {
			json.addProperty("venId", this.venId);
		}
		json.addProperty("signalType", this.signalType.name());
		json.addProperty("level", this.level);
		json.addProperty("price", this.price);
		json.addProperty("start", this.start.toString());
		json.addProperty("end", this.start.plus(this.duration).toString());
		json.addProperty("durationMinutes", this.duration.toMinutes());
		json.addProperty("marketContext", this.marketContext);
		json.addProperty("priority", this.priority);
		json.addProperty("createdAt", this.createdAt.toString());
		json.addProperty("modificationNumber", this.modificationNumber);
		json.addProperty("cancelled", this.cancelled);
		var status = this.getStatus(Instant.now());
		json.addProperty("status", status.toOadr());
		var responses = new JsonObject();
		this.optResponses.forEach((id, opt) -> responses.addProperty(id, opt.toOadr()));
		json.add("optResponses", responses);
		return json;
	}
}
