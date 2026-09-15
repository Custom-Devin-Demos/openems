package io.openems.edge.controller.api.openadr.oadr;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.w3c.dom.Element;

import io.openems.common.exceptions.OpenemsException;

public final class Fixtures {

	public static final String CREATED_PARTY_REGISTRATION = "oadrCreatedPartyRegistration.xml";
	public static final String DISTRIBUTE_EVENT_SIMPLE = "oadrDistributeEvent_simple.xml";
	public static final String DISTRIBUTE_EVENT_PRICE = "oadrDistributeEvent_price.xml";
	public static final String DISTRIBUTE_EVENT_TWO_EVENTS_CANCEL = "oadrDistributeEvent_two_events_cancel.xml";
	public static final String RESPONSE_ERROR = "oadrResponse_error.xml";
	public static final String CREATE_REPORT = "oadrCreateReport.xml";

	private Fixtures() {
	}

	/**
	 * Reads a recorded OpenADR payload from the fixtures directory.
	 * 
	 * @param name the file name
	 * @return the XML
	 */
	public static String read(String name) {
		try (var is = Fixtures.class.getResourceAsStream("/io/openems/edge/controller/api/openadr/fixtures/" + name)) {
			if (is == null) {
				throw new IllegalArgumentException("Fixture [" + name + "] not found");
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Reads and parses a recorded OpenADR payload.
	 * 
	 * @param name the file name
	 * @return the root {@link Element}
	 * @throws OpenemsException on parse error
	 */
	public static Element parse(String name) throws OpenemsException {
		return OadrXml.parse(read(name));
	}
}
