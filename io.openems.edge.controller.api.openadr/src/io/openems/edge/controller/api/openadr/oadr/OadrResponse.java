package io.openems.edge.controller.api.openadr.oadr;

/**
 * The {@code eiResponse} part of any OpenADR payload.
 * 
 * @param responseCode        the ei:responseCode (200 = OK)
 * @param responseDescription the ei:responseDescription
 * @param requestId           the pyld:requestID
 */
public record OadrResponse(int responseCode, String responseDescription, String requestId) {

	/**
	 * Is this a success response?.
	 * 
	 * @return true for 2xx
	 */
	public boolean isOk() {
		return this.responseCode / 100 == 2;
	}
}
