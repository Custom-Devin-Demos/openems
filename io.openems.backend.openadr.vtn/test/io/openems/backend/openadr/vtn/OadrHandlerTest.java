package io.openems.backend.openadr.vtn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

class OadrHandlerTest {

	private static final String BASE_PATH = "/OpenADR2/Simple/2.0b";
	private static final HttpClient CLIENT = HttpClient.newHttpClient();
	private static final VtnRegistry REGISTRY = new VtnRegistry();
	private static Server server;
	private static URI baseUri;

	@BeforeAll
	static void startServer() throws Exception {
		server = new Server();
		var connector = new ServerConnector(server);
		connector.setPort(0);
		server.addConnector(connector);
		server.setHandler(new OadrHandler(REGISTRY, BASE_PATH, "test-vtn", 10));
		server.start();
		baseUri = URI.create("http://localhost:" + connector.getLocalPort() + BASE_PATH);
	}

	@AfterAll
	static void stopServer() throws Exception {
		server.stop();
	}

	@BeforeEach
	void clearRegistry() {
		REGISTRY.clear();
	}

	@Test
	void createPartyRegistrationReturnsRequiredFields() throws Exception {
		var venId = "ven-" + UUID.randomUUID();
		var response = postXml("/EiRegisterParty", fixture("oadrCreatePartyRegistration.xml", venId, null));
		assertEquals(200, response.statusCode());
		var document = OadrXml.parse(response.body());
		assertNotNull(OadrXml.registrationId(document));
		assertEquals(venId, OadrXml.venId(document));
		assertEquals("test-vtn", OadrXml.text(document, "vtnID"));
		assertEquals("PT10S", OadrXml.text(document, "duration"));
	}

	@Test
	void queryRegistrationReturnsProfiles() throws Exception {
		var response = postXml("/EiRegisterParty", fixture("oadrQueryRegistration.xml", null, null));
		assertEquals(200, response.statusCode());
		var document = OadrXml.parse(response.body());
		assertEquals("oadrCreatedPartyRegistration", OadrXml.payloadElement(document).getLocalName());
		assertEquals("2.0b", OadrXml.text(document, "oadrProfileName"));
	}

	@Test
	void cancelRegistrationRemovesVen() throws Exception {
		var ven = registerVen();
		var xml = fixture("oadrCancelPartyRegistration.xml", ven.venId(), null)
				.replace("${registrationId}", ven.registrationId());
		assertEquals(200, postXml("/EiRegisterParty", xml).statusCode());
		assertEquals(0, getJson("/admin/vens").getAsJsonArray().size());
	}

	@Test
	void pollWithoutEventsReturnsResponse() throws Exception {
		var ven = registerVen();
		var response = postXml("/EiEvent", fixture("oadrPoll.xml", ven.venId(), null));
		assertEquals("oadrResponse", OadrXml.payloadElement(OadrXml.parse(response.body())).getLocalName());
		assertEquals("200", OadrXml.text(OadrXml.parse(response.body()), "responseCode"));
	}

	@Test
	void simpleEventIsDistributed() throws Exception {
		var ven = registerVen();
		var eventId = createEvent("{\"signalType\":\"SIMPLE\",\"level\":2,\"start\":\"now\","
				+ "\"durationMinutes\":30}");
		var document = poll(ven.venId());
		assertEquals(eventId, OadrXml.eventId(document));
		assertEquals("active", OadrXml.text(document, "eventStatus"));
		assertEquals("simple", OadrXml.text(document, "signalName"));
		assertEquals("always", OadrXml.text(document, "oadrResponseRequired"));
		assertTrue(OadrXml.text(document, "value").startsWith("2"));
	}

	@Test
	void priceEventCarriesElectricityPrice() throws Exception {
		var ven = registerVen();
		createEvent("{\"signalType\":\"PRICE\",\"price\":0.42,\"start\":\"now\",\"durationMinutes\":30}");
		var document = poll(ven.venId());
		assertEquals("ELECTRICITY_PRICE", OadrXml.text(document, "signalName"));
		assertEquals("0.42", OadrXml.text(document, "value"));
	}

	@Test
	void targetedEventIsNotDeliveredToOtherVen() throws Exception {
		var first = registerVen();
		var second = registerVen();
		createEvent("{\"venId\":\"" + first.venId()
				+ "\",\"signalType\":\"SIMPLE\",\"level\":1,\"start\":\"now\",\"durationMinutes\":30}");
		assertEquals("oadrResponse", OadrXml.payloadElement(poll(second.venId())).getLocalName());
		assertEquals("oadrDistributeEvent", OadrXml.payloadElement(poll(first.venId())).getLocalName());
	}

	@Test
	void createdEventOptOutAppearsInAdminJson() throws Exception {
		var ven = registerVen();
		var eventId = createEvent("{\"signalType\":\"SIMPLE\",\"level\":1,\"start\":\"now\","
				+ "\"durationMinutes\":30}");
		var xml = fixture("oadrCreatedEvent_optOut.xml", ven.venId(), eventId);
		assertEquals(200, postXml("/EiEvent", xml).statusCode());
		var events = getJson("/admin/events").getAsJsonArray();
		assertEquals("optOut", events.get(0).getAsJsonObject().getAsJsonObject("optResponses").get(ven.venId())
				.getAsString());
	}

	@Test
	void deleteDeliversCancellationOnce() throws Exception {
		var ven = registerVen();
		var eventId = createEvent("{\"signalType\":\"SIMPLE\",\"level\":1,\"start\":\"now\","
				+ "\"durationMinutes\":30}");
		assertEquals(200, delete("/admin/events/" + eventId).statusCode());
		var cancelled = poll(ven.venId());
		assertEquals("cancelled", OadrXml.text(cancelled, "eventStatus"));
		assertEquals("1", OadrXml.text(cancelled, "modificationNumber"));
		assertEquals("oadrResponse", OadrXml.payloadElement(poll(ven.venId())).getLocalName());
	}

	@Test
	void deleteUnknownEventReturnsNotFound() throws Exception {
		var response = delete("/admin/events/missing");
		assertEquals(404, response.statusCode());
		assertTrue(response.body().contains("Unknown event"));
	}

	@Test
	void registerReportReturnsRegisteredReport() throws Exception {
		var ven = registerVen();
		var response = postXml("/EiReport", fixture("oadrRegisterReport.xml", ven.venId(), null));
		assertEquals("oadrRegisteredReport", OadrXml.payloadElement(OadrXml.parse(response.body())).getLocalName());
	}

	@Test
	void updateReportStoresLatestValue() throws Exception {
		var ven = registerVen();
		var response = postXml("/EiReport", fixture("oadrUpdateReport.xml", ven.venId(), null));
		assertEquals("oadrUpdatedReport", OadrXml.payloadElement(OadrXml.parse(response.body())).getLocalName());
		var reports = getJson("/admin/reports").getAsJsonArray();
		assertEquals(123.5, reports.get(0).getAsJsonObject().getAsJsonObject("values").get("power").getAsDouble());
	}

	@Test
	void createOptReturnsCreatedOpt() throws Exception {
		var ven = registerVen();
		var eventId = createEvent("{\"signalType\":\"SIMPLE\",\"level\":1,\"start\":\"now\","
				+ "\"durationMinutes\":30}");
		var response = postXml("/EiOpt", fixture("oadrCreateOpt.xml", ven.venId(), eventId));
		var document = OadrXml.parse(response.body());
		assertEquals("oadrCreatedOpt", OadrXml.payloadElement(document).getLocalName());
		assertEquals("opt-id-1", OadrXml.text(document, "optID"));
	}

	@Test
	void invalidXmlReturnsOpenAdrError() throws Exception {
		var response = postXml("/EiEvent", fixture("invalid.xml", null, null));
		assertEquals(400, response.statusCode());
		assertEquals("400", OadrXml.text(OadrXml.parse(response.body()), "responseCode"));
	}

	@Test
	void unknownPathReturnsNotFound() throws Exception {
		var response = postXml("/unknown", fixture("oadrPoll.xml", "ven-unknown", null));
		assertEquals(404, response.statusCode());
	}

	@Test
	void invalidAdminEventReturnsJsonError() throws Exception {
		var response = postJson("/admin/events",
				"{\"signalType\":\"SIMPLE\",\"level\":7,\"start\":\"now\",\"durationMinutes\":30}");
		assertEquals(400, response.statusCode());
		assertTrue(response.body().contains("error"));
	}

	private static Ven registerVen() throws Exception {
		var venId = "ven-" + UUID.randomUUID();
		var response = postXml("/EiRegisterParty", fixture("oadrCreatePartyRegistration.xml", venId, null));
		var document = OadrXml.parse(response.body());
		return REGISTRY.getVen(OadrXml.venId(document)).orElseThrow();
	}

	private static String createEvent(String body) throws Exception {
		var response = postJson("/admin/events", body);
		assertEquals(201, response.statusCode());
		return JsonParser.parseString(response.body()).getAsJsonObject().get("eventId").getAsString();
	}

	private static org.w3c.dom.Document poll(String venId) throws Exception {
		return OadrXml.parse(postXml("/EiEvent", fixture("oadrPoll.xml", venId, null)).body());
	}

	private static HttpResponse<String> postXml(String path, String body) throws Exception {
		var request = HttpRequest.newBuilder(baseUri.resolve(BASE_PATH + path)).timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/xml").POST(HttpRequest.BodyPublishers.ofString(body)).build();
		return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private static HttpResponse<String> postJson(String path, String body) throws Exception {
		var request = HttpRequest.newBuilder(baseUri.resolve(BASE_PATH + path)).timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
		return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private static HttpResponse<String> delete(String path) throws Exception {
		var request = HttpRequest.newBuilder(baseUri.resolve(BASE_PATH + path)).timeout(Duration.ofSeconds(10))
				.DELETE().build();
		return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private static com.google.gson.JsonElement getJson(String path) throws Exception {
		var request = HttpRequest.newBuilder(baseUri.resolve(BASE_PATH + path)).timeout(Duration.ofSeconds(10)).GET()
				.build();
		return JsonParser.parseString(CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body());
	}

	private static String fixture(String name, String venId, String eventId) throws Exception {
		var content = Files.readString(Path.of("test", "io", "openems", "backend", "openadr", "vtn", "fixtures",
				name));
		if (venId != null) {
			content = content.replace("${venId}", venId);
		}
		if (eventId != null) {
			content = content.replace("${eventId}", eventId);
		}
		return content;
	}
}
