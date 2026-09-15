package io.openems.edge.controller.api.openadr.oadr;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * A parsed {@code eiEvent} of an {@code oadrDistributeEvent} payload.
 * 
 * @param eventId            the eventID
 * @param modificationNumber the modificationNumber
 * @param status             the eventStatus (none, far, near, active,
 *                           completed, cancelled)
 * @param priority           the priority; 0 if none
 * @param marketContext      the marketContext URI
 * @param createdDateTime    the createdDateTime
 * @param start              start of the active period (dtstart)
 * @param duration           duration of the active period
 * @param tolerance          startafter tolerance
 * @param notification       x-eiNotification duration
 * @param rampUp             x-eiRampUp duration
 * @param recovery           x-eiRecovery duration
 * @param signalType         the {@link SignalType}
 * @param signalName         the signalName
 * @param signalId           the signalID
 * @param currentValue       the currentValue of the signal; may be null
 * @param intervals          the signal intervals
 * @param responseRequired   the oadrResponseRequired value ("always"/"never")
 * @param testEvent          the eventDescriptor testEvent flag
 * @param targetVenIds       venIDs of the eiTarget; empty if the event is not
 *                           restricted to specific VENs
 */
public record OadrEvent(//
		String eventId, //
		long modificationNumber, //
		String status, //
		int priority, //
		String marketContext, //
		Instant createdDateTime, //
		Instant start, //
		Duration duration, //
		Duration tolerance, //
		Duration notification, //
		Duration rampUp, //
		Duration recovery, //
		SignalType signalType, //
		String signalName, //
		String signalId, //
		Double currentValue, //
		List<Interval> intervals, //
		String responseRequired, //
		boolean testEvent, //
		List<String> targetVenIds) {

	public static final String STATUS_CANCELLED = "cancelled";
	public static final String STATUS_COMPLETED = "completed";

	/**
	 * One signal interval.
	 * 
	 * @param start    the interval start
	 * @param duration the interval duration
	 * @param uid      the interval uid
	 * @param value    the payload value (level for SIMPLE, Currency/MWh for PRICE)
	 */
	public record Interval(Instant start, Duration duration, String uid, double value) {

		/**
		 * Gets the end of this interval.
		 * 
		 * @return the end
		 */
		public Instant end() {
			return this.start.plus(this.duration);
		}
	}

	/**
	 * Gets the end of the active period.
	 * 
	 * @return the end
	 */
	public Instant end() {
		return this.start.plus(this.duration);
	}

	/**
	 * Gets the start of the ramp-up phase.
	 * 
	 * @return start minus rampUp
	 */
	public Instant rampUpStart() {
		return this.start.minus(this.rampUp);
	}

	/**
	 * Gets the end of the recovery phase.
	 * 
	 * @return end plus recovery
	 */
	public Instant recoveryEnd() {
		return this.end().plus(this.recovery);
	}

	/**
	 * Is this event targeted at the given VEN?.
	 * 
	 * @param venId the venID
	 * @return true if the eiTarget is empty or contains the venID
	 */
	public boolean targets(String venId) {
		return this.targetVenIds.isEmpty() || venId == null || this.targetVenIds.contains(venId);
	}

	/**
	 * Is this event cancelled?.
	 * 
	 * @return true if eventStatus is cancelled
	 */
	public boolean isCancelled() {
		return STATUS_CANCELLED.equalsIgnoreCase(this.status);
	}

	/**
	 * Gets the {@link EventPhase} of this event at the given time.
	 * 
	 * @param now the current time
	 * @return the {@link EventPhase}
	 */
	public EventPhase phaseAt(Instant now) {
		if (this.isCancelled() || STATUS_COMPLETED.equalsIgnoreCase(this.status)) {
			return EventPhase.AFTER;
		}
		if (now.isBefore(this.rampUpStart())) {
			return EventPhase.BEFORE;
		}
		if (now.isBefore(this.start)) {
			return EventPhase.RAMP_UP;
		}
		if (now.isBefore(this.end())) {
			return EventPhase.ACTIVE;
		}
		if (now.isBefore(this.recoveryEnd())) {
			return EventPhase.RECOVERY;
		}
		return EventPhase.AFTER;
	}

	/**
	 * Gets the signal value at the given time.
	 * 
	 * <p>
	 * During ramp-up the value of the first interval is used, during recovery the
	 * value of the last interval.
	 * 
	 * @param now the current time
	 * @return the value; 0 if no interval matches
	 */
	public double valueAt(Instant now) {
		if (this.intervals.isEmpty()) {
			return this.currentValue != null ? this.currentValue : 0;
		}
		for (var interval : this.intervals) {
			if (!now.isBefore(interval.start()) && now.isBefore(interval.end())) {
				return interval.value();
			}
		}
		if (now.isBefore(this.start)) {
			return this.intervals.get(0).value();
		}
		return this.intervals.get(this.intervals.size() - 1).value();
	}
}
