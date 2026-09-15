package io.openems.edge.controller.api.openadr.oadr;

import static io.openems.edge.controller.api.openadr.oadr.OadrXml.child;
import static io.openems.edge.controller.api.openadr.oadr.OadrXml.childText;
import static io.openems.edge.controller.api.openadr.oadr.OadrXml.children;
import static io.openems.edge.controller.api.openadr.oadr.OadrXml.descendant;
import static io.openems.edge.controller.api.openadr.oadr.OadrXml.descendantText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.openems.edge.controller.api.openadr.oadr.OadrMessages.EventResponse;
import io.openems.edge.controller.api.openadr.oadr.OadrMessages.ReportPoint;

public class OadrMessagesTest {

	@Test
	public void testParseCreatedPartyRegistration() throws Exception {
		var root = Fixtures.parse(Fixtures.CREATED_PARTY_REGISTRATION);
		assertEquals("oadrCreatedPartyRegistration", OadrMessages.messageName(root));

		var registration = OadrMessages.parseCreatedPartyRegistration(root);
		assertEquals(200, registration.responseCode());
		assertEquals("reg-4711", registration.registrationId());
		assertEquals("ven-openems-1", registration.venId());
		assertEquals("openems-vtn", registration.vtnId());
		assertEquals(Duration.ofSeconds(30), registration.requestedPollFrequency());
		assertTrue(registration.isRegistered());
	}

	@Test
	public void testParseDistributeEventSimple() throws Exception {
		var distribute = OadrMessages.parseDistributeEvent(Fixtures.parse(Fixtures.DISTRIBUTE_EVENT_SIMPLE));
		assertEquals("req-dist-1", distribute.requestId());
		assertEquals("openems-vtn", distribute.vtnId());
		assertEquals(1, distribute.events().size());

		var event = distribute.events().get(0);
		assertEquals("evt-simple-1", event.eventId());
		assertEquals(0, event.modificationNumber());
		assertEquals("far", event.status());
		assertEquals(1, event.priority());
		assertEquals("http://openems.io/marketcontext/us-grid", event.marketContext());
		assertEquals(Instant.parse("2026-09-15T11:55:00Z"), event.createdDateTime());
		assertEquals(Instant.parse("2026-09-15T12:00:00Z"), event.start());
		assertEquals(Duration.ofHours(1), event.duration());
		assertEquals(Duration.ofMinutes(5), event.tolerance());
		assertEquals(Duration.ofMinutes(15), event.notification());
		assertEquals(Duration.ofMinutes(10), event.rampUp());
		assertEquals(Duration.ofMinutes(10), event.recovery());
		assertEquals(SignalType.SIMPLE, event.signalType());
		assertEquals("simple", event.signalName());
		assertEquals("sig-1", event.signalId());
		assertEquals(0.0, event.currentValue(), 0.001);
		assertEquals("always", event.responseRequired());
		assertFalse(event.testEvent());
		assertEquals(List.of("ven-openems-1"), event.targetVenIds());

		assertEquals(2, event.intervals().size());
		var first = event.intervals().get(0);
		assertEquals(Instant.parse("2026-09-15T12:00:00Z"), first.start());
		assertEquals(Duration.ofMinutes(30), first.duration());
		assertEquals("0", first.uid());
		assertEquals(2.0, first.value(), 0.001);
		// second interval has no dtstart: follows the first one
		var second = event.intervals().get(1);
		assertEquals(Instant.parse("2026-09-15T12:30:00Z"), second.start());
		assertEquals("1", second.uid());
		assertEquals(3.0, second.value(), 0.001);
	}

	@Test
	public void testParseDistributeEventPriceNormalisedToMwh() throws Exception {
		var distribute = OadrMessages.parseDistributeEvent(Fixtures.parse(Fixtures.DISTRIBUTE_EVENT_PRICE));
		var event = distribute.events().get(0);
		assertEquals("evt-price-1", event.eventId());
		assertEquals(2, event.modificationNumber());
		assertEquals("active", event.status());
		assertEquals(SignalType.PRICE, event.signalType());
		assertEquals("ELECTRICITY_PRICE", event.signalName());
		assertTrue(event.targetVenIds().isEmpty());
		// 0.05 USD/kWh -> 50 USD/MWh
		assertEquals(50.0, event.currentValue(), 0.001);
		assertEquals(50.0, event.intervals().get(0).value(), 0.001);
		assertEquals(250.0, event.intervals().get(1).value(), 0.001);
		assertEquals(Instant.parse("2026-09-15T15:00:00Z"), event.intervals().get(1).start());
		// no ramp/recovery given
		assertEquals(Duration.ZERO, event.rampUp());
		assertEquals(Duration.ZERO, event.recovery());
	}

	@Test
	public void testParseDistributeEventTwoEventsWithCancellation() throws Exception {
		var distribute = OadrMessages
				.parseDistributeEvent(Fixtures.parse(Fixtures.DISTRIBUTE_EVENT_TWO_EVENTS_CANCEL));
		assertEquals(2, distribute.events().size());

		var cancelled = distribute.events().get(0);
		assertEquals("evt-simple-1", cancelled.eventId());
		assertEquals(1, cancelled.modificationNumber());
		assertTrue(cancelled.isCancelled());
		assertEquals(EventPhase.AFTER, cancelled.phaseAt(Instant.parse("2026-09-15T12:30:00Z")));

		var second = distribute.events().get(1);
		assertEquals("evt-simple-2", second.eventId());
		assertFalse(second.isCancelled());
		assertEquals(2, second.priority());
		assertEquals("never", second.responseRequired());
		assertEquals(Instant.parse("2026-09-15T12:30:00Z"), second.start());
		assertEquals(1.0, second.valueAt(Instant.parse("2026-09-15T12:45:00Z")), 0.001);
	}

	@Test
	public void testParseResponseError() throws Exception {
		var root = Fixtures.parse(Fixtures.RESPONSE_ERROR);
		assertEquals("oadrResponse", OadrMessages.messageName(root));
		var response = OadrMessages.parseResponse(root);
		assertEquals(452, response.responseCode());
		assertEquals("Invalid ID", response.responseDescription());
		assertEquals("req-err-1", response.requestId());
		assertFalse(response.isOk());
	}

	@Test
	public void testParseCreateReport() throws Exception {
		var root = Fixtures.parse(Fixtures.CREATE_REPORT);
		var requests = OadrMessages.parseCreateReport(root);
		assertEquals(1, requests.size());
		var request = requests.get(0);
		assertEquals("rr-1", request.reportRequestId());
		assertEquals("openems_telemetry_usage", request.reportSpecifierId());
		assertEquals(Duration.ofMinutes(1), request.granularity());
		assertEquals(Duration.ofMinutes(2), request.reportBackDuration());
		assertEquals(List.of("ess_active_power", "grid_active_power"), request.rIds());
	}

	@Test
	public void testBuildQueryRegistrationRoundTrip() throws Exception {
		var xml = OadrMessages.queryRegistration("req-1");
		assertTrue(xml.contains(OadrNamespaces.OADR));
		assertTrue(xml.contains(OadrNamespaces.PYLD));
		var root = OadrXml.parse(xml);
		assertEquals("oadrQueryRegistration", OadrMessages.messageName(root));
		var m = OadrMessages.message(root);
		assertEquals(OadrNamespaces.OADR, m.getNamespaceURI());
		assertEquals("2.0b", m.getAttributeNS(OadrNamespaces.EI, "schemaVersion"));
		assertEquals("req-1", OadrMessages.requestId(root).get());
	}

	@Test
	public void testBuildCreatePartyRegistrationRoundTrip() throws Exception {
		var root = OadrXml.parse(OadrMessages.createPartyRegistration("req-2", "my-ven", "ven-1", "reg-1"));
		var m = OadrMessages.message(root);
		assertEquals("oadrCreatePartyRegistration", OadrXml.localName(m));
		assertEquals("my-ven", childText(m, "oadrVenName").get());
		assertEquals("ven-1", childText(m, "venID").get());
		assertEquals("reg-1", childText(m, "registrationID").get());
		assertEquals("2.0b", descendantText(m, "oadrProfileName").get());
		assertEquals("simpleHttp", descendantText(m, "oadrTransportName").get());
		assertEquals("true", childText(m, "oadrHttpPullModel").get());
		assertEquals(OadrNamespaces.EI, child(m, "venID").get().getNamespaceURI());
	}

	@Test
	public void testBuildCreatePartyRegistrationWithoutIds() throws Exception {
		var root = OadrXml.parse(OadrMessages.createPartyRegistration("req-3", "my-ven", "", null));
		var m = OadrMessages.message(root);
		assertTrue(childText(m, "venID").isEmpty());
		assertTrue(childText(m, "registrationID").isEmpty());
	}

	@Test
	public void testBuildPollRoundTrip() throws Exception {
		var root = OadrXml.parse(OadrMessages.poll("ven-1"));
		assertEquals("oadrPoll", OadrMessages.messageName(root));
		assertEquals("ven-1", childText(OadrMessages.message(root), "venID").get());
	}

	@Test
	public void testBuildCreatedEventRoundTrip() throws Exception {
		var responses = List.of(//
				new EventResponse("evt-1", 0, "req-dist-1", "optIn"), //
				new EventResponse("evt-2", 3, "req-dist-1", "optOut"));
		var root = OadrXml.parse(OadrMessages.createdEvent("req-4", "ven-1", responses));
		assertEquals("oadrCreatedEvent", OadrMessages.messageName(root));
		var m = OadrMessages.message(root);
		var created = child(m, "eiCreatedEvent").get();
		assertEquals(200, OadrMessages.parseResponse(root).responseCode());
		assertEquals("ven-1", childText(created, "venID").get());

		var eventResponses = children(child(created, "eventResponses").get(), "eventResponse").toList();
		assertEquals(2, eventResponses.size());
		var first = eventResponses.get(0);
		assertEquals("req-dist-1", childText(first, "requestID").get());
		assertEquals("optIn", childText(first, "optType").get());
		var qualified = child(first, "qualifiedEventID").get();
		assertEquals("evt-1", childText(qualified, "eventID").get());
		assertEquals("0", childText(qualified, "modificationNumber").get());
		var second = eventResponses.get(1);
		assertEquals("optOut", childText(second, "optType").get());
		assertEquals("3", descendantText(second, "modificationNumber").get());
	}

	@Test
	public void testBuildRegisterReportRoundTrip() throws Exception {
		var points = List.of(//
				new ReportPoint("ess_active_power", "ESS"), //
				new ReportPoint("evcs_active_power", "EVCS"), //
				new ReportPoint("grid_active_power", "Grid"));
		var root = OadrXml.parse(OadrMessages.registerReport("req-5", "ven-1", "openems_telemetry_usage",
				Instant.parse("2026-09-15T12:00:00Z"), points, Duration.ofMinutes(1)));
		assertEquals("oadrRegisterReport", OadrMessages.messageName(root));
		var m = OadrMessages.message(root);
		var report = child(m, "oadrReport").get();
		assertEquals("openems_telemetry_usage", childText(report, "reportSpecifierID").get());
		assertEquals("METADATA_TELEMETRY_USAGE", childText(report, "reportName").get());
		var descriptions = children(report, "oadrReportDescription").toList();
		assertEquals(3, descriptions.size());
		assertEquals(List.of("ess_active_power", "evcs_active_power", "grid_active_power"),
				descriptions.stream().map(d -> childText(d, "rID").get()).toList());
		assertEquals("PT1M", descendantText(descriptions.get(0), "oadrMinPeriod").get());
		assertEquals("ven-1", childText(m, "venID").get());
	}

	@Test
	public void testBuildUpdateReportRoundTrip() throws Exception {
		var root = OadrXml.parse(OadrMessages.updateReport("req-6", "ven-1", "rr-1", "openems_telemetry_usage",
				Instant.parse("2026-09-15T12:00:00Z"), Duration.ofMinutes(1),
				Map.of("ess_active_power", 1500.0, "grid_active_power", -250.5)));
		assertEquals("oadrUpdateReport", OadrMessages.messageName(root));
		var m = OadrMessages.message(root);
		var report = child(m, "oadrReport").get();
		assertEquals("rr-1", childText(report, "reportRequestID").get());
		assertEquals("TELEMETRY_USAGE", childText(report, "reportName").get());
		var interval = descendant(report, "interval").get();
		assertEquals("2026-09-15T12:00:00Z", descendantText(interval, "date-time").get());
		var payloads = children(interval, "oadrReportPayload").toList();
		assertEquals(2, payloads.size());
		var byRid = payloads.stream().collect(java.util.stream.Collectors.toMap(//
				p -> childText(p, "rID").get(), //
				p -> Double.parseDouble(descendantText(p, "value").get())));
		assertEquals(1500.0, byRid.get("ess_active_power"), 0.001);
		assertEquals(-250.5, byRid.get("grid_active_power"), 0.001);
	}

	@Test
	public void testBuildCreateOptRoundTrip() throws Exception {
		var event = OadrMessages.parseDistributeEvent(Fixtures.parse(Fixtures.DISTRIBUTE_EVENT_SIMPLE)).events()
				.get(0);
		var root = OadrXml.parse(OadrMessages.createOpt("req-7", "ven-1", "opt-1", "optOut", event,
				Instant.parse("2026-09-15T11:59:00Z")));
		assertEquals("oadrCreateOpt", OadrMessages.messageName(root));
		var m = OadrMessages.message(root);
		assertEquals("opt-1", childText(m, "optID").get());
		assertEquals("optOut", childText(m, "optType").get());
		assertEquals("http://openems.io/marketcontext/us-grid", childText(m, "marketContext").get());
		assertEquals("ven-1", childText(m, "venID").get());
		assertEquals("req-7", childText(m, "requestID").get());
		var qualified = child(m, "qualifiedEventID").get();
		assertEquals("evt-simple-1", childText(qualified, "eventID").get());
		assertEquals("0", childText(qualified, "modificationNumber").get());
	}

	@Test
	public void testBuildCancelPartyRegistrationRoundTrip() throws Exception {
		var root = OadrXml.parse(OadrMessages.cancelPartyRegistration("req-8", "reg-1", "ven-1"));
		assertEquals("oadrCancelPartyRegistration", OadrMessages.messageName(root));
		var m = OadrMessages.message(root);
		assertEquals("reg-1", childText(m, "registrationID").get());
		assertEquals("ven-1", childText(m, "venID").get());
		assertEquals("req-8", childText(m, "requestID").get());
	}

	@Test
	public void testBuildResponseRoundTrip() throws Exception {
		var root = OadrXml.parse(OadrMessages.response("req-9", "ven-1", 200, "OK"));
		assertEquals("oadrResponse", OadrMessages.messageName(root));
		var response = OadrMessages.parseResponse(root);
		assertTrue(response.isOk());
		assertEquals("req-9", response.requestId());
	}

	@Test
	public void testPriceFactorToMwh() throws Exception {
		assertEquals(1.0, OadrMessages.priceFactorToMwh(signalWithItemBase(//
				"<emix:itemBase><ei:currencyPerMWh><ei:itemDescription>currencyPerMWh</ei:itemDescription>"
						+ "<ei:itemUnits>USD</ei:itemUnits></ei:currencyPerMWh></emix:itemBase>")),
				0.001);
		assertEquals(1000.0, OadrMessages.priceFactorToMwh(signalWithItemBase(//
				"<emix:itemBase><ei:currencyPerKWh><ei:itemDescription>currencyPerKWh</ei:itemDescription>"
						+ "<ei:itemUnits>USD</ei:itemUnits></ei:currencyPerKWh></emix:itemBase>")),
				0.001);
		assertEquals(1.0, OadrMessages.priceFactorToMwh(signalWithItemBase("")), 0.001);
	}

	private static org.w3c.dom.Element signalWithItemBase(String itemBase) throws Exception {
		return OadrXml.parse("<ei:eiEventSignal xmlns:ei=\"" + OadrNamespaces.EI + "\" xmlns:emix=\""
				+ OadrNamespaces.EMIX + "\">" + itemBase + "</ei:eiEventSignal>");
	}
}
