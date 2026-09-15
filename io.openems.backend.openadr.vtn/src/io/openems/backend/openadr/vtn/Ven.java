package io.openems.backend.openadr.vtn;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/**
 * Registered virtual end node.
 */
public record Ven(String venId, String registrationId, String venName, Instant registeredAt,
		AtomicReference<Instant> lastPoll) {

	/**
	 * Serializes this VEN.
	 *
	 * @return JSON representation
	 */
	public JsonObject toJson() {
		var json = new JsonObject();
		json.addProperty("venId", this.venId);
		json.addProperty("registrationId", this.registrationId);
		json.addProperty("venName", this.venName);
		json.addProperty("registeredAt", this.registeredAt.toString());
		var poll = this.lastPoll.get();
		if (poll == null) {
			json.add("lastPoll", JsonNull.INSTANCE);
		} else {
			json.addProperty("lastPoll", poll.toString());
		}
		return json;
	}
}
