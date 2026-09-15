package io.openems.backend.openadr.vtn;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import io.openems.common.exceptions.OpenemsException;

/**
 * OpenADR XML parser and builder helpers.
 */
public final class OadrXml {

	public static final String OADR = "http://openadr.org/oadr-2.0b/2012/07";
	public static final String EI = "http://docs.oasis-open.org/ns/energyinterop/201110";
	public static final String PYLD = "http://docs.oasis-open.org/ns/energyinterop/201110/payloads";
	public static final String XCAL = "urn:ietf:params:xml:ns:icalendar-2.0";
	public static final String EMIX = "http://docs.oasis-open.org/ns/emix/2011/06";
	public static final String STRM = "urn:ietf:params:xml:ns:icalendar-2.0:stream";

	private OadrXml() {
	}

	/**
	 * Parses secure, namespace-aware XML.
	 *
	 * @param xml XML text
	 * @return parsed document
	 * @throws OpenemsException when parsing fails
	 */
	public static Document parse(String xml) throws OpenemsException {
		try {
			var factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			var builder = factory.newDocumentBuilder();
			return builder.parse(new InputSource(new StringReader(xml)));
		} catch (Exception e) {
			throw new OpenemsException("Invalid OpenADR XML: " + e.getMessage());
		}
	}

	/**
	 * Gets the payload element from an OpenADR document.
	 *
	 * @param document XML document
	 * @return payload element
	 * @throws OpenemsException when no payload is found
	 */
	public static Element payloadElement(Document document) throws OpenemsException {
		var payloads = document.getElementsByTagNameNS(OADR, "oadrPayload");
		if (payloads.getLength() > 0) {
			var signed = ((Element) payloads.item(0)).getElementsByTagNameNS(OADR, "oadrSignedObject");
			if (signed.getLength() > 0) {
				for (var child = signed.item(0).getFirstChild(); child != null; child = child.getNextSibling()) {
					if (child instanceof Element element) {
						return element;
					}
				}
			}
		}
		if (document.getDocumentElement() != null) {
			return document.getDocumentElement();
		}
		throw new OpenemsException("OpenADR payload is missing");
	}

	/**
	 * Finds the first element by local name.
	 *
	 * @param document XML document
	 * @param localName local element name
	 * @return matching element or null
	 */
	public static Element first(Document document, String localName) {
		var elements = document.getElementsByTagNameNS("*", localName);
		return elements.getLength() == 0 ? null : (Element) elements.item(0);
	}

	/**
	 * Reads the first element text by local name.
	 *
	 * @param document XML document
	 * @param localName local element name
	 * @return text or null
	 */
	public static String text(Document document, String localName) {
		var element = first(document, localName);
		return element == null ? null : element.getTextContent().trim();
	}

	/**
	 * Reads the VEN ID.
	 *
	 * @param document XML document
	 * @return VEN ID
	 */
	public static String venId(Document document) {
		return text(document, "venID");
	}

	/**
	 * Reads the VEN name.
	 *
	 * @param document XML document
	 * @return VEN name
	 */
	public static String venName(Document document) {
		return text(document, "venName");
	}

	/**
	 * Reads the request ID.
	 *
	 * @param document XML document
	 * @return request ID
	 */
	public static String requestId(Document document) {
		return text(document, "requestID");
	}

	/**
	 * Reads the registration ID.
	 *
	 * @param document XML document
	 * @return registration ID
	 */
	public static String registrationId(Document document) {
		return text(document, "registrationID");
	}

	/**
	 * Reads an event ID.
	 *
	 * @param document XML document
	 * @return event ID
	 */
	public static String eventId(Document document) {
		return text(document, "eventID");
	}

	/**
	 * Reads an opt type.
	 *
	 * @param document XML document
	 * @return opt type
	 */
	public static String optType(Document document) {
		return text(document, "optType");
	}

	/**
	 * Builds a generic response.
	 *
	 * @param code response code
	 * @param description response description
	 * @param requestId request ID
	 * @param venId VEN ID
	 * @return XML response
	 * @throws OpenemsException on XML serialization error
	 */
	public static String oadrResponse(int code, String description, String requestId, String venId)
			throws OpenemsException {
		var document = document("oadrResponse");
		var root = root(document);
		var response = child(root, EI, "ei", "eiResponse");
		child(response, EI, "ei", "responseCode", Integer.toString(code));
		child(response, EI, "ei", "responseDescription", description == null ? "" : description);
		child(response, PYLD, "pyld", "requestID", requestId == null ? "" : requestId);
		if (venId != null) {
			child(response, EI, "ei", "venID", venId);
		}
		return serialize(document);
	}

	/**
	 * Builds a party registration response.
	 *
	 * @param requestId request ID
	 * @param ven VEN or null
	 * @param vtnId VTN ID
	 * @param pollFrequencySeconds poll frequency
	 * @return XML response
	 * @throws OpenemsException on XML serialization error
	 */
	public static String oadrCreatedPartyRegistration(String requestId, Ven ven, String vtnId,
			int pollFrequencySeconds) throws OpenemsException {
		var document = document("oadrCreatedPartyRegistration");
		var root = root(document);
		var response = child(root, EI, "ei", "eiResponse");
		child(response, EI, "ei", "responseCode", "200");
		child(response, EI, "ei", "responseDescription", "OK");
		child(response, PYLD, "pyld", "requestID", requestId == null ? "" : requestId);
		if (ven != null) {
			child(root, EI, "ei", "registrationID", ven.registrationId());
			child(root, EI, "ei", "venID", ven.venId());
		}
		child(root, EI, "ei", "vtnID", vtnId);
		var profiles = child(root, OADR, "oadr", "oadrProfiles");
		var profile = child(profiles, OADR, "oadr", "oadrProfile");
		child(profile, OADR, "oadr", "oadrProfileName", "2.0b");
		var transports = child(profile, OADR, "oadr", "oadrTransports");
		var transport = child(transports, OADR, "oadr", "oadrTransport");
		child(transport, OADR, "oadr", "oadrTransportName", "simpleHttp");
		var poll = child(root, OADR, "oadr", "oadrRequestedOadrPollFreq");
		var duration = child(poll, XCAL, "xcal", "duration");
		child(duration, XCAL, "xcal", "duration", "PT" + pollFrequencySeconds + "S");
		return serialize(document);
	}

	/**
	 * Builds a party registration cancellation response.
	 *
	 * @param requestId request ID
	 * @param registrationId registration ID
	 * @param venId VEN ID
	 * @return XML response
	 * @throws OpenemsException on XML serialization error
	 */
	public static String oadrCanceledPartyRegistration(String requestId, String registrationId, String venId)
			throws OpenemsException {
		var document = document("oadrCanceledPartyRegistration");
		var root = root(document);
		var response = child(root, EI, "ei", "eiResponse");
		child(response, EI, "ei", "responseCode", "200");
		child(response, EI, "ei", "responseDescription", "OK");
		child(response, PYLD, "pyld", "requestID", requestId == null ? "" : requestId);
		child(root, EI, "ei", "registrationID", registrationId == null ? "" : registrationId);
		child(root, EI, "ei", "venID", venId == null ? "" : venId);
		return serialize(document);
	}

	/**
	 * Builds a report registration response.
	 *
	 * @param requestId request ID
	 * @param venId VEN ID
	 * @return XML response
	 * @throws OpenemsException on XML serialization error
	 */
	public static String oadrRegisteredReport(String requestId, String venId) throws OpenemsException {
		return simpleVenResponse("oadrRegisteredReport", requestId, venId);
	}

	/**
	 * Builds a report update response.
	 *
	 * @param requestId request ID
	 * @param venId VEN ID
	 * @return XML response
	 * @throws OpenemsException on XML serialization error
	 */
	public static String oadrUpdatedReport(String requestId, String venId) throws OpenemsException {
		return simpleVenResponse("oadrUpdatedReport", requestId, venId);
	}

	/**
	 * Builds an opt response.
	 *
	 * @param requestId request ID
	 * @param optId opt ID
	 * @param venId VEN ID
	 * @return XML response
	 * @throws OpenemsException on XML serialization error
	 */
	public static String oadrCreatedOpt(String requestId, String optId, String venId) throws OpenemsException {
		var document = document("oadrCreatedOpt");
		var root = root(document);
		var response = child(root, EI, "ei", "eiResponse");
		child(response, EI, "ei", "responseCode", "200");
		child(response, EI, "ei", "responseDescription", "OK");
		child(response, PYLD, "pyld", "requestID", requestId == null ? "" : requestId);
		child(root, EI, "ei", "optID", optId == null ? "" : optId);
		child(root, EI, "ei", "venID", venId == null ? "" : venId);
		return serialize(document);
	}

	/**
	 * Builds an event distribution.
	 *
	 * @param requestId request ID
	 * @param vtnId VTN ID
	 * @param events events
	 * @param now current time
	 * @return XML distribution
	 * @throws OpenemsException on XML serialization error
	 */
	public static String oadrDistributeEvent(String requestId, String vtnId, List<DrEvent> events, Instant now)
			throws OpenemsException {
		var document = document("oadrDistributeEvent");
		var root = root(document);
		var response = child(root, EI, "ei", "eiResponse");
		child(response, EI, "ei", "responseCode", "200");
		child(response, EI, "ei", "responseDescription", "OK");
		child(response, PYLD, "pyld", "requestID", requestId == null ? "" : requestId);
		child(root, PYLD, "pyld", "requestID", requestId == null ? "" : requestId);
		child(root, EI, "ei", "vtnID", vtnId);
		for (var event : events) {
			var oadrEvent = child(root, OADR, "oadr", "oadrEvent");
			var eiEvent = child(oadrEvent, EI, "ei", "eiEvent");
			var descriptor = child(eiEvent, EI, "ei", "eventDescriptor");
			child(descriptor, EI, "ei", "eventID", event.eventId());
			child(descriptor, EI, "ei", "modificationNumber", Integer.toString(event.modificationNumber()));
			child(descriptor, EI, "ei", "priority", Integer.toString(event.priority()));
			var context = child(descriptor, EI, "ei", "eiMarketContext");
			child(context, EMIX, "emix", "marketContext", event.marketContext());
			child(descriptor, EI, "ei", "createdDateTime", DateTimeFormatter.ISO_INSTANT.format(now));
			child(descriptor, EI, "ei", "eventStatus", event.getStatus(now).toOadr());
			child(descriptor, EI, "ei", "testEvent", "false");
			var period = child(eiEvent, EI, "ei", "eiActivePeriod");
			var properties = child(period, XCAL, "xcal", "properties");
			var start = child(properties, XCAL, "xcal", "dtstart");
			child(start, XCAL, "xcal", "date-time", event.start().toString());
			var duration = child(properties, XCAL, "xcal", "duration");
			child(duration, XCAL, "xcal", "duration", "PT" + event.duration().toMinutes() + "M");
			var tolerance = child(properties, XCAL, "xcal", "tolerance");
			var tolerate = child(tolerance, XCAL, "xcal", "tolerate");
			child(tolerate, XCAL, "xcal", "startafter", "PT0S");
			var notification = child(properties, EI, "ei", "x-eiNotification");
			child(notification, XCAL, "xcal", "duration", "PT0S");
			var signals = child(eiEvent, EI, "ei", "eiEventSignals");
			var signal = child(signals, EI, "ei", "eiEventSignal");
			var intervals = child(signal, STRM, "strm", "intervals");
			var interval = child(intervals, EI, "ei", "interval");
			var intervalDuration = child(interval, XCAL, "xcal", "duration");
			child(intervalDuration, XCAL, "xcal", "duration", "PT" + event.duration().toMinutes() + "M");
			var uid = child(interval, XCAL, "xcal", "uid");
			child(uid, XCAL, "xcal", "text", "0");
			var payload = child(interval, EI, "ei", "signalPayload");
			var payloadFloat = child(payload, EI, "ei", "payloadFloat");
			var eventValue = event.signalType() == SignalType.SIMPLE ? event.level() : event.price();
			child(payloadFloat, EI, "ei", "value", Double.toString(eventValue));
			child(signal, EI, "ei", "signalName",
					event.signalType() == SignalType.SIMPLE ? "simple" : "ELECTRICITY_PRICE");
			child(signal, EI, "ei", "signalType", event.signalType() == SignalType.SIMPLE ? "level" : "price");
			child(signal, EI, "ei", "signalID", "0");
			var current = child(signal, EI, "ei", "currentValue");
			var currentFloat = child(current, EI, "ei", "payloadFloat");
			var currentValue = event.getStatus(now) == EventStatus.ACTIVE ? eventValue : 0;
			child(currentFloat, EI, "ei", "value", Double.toString(currentValue));
			if (event.venId() != null) {
				var target = child(eiEvent, EI, "ei", "eiTarget");
				child(target, EI, "ei", "venID", event.venId());
			}
			child(oadrEvent, OADR, "oadr", "oadrResponseRequired", "always");
		}
		return serialize(document);
	}

	private static String simpleVenResponse(String rootName, String requestId, String venId) throws OpenemsException {
		var document = document(rootName);
		var root = root(document);
		var response = child(root, EI, "ei", "eiResponse");
		child(response, EI, "ei", "responseCode", "200");
		child(response, EI, "ei", "responseDescription", "OK");
		child(response, PYLD, "pyld", "requestID", requestId == null ? "" : requestId);
		if (venId != null) {
			child(root, EI, "ei", "venID", venId);
		}
		return serialize(document);
	}

	private static Document document(String rootName) throws OpenemsException {
		try {
			var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			var payload = document.createElementNS(OADR, "oadr:oadrPayload");
			payload.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:oadr", OADR);
			payload.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:ei", EI);
			payload.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:pyld", PYLD);
			payload.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:xcal", XCAL);
			payload.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:emix", EMIX);
			payload.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:strm", STRM);
			document.appendChild(payload);
			var signed = document.createElementNS(OADR, "oadr:oadrSignedObject");
			payload.appendChild(signed);
			signed.appendChild(document.createElementNS(OADR, "oadr:" + rootName));
			return document;
		} catch (Exception e) {
			throw new OpenemsException("Unable to create OpenADR XML: " + e.getMessage());
		}
	}

	private static Element root(Document document) {
		return (Element) document.getElementsByTagNameNS(OADR, "oadrSignedObject").item(0).getFirstChild();
	}

	private static Element child(Document document, String namespace, String prefix, String name) {
		return child(root(document), namespace, prefix, name);
	}

	private static Element child(Node parent, String namespace, String prefix, String name) {
		var document = parent instanceof Document doc ? doc : parent.getOwnerDocument();
		var element = document.createElementNS(namespace, prefix + ":" + name);
		parent.appendChild(element);
		return element;
	}

	private static Element child(Element parent, String namespace, String prefix, String name, String value) {
		var element = child((Node) parent, namespace, prefix, name);
		element.appendChild(parent.getOwnerDocument().createTextNode(value));
		return element;
	}

	private static String serialize(Document document) throws OpenemsException {
		try {
			var transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			var writer = new StringWriter();
			transformer.transform(new DOMSource(document), new StreamResult(writer));
			return writer.toString();
		} catch (Exception e) {
			throw new OpenemsException("Unable to serialize OpenADR XML: " + e.getMessage());
		}
	}
}
