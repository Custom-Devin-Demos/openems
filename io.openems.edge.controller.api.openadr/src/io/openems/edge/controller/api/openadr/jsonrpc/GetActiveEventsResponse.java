package io.openems.edge.controller.api.openadr.jsonrpc;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.openems.common.jsonrpc.base.JsonrpcResponseSuccess;
import io.openems.common.utils.JsonUtils;

/**
 * Represents a JSON-RPC Response for 'getActiveEvents'.
 *
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "id": "UUID",
 *   "result": {
 *     "events": [{
 *       "eventId": String,
 *       "signalType": "SIMPLE" | "PRICE" | "UNKNOWN",
 *       "level": number,
 *       "price": number | null,
 *       "start": number (epoch seconds),
 *       "end": number (epoch seconds),
 *       "optState": "OPT_IN" | "OPT_OUT",
 *       "status": String,
 *       "phase": String
 *     }]
 *   }
 * }
 * </pre>
 */
public class GetActiveEventsResponse extends JsonrpcResponseSuccess {

	/**
	 * One event entry.
	 * 
	 * @param eventId    the eventID
	 * @param signalType the signal type
	 * @param level      the SIMPLE level; -1 if none
	 * @param price      the PRICE value in Currency/MWh; null if none
	 * @param start      start epoch seconds
	 * @param end        end epoch seconds
	 * @param optState   the opt state name
	 * @param status     the eventStatus
	 * @param phase      the current phase
	 */
	public record Event(String eventId, String signalType, int level, Double price, long start, long end,
			String optState, String status, String phase) {

		/**
		 * Serializes to JSON.
		 * 
		 * @return the {@link JsonObject}
		 */
		public JsonObject toJson() {
			return JsonUtils.buildJsonObject() //
					.addProperty("eventId", this.eventId) //
					.addProperty("signalType", this.signalType) //
					.addProperty("level", this.level) //
					.addPropertyIfNotNull("price", this.price) //
					.addProperty("start", this.start) //
					.addProperty("end", this.end) //
					.addProperty("optState", this.optState) //
					.addProperty("status", this.status) //
					.addProperty("phase", this.phase) //
					.build();
		}
	}

	private final List<Event> events;

	public GetActiveEventsResponse(UUID id, List<Event> events) {
		super(id);
		this.events = events;
	}

	@Override
	public JsonObject getResult() {
		var array = new JsonArray();
		for (var e : this.events) {
			array.add(e.toJson());
		}
		return JsonUtils.buildJsonObject() //
				.add("events", array) //
				.build();
	}

}
