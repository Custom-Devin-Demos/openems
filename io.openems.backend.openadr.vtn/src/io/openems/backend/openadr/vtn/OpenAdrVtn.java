package io.openems.backend.openadr.vtn;

import java.util.Collection;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;

/**
 * Registry facade for the OpenADR VTN simulator.
 */
public interface OpenAdrVtn {

	/**
	 * Gets registered VENs.
	 *
	 * @return registered VENs
	 */
	Collection<Ven> getVens();

	/**
	 * Gets configured events.
	 *
	 * @return events
	 */
	Collection<DrEvent> getEvents();

	/**
	 * Creates an event.
	 *
	 * @param request event request
	 * @return created event
	 * @throws OpenemsNamedException on invalid input
	 */
	DrEvent createEvent(CreateEventRequest request) throws OpenemsNamedException;

	/**
	 * Cancels an event.
	 *
	 * @param eventId event ID
	 * @return whether the event existed
	 */
	boolean cancelEvent(String eventId);

	/**
	 * Gets the latest VEN reports.
	 *
	 * @return reports
	 */
	Collection<VenReport> getReports();
}
