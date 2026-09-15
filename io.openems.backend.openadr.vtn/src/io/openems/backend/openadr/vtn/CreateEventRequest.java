package io.openems.backend.openadr.vtn;

import java.time.Instant;
import java.time.OffsetDateTime;

import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.utils.JsonUtils;

/**
 * Admin event creation request.
 */
public record CreateEventRequest(String venId, SignalType signalType, Integer level, Double price, Instant start,
		int durationMinutes, String marketContext, int priority) {

	/**
	 * Parses a JSON request.
	 *
	 * @param json request JSON
	 * @param now current time
	 * @return parsed request
	 * @throws OpenemsNamedException on invalid input
	 */
	public static CreateEventRequest fromJson(JsonObject json, Instant now) throws OpenemsNamedException {
		try {
			var typeName = JsonUtils.getAsString(json, "signalType");
			var type = SignalType.valueOf(typeName.toUpperCase());
			var level = JsonUtils.getAsOptionalInt(json, "level").orElse(null);
			var price = JsonUtils.getAsOptionalDouble(json, "price").orElse(null);
			if (type == SignalType.SIMPLE && (level == null || level < 0 || level > 3)) {
				throw new OpenemsException("SIMPLE signal level must be between 0 and 3");
			}
			if (type == SignalType.PRICE && price == null) {
				throw new OpenemsException("PRICE signal requires price");
			}
			var startText = JsonUtils.getAsOptionalString(json, "start").orElse("now");
			Instant start;
			if ("now".equalsIgnoreCase(startText)) {
				start = now;
			} else {
				try {
					start = Instant.parse(startText);
				} catch (Exception e) {
					start = OffsetDateTime.parse(startText).toInstant();
				}
			}
			var duration = JsonUtils.getAsInt(json, "durationMinutes");
			if (duration <= 0) {
				throw new OpenemsException("durationMinutes must be greater than zero");
			}
			return new CreateEventRequest(JsonUtils.getAsOptionalString(json, "venId").orElse(null), type, level, price,
					start, duration, JsonUtils.getAsOptionalString(json, "marketContext").orElse(null),
					JsonUtils.getAsOptionalInt(json, "priority").orElse(0));
		} catch (IllegalArgumentException e) {
			throw new OpenemsException("Invalid signalType");
		}
	}
}
