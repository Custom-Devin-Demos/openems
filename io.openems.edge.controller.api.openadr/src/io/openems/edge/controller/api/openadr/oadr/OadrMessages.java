package io.openems.edge.controller.api.openadr.oadr;

import static io.openems.edge.controller.api.openadr.oadr.OadrNamespaces.SCHEMA_VERSION;
import static io.openems.edge.controller.api.openadr.oadr.OadrXml.child;
import static io.openems.edge.controller.api.openadr.oadr.OadrXml.childText;
import static io.openems.edge.controller.api.openadr.oadr.OadrXml.children;
import static io.openems.edge.controller.api.openadr.oadr.OadrXml.descendant;
import static io.openems.edge.controller.api.openadr.oadr.OadrXml.descendantText;
import static io.openems.edge.controller.api.openadr.oadr.OadrXml.formatDuration;
import static io.openems.edge.controller.api.openadr.oadr.OadrXml.formatInstant;
import static io.openems.edge.controller.api.openadr.oadr.OadrXml.localName;
import static io.openems.edge.controller.api.openadr.oadr.OadrXml.parseDuration;
import static io.openems.edge.controller.api.openadr.oadr.OadrXml.parseInstant;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.utils.XmlUtils;
import io.openems.edge.controller.api.openadr.oadr.OadrEvent.Interval;
import io.openems.edge.controller.api.openadr.oadr.OadrXml.Builder;

/**
 * Builds and parses OpenADR 2.0b payloads.
 */
public final class OadrMessages {

	public static final String REPORT_NAME_METADATA = "METADATA_TELEMETRY_USAGE";
	public static final String REPORT_NAME_TELEMETRY_USAGE = "TELEMETRY_USAGE";
	public static final String OPT_TYPE_OPT_IN = "optIn";
	public static final String OPT_TYPE_OPT_OUT = "optOut";

	private OadrMessages() {
	}

	/**
	 * Response of a VEN for a single event in {@code oadrCreatedEvent}.
	 * 
	 * @param eventId            the eventID
	 * @param modificationNumber the modificationNumber
	 * @param requestId          the requestID of the oadrDistributeEvent
	 * @param optType            "optIn" or "optOut"
	 */
	public record EventResponse(String eventId, long modificationNumber, String requestId, String optType) {
	}

	/**
	 * A parsed {@code oadrDistributeEvent}.
	 * 
	 * @param requestId the requestID
	 * @param vtnId     the vtnID
	 * @param events    the events
	 */
	public record DistributeEvent(String requestId, String vtnId, List<OadrEvent> events) {
	}

	/**
	 * A report request of a VTN ({@code oadrCreateReport}).
	 * 
	 * @param reportRequestId    the reportRequestID
	 * @param reportSpecifierId  the reportSpecifierID
	 * @param reportBackDuration the reportBackDuration
	 * @param granularity        the granularity
	 * @param rIds               the requested rIDs
	 */
	public record ReportRequest(String reportRequestId, String reportSpecifierId, Duration reportBackDuration,
			Duration granularity, List<String> rIds) {
	}

	/**
	 * Description of one telemetry data point (rID).
	 * 
	 * @param rId         the rID
	 * @param description a human readable description
	 */
	public record ReportPoint(String rId, String description) {
	}

	/*
	 * Builders
	 */

	private static Builder payload(String messageName) throws OpenemsException {
		return Builder.root("oadrPayload") //
				.oadr("oadrSignedObject") //
				.oadr(messageName) //
				.attribute("ei:schemaVersion", SCHEMA_VERSION);
	}

	/**
	 * Builds {@code oadrQueryRegistration}.
	 * 
	 * @param requestId the requestID
	 * @return the XML
	 * @throws OpenemsException on error
	 */
	public static String queryRegistration(String requestId) throws OpenemsException {
		var m = payload("oadrQueryRegistration");
		m.pyld("requestID").text(requestId);
		return m.toXml();
	}

	/**
	 * Builds {@code oadrCreatePartyRegistration}.
	 * 
	 * @param requestId      the requestID
	 * @param venName        the VEN name
	 * @param venId          the venID; may be null
	 * @param registrationId the registrationID; may be null
	 * @return the XML
	 * @throws OpenemsException on error
	 */
	public static String createPartyRegistration(String requestId, String venName, String venId,
			String registrationId) throws OpenemsException {
		var m = payload("oadrCreatePartyRegistration");
		m.pyld("requestID").text(requestId);
		if (registrationId != null && !registrationId.isBlank()) {
			m.ei("registrationID").text(registrationId);
		}
		if (venId != null && !venId.isBlank()) {
			m.ei("venID").text(venId);
		}
		m.oadr("oadrProfileName").text(SCHEMA_VERSION);
		m.oadr("oadrTransportName").text("simpleHttp");
		m.oadr("oadrReportOnly").text("false");
		m.oadr("oadrXmlSignature").text("false");
		m.oadr("oadrVenName").text(venName);
		m.oadr("oadrHttpPullModel").text("true");
		return m.toXml();
	}

	/**
	 * Builds {@code oadrCancelPartyRegistration}.
	 * 
	 * @param requestId      the requestID
	 * @param registrationId the registrationID
	 * @param venId          the venID
	 * @return the XML
	 * @throws OpenemsException on error
	 */
	public static String cancelPartyRegistration(String requestId, String registrationId, String venId)
			throws OpenemsException {
		var m = payload("oadrCancelPartyRegistration");
		m.pyld("requestID").text(requestId);
		m.ei("registrationID").text(registrationId);
		m.ei("venID").text(venId);
		return m.toXml();
	}

	/**
	 * Builds {@code oadrPoll}.
	 * 
	 * @param venId the venID
	 * @return the XML
	 * @throws OpenemsException on error
	 */
	public static String poll(String venId) throws OpenemsException {
		var m = payload("oadrPoll");
		m.ei("venID").text(venId);
		return m.toXml();
	}

	/**
	 * Builds {@code oadrCreatedEvent}.
	 * 
	 * @param requestId the requestID of this message
	 * @param venId     the venID
	 * @param responses the {@link EventResponse}s
	 * @return the XML
	 * @throws OpenemsException on error
	 */
	public static String createdEvent(String requestId, String venId, List<EventResponse> responses)
			throws OpenemsException {
		var m = payload("oadrCreatedEvent");
		var created = m.pyld("eiCreatedEvent");
		eiResponse(created, 200, "OK", requestId);
		if (!responses.isEmpty()) {
			var eventResponses = created.ei("eventResponses");
			for (var r : responses) {
				var er = eventResponses.ei("eventResponse");
				er.ei("responseCode").text("200");
				er.ei("responseDescription").text("OK");
				er.pyld("requestID").text(r.requestId());
				var q = er.ei("qualifiedEventID");
				q.ei("eventID").text(r.eventId());
				q.ei("modificationNumber").text(r.modificationNumber());
				er.ei("optType").text(r.optType());
			}
		}
		created.ei("venID").text(venId);
		return m.toXml();
	}

	/**
	 * Builds {@code oadrResponse}.
	 * 
	 * @param requestId    the requestID
	 * @param venId        the venID
	 * @param responseCode the responseCode
	 * @param description  the responseDescription
	 * @return the XML
	 * @throws OpenemsException on error
	 */
	public static String response(String requestId, String venId, int responseCode, String description)
			throws OpenemsException {
		var m = payload("oadrResponse");
		eiResponse(m, responseCode, description, requestId);
		m.ei("venID").text(venId);
		return m.toXml();
	}

	/**
	 * Builds {@code oadrRegisterReport} with a METADATA_TELEMETRY_USAGE report.
	 * 
	 * @param requestId         the requestID
	 * @param venId             the venID
	 * @param reportSpecifierId the reportSpecifierID
	 * @param createdDateTime   the createdDateTime
	 * @param points            the {@link ReportPoint}s
	 * @param minPeriod         the minimum sampling period
	 * @return the XML
	 * @throws OpenemsException on error
	 */
	public static String registerReport(String requestId, String venId, String reportSpecifierId,
			Instant createdDateTime, List<ReportPoint> points, Duration minPeriod) throws OpenemsException {
		var m = payload("oadrRegisterReport");
		m.pyld("requestID").text(requestId);
		var report = m.oadr("oadrReport");
		report.xcal("duration").xcal("duration").text(formatDuration(Duration.ZERO));
		for (var p : points) {
			var d = report.oadr("oadrReportDescription");
			d.ei("rID").text(p.rId());
			d.ei("reportType").text("usage");
			var itemBase = d.emix("itemBase").power("powerReal");
			itemBase.power("itemDescription").text("RealPower");
			itemBase.power("itemUnits").text("W");
			itemBase.scale("siScaleCode").text("none");
			var pa = itemBase.power("powerAttributes");
			pa.power("hertz").text("60");
			pa.power("voltage").text("230");
			pa.power("ac").text("true");
			d.ei("readingType").text("Direct Read");
			d.emix("marketContext").text(p.description());
			var sampling = d.oadr("oadrSamplingRate");
			sampling.oadr("oadrMinPeriod").text(formatDuration(minPeriod));
			sampling.oadr("oadrMaxPeriod").text(formatDuration(minPeriod));
			sampling.oadr("oadrOnChange").text("false");
		}
		report.ei("reportRequestID").text("0");
		report.ei("reportSpecifierID").text(reportSpecifierId);
		report.ei("reportName").text(REPORT_NAME_METADATA);
		report.ei("createdDateTime").text(formatInstant(createdDateTime));
		m.ei("venID").text(venId);
		return m.toXml();
	}

	/**
	 * Builds {@code oadrCreatedReport} acknowledging pending report requests.
	 * 
	 * @param requestId               the requestID
	 * @param venId                   the venID
	 * @param pendingReportRequestIds the pending reportRequestIDs
	 * @return the XML
	 * @throws OpenemsException on error
	 */
	public static String createdReport(String requestId, String venId, List<String> pendingReportRequestIds)
			throws OpenemsException {
		var m = payload("oadrCreatedReport");
		eiResponse(m, 200, "OK", requestId);
		var pending = m.oadr("oadrPendingReports");
		for (var id : pendingReportRequestIds) {
			pending.ei("reportRequestID").text(id);
		}
		m.ei("venID").text(venId);
		return m.toXml();
	}

	/**
	 * Builds {@code oadrUpdateReport} with one TELEMETRY_USAGE interval.
	 * 
	 * @param requestId         the requestID
	 * @param venId             the venID
	 * @param reportRequestId   the reportRequestID
	 * @param reportSpecifierId the reportSpecifierID
	 * @param intervalStart     start of the reported interval
	 * @param intervalDuration  duration of the reported interval
	 * @param values            rID to value
	 * @return the XML
	 * @throws OpenemsException on error
	 */
	public static String updateReport(String requestId, String venId, String reportRequestId,
			String reportSpecifierId, Instant intervalStart, Duration intervalDuration, Map<String, Double> values)
			throws OpenemsException {
		var m = payload("oadrUpdateReport");
		m.pyld("requestID").text(requestId);
		var report = m.oadr("oadrReport");
		report.ei("eiReportID").text(reportRequestId + "-" + intervalStart.getEpochSecond());
		var interval = report.strm("intervals").ei("interval");
		interval.xcal("dtstart").xcal("date-time").text(formatInstant(intervalStart));
		interval.xcal("duration").xcal("duration").text(formatDuration(intervalDuration));
		interval.xcal("uid").xcal("text").text("0");
		for (var e : values.entrySet()) {
			var p = interval.oadr("oadrReportPayload");
			p.ei("rID").text(e.getKey());
			p.ei("confidence").text("100");
			p.ei("accuracy").text("0");
			p.ei("payloadFloat").ei("value").text(formatValue(e.getValue()));
			p.oadr("oadrDataQuality").text("Quality Good - Non Specific");
		}
		report.ei("reportRequestID").text(reportRequestId);
		report.ei("reportSpecifierID").text(reportSpecifierId);
		report.ei("reportName").text(REPORT_NAME_TELEMETRY_USAGE);
		report.ei("createdDateTime").text(formatInstant(intervalStart.plus(intervalDuration)));
		m.ei("venID").text(venId);
		return m.toXml();
	}

	/**
	 * Builds {@code oadrCreateOpt} for a single event (schedule-based opt).
	 * 
	 * @param requestId       the requestID
	 * @param venId           the venID
	 * @param optId           the optID
	 * @param optType         "optIn" or "optOut"
	 * @param event           the {@link OadrEvent}
	 * @param createdDateTime the createdDateTime
	 * @return the XML
	 * @throws OpenemsException on error
	 */
	public static String createOpt(String requestId, String venId, String optId, String optType, OadrEvent event,
			Instant createdDateTime) throws OpenemsException {
		var m = payload("oadrCreateOpt");
		m.ei("optID").text(optId);
		m.ei("optType").text(optType);
		m.ei("optReason").text("economic");
		if (event.marketContext() != null && !event.marketContext().isBlank()) {
			m.emix("marketContext").text(event.marketContext());
		}
		m.ei("venID").text(venId);
		m.ei("createdDateTime").text(formatInstant(createdDateTime));
		m.pyld("requestID").text(requestId);
		var q = m.ei("qualifiedEventID");
		q.ei("eventID").text(event.eventId());
		q.ei("modificationNumber").text(event.modificationNumber());
		return m.toXml();
	}

	private static void eiResponse(Builder parent, int code, String description, String requestId) {
		var r = parent.ei("eiResponse");
		r.ei("responseCode").text(Integer.toString(code));
		r.ei("responseDescription").text(description);
		r.pyld("requestID").text(requestId);
	}

	private static String formatValue(double value) {
		if (value == Math.rint(value) && Math.abs(value) < 1e15) {
			return Long.toString((long) value);
		}
		return Double.toString(value);
	}

	/*
	 * Parsers
	 */

	/**
	 * Gets the OpenADR message element inside {@code oadrPayload/oadrSignedObject}
	 * or the root itself if not wrapped.
	 * 
	 * @param root the root {@link Element}
	 * @return the message {@link Element}, e.g. oadrDistributeEvent
	 */
	public static Element message(Element root) {
		var e = root;
		if (localName(e).equals("oadrPayload")) {
			e = child(e, "oadrSignedObject").orElse(e);
		}
		if (localName(e).equals("oadrSignedObject")) {
			e = XmlUtils.stream(e) //
					.filter(n -> n.getNodeType() == Node.ELEMENT_NODE) //
					.map(Element.class::cast) //
					.findFirst().orElse(e);
		}
		return e;
	}

	/**
	 * Gets the local name of the OpenADR message, e.g. "oadrDistributeEvent".
	 * 
	 * @param root the root {@link Element}
	 * @return the local name
	 */
	public static String messageName(Element root) {
		return localName(message(root));
	}

	/**
	 * Parses the {@code eiResponse} of any message.
	 * 
	 * @param root the root {@link Element}
	 * @return the {@link OadrResponse}; responseCode 0 if no eiResponse found
	 */
	public static OadrResponse parseResponse(Element root) {
		var m = message(root);
		var eiResponse = descendant(m, "eiResponse");
		if (eiResponse.isEmpty()) {
			return new OadrResponse(0, "", descendantText(m, "requestID").orElse(""));
		}
		var r = eiResponse.get();
		return new OadrResponse(//
				childText(r, "responseCode").map(OadrMessages::parseIntSafe).orElse(0), //
				childText(r, "responseDescription").orElse(""), //
				childText(r, "requestID").orElse(""));
	}

	/**
	 * Parses {@code oadrCreatedPartyRegistration}.
	 * 
	 * @param root the root {@link Element}
	 * @return the {@link PartyRegistration}
	 */
	public static PartyRegistration parseCreatedPartyRegistration(Element root) {
		var m = message(root);
		var response = parseResponse(root);
		return new PartyRegistration(//
				response.responseCode(), //
				childText(m, "registrationID").orElse(null), //
				childText(m, "venID").orElse(null), //
				childText(m, "vtnID").orElse(null), //
				childText(m, "oadrRequestedOadrPollFreq") //
						.or(() -> descendant(m, "oadrRequestedOadrPollFreq").flatMap(e -> childText(e, "duration")))
						.map(OadrXml::parseDuration).orElse(null));
	}

	/**
	 * Parses {@code oadrDistributeEvent}.
	 * 
	 * @param root the root {@link Element}
	 * @return the {@link DistributeEvent}
	 * @throws OpenemsException on parse error
	 */
	public static DistributeEvent parseDistributeEvent(Element root) throws OpenemsException {
		var m = message(root);
		var requestId = childText(m, "requestID").orElse("");
		var vtnId = childText(m, "vtnID").orElse("");
		var events = new ArrayList<OadrEvent>();
		for (var oadrEvent : children(m, "oadrEvent").toList()) {
			var responseRequired = childText(oadrEvent, "oadrResponseRequired").orElse("always");
			var eiEvent = child(oadrEvent, "eiEvent")
					.orElseThrow(() -> new OpenemsException("oadrEvent without eiEvent"));
			events.add(parseEiEvent(eiEvent, responseRequired));
		}
		return new DistributeEvent(requestId, vtnId, events);
	}

	private static OadrEvent parseEiEvent(Element eiEvent, String responseRequired) throws OpenemsException {
		var descriptor = child(eiEvent, "eventDescriptor")
				.orElseThrow(() -> new OpenemsException("eiEvent without eventDescriptor"));
		var eventId = childText(descriptor, "eventID")
				.orElseThrow(() -> new OpenemsException("eventDescriptor without eventID"));
		var modificationNumber = childText(descriptor, "modificationNumber").map(Long::parseLong).orElse(0L);
		var status = childText(descriptor, "eventStatus").orElse("none");
		var priority = childText(descriptor, "priority").map(OadrMessages::parseIntSafe).orElse(0);
		var marketContext = descendantText(descriptor, "marketContext").orElse("");
		var createdDateTime = childText(descriptor, "createdDateTime").map(t -> {
			try {
				return parseInstant(t);
			} catch (OpenemsException e) {
				return null;
			}
		}).orElse(null);
		var testEvent = childText(descriptor, "testEvent").map(Boolean::parseBoolean).orElse(false);

		var activePeriod = child(eiEvent, "eiActivePeriod")
				.orElseThrow(() -> new OpenemsException("eiEvent [" + eventId + "] without eiActivePeriod"));
		var properties = child(activePeriod, "properties").orElse(activePeriod);
		var start = parseInstant(descendantText(properties, "date-time")
				.orElseThrow(() -> new OpenemsException("eiEvent [" + eventId + "] without dtstart")));
		var duration = child(properties, "duration").flatMap(e -> childText(e, "duration")).map(OadrXml::parseDuration)
				.orElse(Duration.ZERO);
		var tolerance = descendant(properties, "startafter").map(e -> parseDuration(e.getTextContent().trim()))
				.orElse(Duration.ZERO);
		var notification = child(properties, "x-eiNotification").flatMap(e -> childText(e, "duration"))
				.map(OadrXml::parseDuration).orElse(Duration.ZERO);
		var rampUp = child(properties, "x-eiRampUp").flatMap(e -> childText(e, "duration"))
				.map(OadrXml::parseDuration).orElse(Duration.ZERO);
		var recovery = child(properties, "x-eiRecovery").flatMap(e -> childText(e, "duration"))
				.map(OadrXml::parseDuration).orElse(Duration.ZERO);

		// Signal: prefer SIMPLE or PRICE, otherwise the first one
		var signals = child(eiEvent, "eiEventSignals").map(s -> children(s, "eiEventSignal").toList())
				.orElse(List.of());
		Element signal = null;
		var signalType = SignalType.UNKNOWN;
		for (var s : signals) {
			var type = SignalType.from(childText(s, "signalName").orElse(null), childText(s, "signalType").orElse(null));
			if (signal == null || (signalType == SignalType.UNKNOWN && type != SignalType.UNKNOWN)) {
				signal = s;
				signalType = type;
			}
		}

		var intervals = new ArrayList<Interval>();
		String signalName = null;
		String signalId = null;
		Double currentValue = null;
		if (signal != null) {
			signalName = childText(signal, "signalName").orElse(null);
			signalId = childText(signal, "signalID").orElse(null);
			var factor = signalType == SignalType.PRICE ? priceFactorToMwh(signal) : 1.0;
			currentValue = child(signal, "currentValue").flatMap(c -> descendantText(c, "value"))
					.map(Double::parseDouble).map(v -> v * factor).orElse(null);
			var cursor = start;
			for (var interval : child(signal, "intervals").map(i -> children(i, "interval").toList())
					.orElse(List.of())) {
				var intervalStart = descendant(interval, "dtstart").flatMap(d -> childText(d, "date-time"))
						.map(t -> {
							try {
								return parseInstant(t);
							} catch (OpenemsException e) {
								return null;
							}
						}).orElse(cursor);
				var intervalDuration = child(interval, "duration").flatMap(d -> childText(d, "duration"))
						.map(OadrXml::parseDuration).orElse(duration);
				var uid = child(interval, "uid").flatMap(u -> childText(u, "text")).orElse("0");
				var value = child(interval, "signalPayload").flatMap(p -> descendantText(p, "value"))
						.map(Double::parseDouble).orElse(0.0) * factor;
				intervals.add(new Interval(intervalStart, intervalDuration, uid, value));
				cursor = intervalStart.plus(intervalDuration);
			}
		}

		var targetVenIds = child(eiEvent, "eiTarget") //
				.map(t -> children(t, "venID").map(v -> v.getTextContent().trim()).toList()) //
				.orElse(List.of());

		return new OadrEvent(eventId, modificationNumber, status, priority, marketContext, createdDateTime, start,
				duration, tolerance, notification, rampUp, recovery, signalType, signalName, signalId, currentValue,
				intervals, responseRequired, testEvent, targetVenIds);
	}

	/**
	 * Gets the factor to normalise a PRICE signal to Currency/MWh from its
	 * {@code itemBase}.
	 * 
	 * <p>
	 * {@code currencyPerKWh} yields 1000, {@code currencyPerMWh} or missing
	 * itemBase yields 1. An {@code siScaleCode} is applied additionally.
	 * 
	 * @param signal the eiEventSignal
	 * @return the factor
	 */
	protected static double priceFactorToMwh(Element signal) {
		var itemBase = child(signal, "itemBase");
		if (itemBase.isEmpty()) {
			return 1.0;
		}
		var unitElement = XmlUtils.stream(itemBase.get()) //
				.filter(n -> n.getNodeType() == Node.ELEMENT_NODE) //
				.map(Element.class::cast) //
				.findFirst();
		var description = unitElement.map(OadrXml::localName).orElse("")
				+ unitElement.flatMap(u -> childText(u, "itemDescription")).orElse("")
				+ unitElement.flatMap(u -> childText(u, "itemUnits")).orElse("");
		var lower = description.toLowerCase();
		var factor = 1.0;
		if (lower.contains("kwh") || lower.contains("perkw")) {
			factor = 1000.0;
		} else if (lower.contains("wh") && !lower.contains("mwh")) {
			factor = 1_000_000.0;
		}
		var scale = unitElement.flatMap(u -> childText(u, "siScaleCode")).orElse("none");
		factor *= switch (scale) {
		case "k" -> 1e3;
		case "M" -> 1e6;
		case "m" -> 1e-3;
		case "c" -> 1e-2;
		case "u" -> 1e-6;
		default -> 1.0;
		};
		return factor;
	}

	/**
	 * Parses {@code oadrCreateReport}.
	 * 
	 * @param root the root {@link Element}
	 * @return the {@link ReportRequest}s
	 */
	public static List<ReportRequest> parseCreateReport(Element root) {
		var m = message(root);
		var result = new ArrayList<ReportRequest>();
		for (var rr : children(m, "oadrReportRequest").toList()) {
			var reportRequestId = childText(rr, "reportRequestID").orElse("");
			var specifier = child(rr, "reportSpecifier");
			var specifierId = specifier.flatMap(s -> childText(s, "reportSpecifierID")).orElse("");
			var granularity = specifier.flatMap(s -> child(s, "granularity")).flatMap(g -> childText(g, "duration"))
					.map(OadrXml::parseDuration).orElse(Duration.ZERO);
			var reportBack = specifier.flatMap(s -> child(s, "reportBackDuration"))
					.flatMap(g -> childText(g, "duration")).map(OadrXml::parseDuration).orElse(Duration.ZERO);
			var rIds = specifier.map(s -> children(s, "specifierPayload").toList()).orElse(List.of()).stream()
					.map(p -> childText(p, "rID").orElse("")).filter(s -> !s.isEmpty()).toList();
			result.add(new ReportRequest(reportRequestId, specifierId, reportBack, granularity, rIds));
		}
		return result;
	}

	/**
	 * Gets the requestID of a message.
	 * 
	 * @param root the root {@link Element}
	 * @return the requestID or empty
	 */
	public static Optional<String> requestId(Element root) {
		return descendantText(message(root), "requestID");
	}

	private static int parseIntSafe(String s) {
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
