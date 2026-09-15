package io.openems.backend.openadr.vtn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class VtnRegistryTest {

	private static final Instant NOW = Instant.parse("2025-01-01T12:00:00Z");

	@Test
	void registerAssignsIdentifiers() {
		var registry = new VtnRegistry(Clock.fixed(NOW, ZoneOffset.UTC));
		var ven = registry.register("VEN", null);
		assertTrue(ven.venId().matches("ven-[0-9a-f]{8}"));
		assertTrue(ven.registrationId().startsWith("reg-"));
	}

	@Test
	void registerReusesKnownVen() {
		var registry = new VtnRegistry();
		var first = registry.register("VEN", "ven-fixed");
		assertEquals(first, registry.register("Changed", "ven-fixed"));
	}

	@Test
	void recordsPollForKnownVen() {
		var registry = new VtnRegistry(Clock.fixed(NOW, ZoneOffset.UTC));
		var ven = registry.register("VEN", null);
		registry.recordPoll(ven.venId());
		assertEquals(NOW, ven.lastPoll().get());
	}

	@Test
	void eventLifecycleFollowsClock() throws Exception {
		var clock = new MutableClock(NOW);
		var registry = new VtnRegistry(clock);
		var event = registry.createEvent(request(NOW.plusSeconds(3600), null));
		assertEquals(EventStatus.FAR, event.getStatus(clock.instant()));
		clock.set(NOW.plusSeconds(3000));
		assertEquals(EventStatus.NEAR, event.getStatus(clock.instant()));
		clock.set(NOW.plusSeconds(3600));
		assertEquals(EventStatus.ACTIVE, event.getStatus(clock.instant()));
		clock.set(NOW.plusSeconds(5400));
		assertEquals(EventStatus.COMPLETED, event.getStatus(clock.instant()));
	}

	@Test
	void cancelUpdatesEventAndUnknownReturnsFalse() throws Exception {
		var registry = new VtnRegistry(Clock.fixed(NOW, ZoneOffset.UTC));
		var event = registry.createEvent(request(NOW, null));
		assertTrue(registry.cancelEvent(event.eventId()));
		assertTrue(event.cancelled());
		assertEquals(1, event.modificationNumber());
		assertFalse(registry.cancelEvent("missing"));
	}

	@Test
	void targetedAndBroadcastEventsAreFiltered() throws Exception {
		var registry = new VtnRegistry(Clock.fixed(NOW, ZoneOffset.UTC));
		var targeted = registry.createEvent(request(NOW, "ven-a"));
		var broadcast = registry.createEvent(request(NOW, null));
		var forA = registry.getEventsForVen("ven-a", NOW);
		var forB = registry.getEventsForVen("ven-b", NOW);
		assertTrue(forA.contains(targeted));
		assertTrue(forA.contains(broadcast));
		assertFalse(forB.contains(targeted));
		assertTrue(forB.contains(broadcast));
	}

	@Test
	void cancelledEventIsDeliveredOncePerVen() throws Exception {
		var registry = new VtnRegistry(Clock.fixed(NOW, ZoneOffset.UTC));
		var event = registry.createEvent(request(NOW, null));
		registry.cancelEvent(event.eventId());
		assertEquals(1, registry.getEventsForVen("ven-a", NOW).size());
		assertTrue(registry.getEventsForVen("ven-a", NOW).isEmpty());
		assertEquals(1, registry.getEventsForVen("ven-b", NOW).size());
	}

	@Test
	void recordsOptResponse() throws Exception {
		var registry = new VtnRegistry(Clock.fixed(NOW, ZoneOffset.UTC));
		var event = registry.createEvent(request(NOW, null));
		registry.recordOptResponse(event.eventId(), "ven-a", OptType.OPT_OUT);
		assertEquals(OptType.OPT_OUT, event.optResponses().get("ven-a"));
	}

	@Test
	void recordsLatestReport() {
		var registry = new VtnRegistry();
		registry.recordReport("ven-a", "request", "specifier", Map.of("power", 12.5), NOW);
		assertEquals(12.5, registry.getReports().getFirst().values().get("power"));
	}

	@Test
	void createEventRequestAcceptsNow() throws Exception {
		var json = eventJson("SIMPLE", 2, null, 30);
		assertEquals(NOW, CreateEventRequest.fromJson(json, NOW).start());
	}

	@Test
	void createEventRequestRejectsInvalidLevel() {
		assertThrows(Exception.class, () -> CreateEventRequest.fromJson(eventJson("SIMPLE", 7, null, 30), NOW));
	}

	@Test
	void createEventRequestRejectsMissingDuration() {
		var json = eventJson("SIMPLE", 2, null, 30);
		json.remove("durationMinutes");
		assertThrows(Exception.class, () -> CreateEventRequest.fromJson(json, NOW));
	}

	@Test
	void createEventRequestRejectsPriceWithoutPrice() {
		assertThrows(Exception.class, () -> CreateEventRequest.fromJson(eventJson("PRICE", null, null, 30), NOW));
	}

	private static CreateEventRequest request(Instant start, String venId) {
		return new CreateEventRequest(venId, SignalType.SIMPLE, 2, null, start, 30, null, 0);
	}

	private static JsonObject eventJson(String type, Integer level, Double price, int duration) {
		var json = new JsonObject();
		json.addProperty("signalType", type);
		if (level != null) {
			json.addProperty("level", level);
		}
		if (price != null) {
			json.addProperty("price", price);
		}
		json.addProperty("start", "now");
		json.addProperty("durationMinutes", duration);
		return json;
	}

	private static final class MutableClock extends Clock {
		private Instant instant;

		private MutableClock(Instant instant) {
			this.instant = instant;
		}

		private void set(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return this.instant;
		}
	}
}
