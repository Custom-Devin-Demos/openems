package io.openems.edge.controller.api.openadr.jsonrpc;

import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.jsonrpc.base.JsonrpcRequest;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.controller.api.openadr.OptState;

/**
 * Represents a JSON-RPC Request for 'setOptState'.
 *
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "id": "UUID",
 *   "method": "setOptState",
 *   "params": {
 *     "eventId": String (optional; defaults to the active event),
 *     "optState": "OPT_IN" | "OPT_OUT"
 *   }
 * }
 * </pre>
 */
public class SetOptStateRequest extends JsonrpcRequest {

	public static final String METHOD = "setOptState";

	/**
	 * Create {@link SetOptStateRequest} from a template {@link JsonrpcRequest}.
	 *
	 * @param r the template {@link JsonrpcRequest}
	 * @return the {@link SetOptStateRequest}
	 * @throws OpenemsNamedException on parse error
	 */
	public static SetOptStateRequest from(JsonrpcRequest r) throws OpenemsNamedException {
		var p = r.getParams();
		var eventId = JsonUtils.getAsOptionalString(p, "eventId").orElse(null);
		var optState = JsonUtils.getAsEnum(OptState.class, p, "optState");
		return new SetOptStateRequest(r, eventId, optState);
	}

	private final String eventId;
	private final OptState optState;

	public SetOptStateRequest(String eventId, OptState optState) {
		super(METHOD);
		this.eventId = eventId;
		this.optState = optState;
	}

	private SetOptStateRequest(JsonrpcRequest request, String eventId, OptState optState) {
		super(request, METHOD);
		this.eventId = eventId;
		this.optState = optState;
	}

	/**
	 * Gets the eventId; null for the active event.
	 * 
	 * @return the eventId
	 */
	public String getEventId() {
		return this.eventId;
	}

	/**
	 * Gets the requested {@link OptState}.
	 * 
	 * @return the {@link OptState}
	 */
	public OptState getOptState() {
		return this.optState;
	}

	@Override
	public JsonObject getParams() {
		return JsonUtils.buildJsonObject() //
				.addPropertyIfNotNull("eventId", this.eventId) //
				.addProperty("optState", this.optState.name()) //
				.build();
	}

}
