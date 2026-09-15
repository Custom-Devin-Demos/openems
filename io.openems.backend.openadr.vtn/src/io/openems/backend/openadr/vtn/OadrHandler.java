package io.openems.backend.openadr.vtn;

import static io.openems.common.utils.JettyUtils.parseJson;
import static io.openems.common.utils.JettyUtils.sendOkResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsException;

/**
 * Jetty handler for OpenADR and administrative JSON requests.
 */
public class OadrHandler extends Handler.Abstract {

	private final Logger log = LoggerFactory.getLogger(OadrHandler.class);
	private final VtnRegistry registry;
	private final String basePath;
	private final String vtnId;
	private final int pollFrequencySeconds;

	/**
	 * Creates the handler.
	 *
	 * @param registry VTN registry
	 * @param basePath OpenADR base path
	 * @param vtnId VTN ID
	 * @param pollFrequencySeconds poll frequency
	 */
	public OadrHandler(VtnRegistry registry, String basePath, String vtnId, int pollFrequencySeconds) {
		this.registry = registry;
		this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
		this.vtnId = vtnId;
		this.pollFrequencySeconds = pollFrequencySeconds;
	}

	@Override
	public boolean handle(Request request, Response response, Callback callback) throws Exception {
		try {
			var path = request.getHttpURI().getDecodedPath();
			if (path.startsWith(this.basePath)) {
				var target = path.substring(this.basePath.length());
				if (target.startsWith("/admin")) {
					this.handleAdmin(request, response, target);
				} else if ("/EiRegisterParty".equals(target) || "/EiEvent".equals(target)
						|| "/EiReport".equals(target) || "/EiOpt".equals(target)) {
					this.handleOadr(request, response, target);
				} else {
					this.sendJsonError(response, HttpStatus.NOT_FOUND_404, "Unknown path");
				}
			} else {
				this.handleAdmin(request, response, path);
			}
			callback.succeeded();
			return true;
		} catch (Exception e) {
			callback.failed(e);
			return false;
		}
	}

	private void handleOadr(Request request, Response response, String path) throws Exception {
		if (!"POST".equals(request.getMethod())) {
			this.sendJsonError(response, HttpStatus.METHOD_NOT_ALLOWED_405, "POST required");
			return;
		}
		try {
			var xml = new String(Content.Source.asInputStream(request).readAllBytes(), StandardCharsets.UTF_8);
			var document = OadrXml.parse(xml);
			var payload = OadrXml.payloadElement(document).getLocalName();
			var result = switch (path) {
			case "/EiRegisterParty" -> this.handleRegister(document, payload);
			case "/EiEvent" -> this.handleEvent(document, payload);
			case "/EiReport" -> this.handleReport(document, payload);
			case "/EiOpt" -> this.handleOpt(document, payload);
			default -> throw new OpenemsException("Unknown OpenADR service path");
			};
			this.sendXml(response, HttpStatus.OK_200, result);
		} catch (Exception e) {
			this.sendXml(response, HttpStatus.BAD_REQUEST_400,
					OadrXml.oadrResponse(400, e.getMessage(), null, null));
		}
	}

	private String handleRegister(Document document, String payload) throws Exception {
		var requestId = OadrXml.requestId(document);
		var requestedVenId = OadrXml.venId(document);
		if (requestedVenId != null && requestedVenId.isBlank()) {
			requestedVenId = null;
		}
		return switch (payload) {
		case "oadrQueryRegistration" -> OadrXml.oadrCreatedPartyRegistration(requestId,
				requestedVenId == null ? null : this.registry.getVen(requestedVenId).orElse(null), this.vtnId,
				this.pollFrequencySeconds);
		case "oadrCreatePartyRegistration" -> {
			var ven = this.registry.register(OadrXml.venName(document), requestedVenId);
			this.log.info("Registered VEN [{}]", ven.venId());
			yield OadrXml.oadrCreatedPartyRegistration(requestId, ven, this.vtnId, this.pollFrequencySeconds);
		}
		case "oadrCancelPartyRegistration" -> {
			var registrationId = OadrXml.registrationId(document);
			this.registry.cancelRegistration(registrationId);
			yield OadrXml.oadrCanceledPartyRegistration(requestId, registrationId, requestedVenId);
		}
		default -> throw new OpenemsException("Unknown registration payload: " + payload);
		};
	}

	private String handleEvent(Document document, String payload) throws Exception {
		var requestId = OadrXml.requestId(document);
		var venId = this.normalizedVenId(document);
		return switch (payload) {
		case "oadrPoll" -> {
			this.registry.recordPoll(venId);
			if (this.registry.getVen(venId).isEmpty()) {
				this.log.warn("Poll from unknown VEN [{}]", venId);
			}
			var events = this.registry.getEventsForVen(venId, Instant.now());
			this.log.info("Poll from VEN [{}], returning [{}] events", venId, events.size());
			yield events.isEmpty() ? OadrXml.oadrResponse(200, "OK", requestId, venId)
					: OadrXml.oadrDistributeEvent(requestId, this.vtnId, events, Instant.now());
		}
		case "oadrCreatedEvent" -> {
			var ids = document.getElementsByTagNameNS("*", "eventID");
			var opts = document.getElementsByTagNameNS("*", "optType");
			for (var i = 0; i < Math.min(ids.getLength(), opts.getLength()); i++) {
				var eventId = ids.item(i).getTextContent().trim();
				var opt = OptType.fromOadr(opts.item(i).getTextContent().trim());
				this.registry.recordOptResponse(eventId, venId, opt);
				this.log.info("VEN [{}] responded [{}] for event [{}]", venId, opt, eventId);
			}
			yield OadrXml.oadrResponse(200, "OK", requestId, venId);
		}
		default -> throw new OpenemsException("Unknown event payload: " + payload);
		};
	}

	private String handleReport(Document document, String payload) throws Exception {
		var requestId = OadrXml.requestId(document);
		var venId = this.normalizedVenId(document);
		return switch (payload) {
		case "oadrRegisterReport" -> OadrXml.oadrRegisteredReport(requestId, venId);
		case "oadrUpdateReport" -> {
			var reports = document.getElementsByTagNameNS(OadrXml.OADR, "oadrReport");
			for (var i = 0; i < reports.getLength(); i++) {
				var report = (Element) reports.item(i);
				var reportRequestId = text(report, "reportRequestID");
				var specifierId = text(report, "reportSpecifierID");
				var values = new HashMap<String, Double>();
				var payloads = report.getElementsByTagNameNS(OadrXml.OADR, "oadrReportPayload");
				for (var j = 0; j < payloads.getLength(); j++) {
					var payloadElement = (Element) payloads.item(j);
					var id = text(payloadElement, "rID");
					var value = text(payloadElement, "value");
					if (id != null && value != null) {
						values.put(id, Double.parseDouble(value));
					}
				}
				this.registry.recordReport(venId, reportRequestId, specifierId, values, Instant.now());
			}
			yield OadrXml.oadrUpdatedReport(requestId, venId);
		}
		case "oadrCreateReport", "oadrCancelReport" -> OadrXml.oadrResponse(200, "OK", requestId, venId);
		default -> throw new OpenemsException("Unknown report payload: " + payload);
		};
	}

	private String handleOpt(Document document, String payload) throws Exception {
		if (!"oadrCreateOpt".equals(payload)) {
			throw new OpenemsException("Unknown opt payload: " + payload);
		}
		var venId = this.normalizedVenId(document);
		var opt = OptType.fromOadr(OadrXml.optType(document));
		var eventId = OadrXml.eventId(document);
		if (eventId != null) {
			this.registry.recordOptResponse(eventId, venId, opt);
		}
		return OadrXml.oadrCreatedOpt(OadrXml.requestId(document), OadrXml.text(document, "optID"), venId);
	}

	private void handleAdmin(Request request, Response response, String path) throws Exception {
		if ("GET".equals(request.getMethod()) && "/admin/vens".equals(path)) {
			var result = new JsonArray();
			this.registry.getVens().forEach(ven -> result.add(ven.toJson()));
			sendOkResponse(response, result);
		} else if ("GET".equals(request.getMethod()) && "/admin/events".equals(path)) {
			var result = new JsonArray();
			this.registry.getEvents().forEach(event -> result.add(event.toJson()));
			sendOkResponse(response, result);
		} else if ("POST".equals(request.getMethod()) && "/admin/events".equals(path)) {
			try {
				var event = this.registry.createEvent(CreateEventRequest.fromJson(parseJson(request), Instant.now()));
				response.setStatus(HttpStatus.CREATED_201);
				response.getHeaders().put("Content-Type", "application/json");
				response.write(true, StandardCharsets.UTF_8.encode(event.toJson().toString()), Callback.NOOP);
			} catch (Exception e) {
				this.sendJsonError(response, HttpStatus.BAD_REQUEST_400, e.getMessage());
			}
		} else if ("GET".equals(request.getMethod()) && "/admin/reports".equals(path)) {
			var result = new JsonArray();
			this.registry.getReports().forEach(report -> result.add(report.toJson()));
			sendOkResponse(response, result);
		} else if ("DELETE".equals(request.getMethod()) && path.startsWith("/admin/events/")) {
			var eventId = path.substring("/admin/events/".length());
			var event = this.registry.getEvents().stream().filter(item -> item.eventId().equals(eventId)).findFirst();
			if (event.isEmpty()) {
				this.sendJsonError(response, HttpStatus.NOT_FOUND_404, "Unknown event: " + eventId);
			} else {
				this.registry.cancelEvent(eventId);
				sendOkResponse(response, event.get().toJson());
			}
		} else {
			this.sendJsonError(response, HttpStatus.NOT_FOUND_404, "Unknown path");
		}
	}

	private static String text(Element parent, String localName) {
		var elements = parent.getElementsByTagNameNS("*", localName);
		return elements.getLength() == 0 ? null : elements.item(0).getTextContent().trim();
	}

	private static String normalizedVenId(Document document) {
		var venId = Objects.requireNonNullElse(OadrXml.venId(document), "");
		return venId.isBlank() ? "" : venId;
	}

	private void sendXml(Response response, int status, String xml) throws IOException {
		response.setStatus(status);
		response.getHeaders().put("Content-Type", "application/xml");
		response.write(true, StandardCharsets.UTF_8.encode(xml), Callback.NOOP);
	}

	private void sendJsonError(Response response, int status, String message) throws IOException {
		var json = new JsonObject();
		json.addProperty("error", message == null ? "Request failed" : message);
		response.setStatus(status);
		response.getHeaders().put("Content-Type", "application/json");
		response.write(true, StandardCharsets.UTF_8.encode(json.toString()), Callback.NOOP);
	}
}
