package io.openems.edge.controller.api.openadr.jsonrpc;

import com.google.gson.JsonObject;

import io.openems.common.jsonrpc.base.JsonrpcRequest;

/**
 * Represents a JSON-RPC Request for 'getActiveEvents'.
 *
 * <pre>
 * {
 *   "jsonrpc": "2.0",
 *   "id": "UUID",
 *   "method": "getActiveEvents",
 *   "params": {}
 * }
 * </pre>
 */
public class GetActiveEventsRequest extends JsonrpcRequest {

	public static final String METHOD = "getActiveEvents";

	public GetActiveEventsRequest() {
		super(METHOD);
	}

	@Override
	public JsonObject getParams() {
		return new JsonObject();
	}

}
