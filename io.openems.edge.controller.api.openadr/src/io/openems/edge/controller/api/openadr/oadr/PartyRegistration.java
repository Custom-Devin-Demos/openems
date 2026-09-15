package io.openems.edge.controller.api.openadr.oadr;

import java.time.Duration;

/**
 * Result of {@code oadrCreatedPartyRegistration}.
 * 
 * @param responseCode   the ei:responseCode
 * @param registrationId the registrationID; null if not registered
 * @param venId          the venID
 * @param vtnId          the vtnID
 * @param requestedPollFrequency the oadrRequestedOadrPollFreq; null if not given
 */
public record PartyRegistration(//
		int responseCode, //
		String registrationId, //
		String venId, //
		String vtnId, //
		Duration requestedPollFrequency) {

	/**
	 * Was the registration accepted?.
	 * 
	 * @return true if responseCode is 2xx and a registrationID is present
	 */
	public boolean isRegistered() {
		return this.responseCode / 100 == 2 && this.registrationId != null && !this.registrationId.isBlank();
	}
}
