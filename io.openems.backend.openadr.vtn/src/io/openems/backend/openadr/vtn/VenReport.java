package io.openems.backend.openadr.vtn;

import java.time.Instant;
import java.util.Map;

import com.google.gson.JsonObject;

/**
 * Latest report values received from a VEN.
 */
public record VenReport(String venId, String reportRequestId, String reportSpecifierId, Map<String, Double> values,
		Instant receivedAt) {

	/**
	 * Serializes this report.
	 *
	 * @return JSON representation
	 */
	public JsonObject toJson() {
		var json = new JsonObject();
		json.addProperty("venId", this.venId);
		json.addProperty("reportRequestId", this.reportRequestId);
		json.addProperty("reportSpecifierId", this.reportSpecifierId);
		var values = new JsonObject();
		this.values.forEach(values::addProperty);
		json.add("values", values);
		json.addProperty("receivedAt", this.receivedAt.toString());
		return json;
	}
}
