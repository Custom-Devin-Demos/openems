package io.openems.edge.controller.api.openadr.oadr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.Test;

import io.openems.edge.controller.api.openadr.oadr.OadrEvent.Interval;

public class OadrEventTest {

	private static final Instant START = Instant.parse("2026-09-15T12:00:00Z");

	private static OadrEvent event(String status, Duration rampUp, Duration recovery) {
		return new OadrEvent("evt-1", 0, status, 1, "", START, START, Duration.ofHours(1), Duration.ZERO,
				Duration.ZERO, rampUp, recovery, SignalType.SIMPLE, "simple", "sig-1", null, List.of(//
						new Interval(START, Duration.ofMinutes(30), "0", 2), //
						new Interval(START.plus(Duration.ofMinutes(30)), Duration.ofMinutes(30), "1", 3)),
				"always", false, List.of());
	}

	@Test
	public void testPhaseWithRampUpAndRecovery() {
		var e = event("far", Duration.ofMinutes(10), Duration.ofMinutes(15));
		assertEquals(EventPhase.BEFORE, e.phaseAt(START.minus(Duration.ofMinutes(11))));
		assertEquals(EventPhase.RAMP_UP, e.phaseAt(START.minus(Duration.ofMinutes(10))));
		assertEquals(EventPhase.RAMP_UP, e.phaseAt(START.minusSeconds(1)));
		assertEquals(EventPhase.ACTIVE, e.phaseAt(START));
		assertEquals(EventPhase.ACTIVE, e.phaseAt(START.plus(Duration.ofMinutes(59))));
		assertEquals(EventPhase.RECOVERY, e.phaseAt(START.plus(Duration.ofHours(1))));
		assertEquals(EventPhase.RECOVERY, e.phaseAt(START.plus(Duration.ofMinutes(74))));
		assertEquals(EventPhase.AFTER, e.phaseAt(START.plus(Duration.ofMinutes(75))));

		assertFalse(EventPhase.BEFORE.isCurtailing());
		assertTrue(EventPhase.RAMP_UP.isCurtailing());
		assertTrue(EventPhase.ACTIVE.isCurtailing());
		assertTrue(EventPhase.RECOVERY.isCurtailing());
		assertFalse(EventPhase.AFTER.isCurtailing());
	}

	@Test
	public void testPhaseWithoutRamps() {
		var e = event("active", Duration.ZERO, Duration.ZERO);
		assertEquals(EventPhase.BEFORE, e.phaseAt(START.minusSeconds(1)));
		assertEquals(EventPhase.ACTIVE, e.phaseAt(START));
		assertEquals(EventPhase.AFTER, e.phaseAt(START.plus(Duration.ofHours(1))));
		assertEquals(START.plus(Duration.ofHours(1)), e.end());
		assertEquals(START, e.rampUpStart());
		assertEquals(e.end(), e.recoveryEnd());
	}

	@Test
	public void testCancelledAndCompletedAreAfter() {
		assertTrue(event("cancelled", Duration.ZERO, Duration.ZERO).isCancelled());
		assertEquals(EventPhase.AFTER, event("cancelled", Duration.ZERO, Duration.ZERO).phaseAt(START));
		assertEquals(EventPhase.AFTER, event("completed", Duration.ZERO, Duration.ZERO).phaseAt(START));
		assertFalse(event("completed", Duration.ZERO, Duration.ZERO).isCancelled());
	}

	@Test
	public void testValueAt() {
		var e = event("far", Duration.ofMinutes(10), Duration.ofMinutes(10));
		// ramp-up uses the first interval
		assertEquals(2, e.valueAt(START.minus(Duration.ofMinutes(5))), 0.001);
		assertEquals(2, e.valueAt(START), 0.001);
		assertEquals(2, e.valueAt(START.plus(Duration.ofMinutes(29))), 0.001);
		assertEquals(3, e.valueAt(START.plus(Duration.ofMinutes(30))), 0.001);
		// recovery uses the last interval
		assertEquals(3, e.valueAt(START.plus(Duration.ofMinutes(65))), 0.001);
	}

	@Test
	public void testValueAtWithoutIntervalsUsesCurrentValue() {
		var e = new OadrEvent("evt-2", 0, "active", 0, "", START, START, Duration.ofHours(1), Duration.ZERO,
				Duration.ZERO, Duration.ZERO, Duration.ZERO, SignalType.PRICE, "ELECTRICITY_PRICE", "sig", 120.0,
				List.of(), "always", false, List.of());
		assertEquals(120.0, e.valueAt(START), 0.001);
	}

	@Test
	public void testTargets() {
		var all = event("far", Duration.ZERO, Duration.ZERO);
		assertTrue(all.targets("ven-1"));
		assertTrue(all.targets(null));

		var targeted = new OadrEvent("evt-3", 0, "far", 0, "", START, START, Duration.ofHours(1), Duration.ZERO,
				Duration.ZERO, Duration.ZERO, Duration.ZERO, SignalType.SIMPLE, "simple", "sig", null, List.of(),
				"always", false, List.of("ven-1", "ven-2"));
		assertTrue(targeted.targets("ven-1"));
		assertFalse(targeted.targets("ven-3"));
		// unknown own venID: accept
		assertTrue(targeted.targets(null));
	}

	@Test
	public void testSignalTypeFrom() {
		assertEquals(SignalType.SIMPLE, SignalType.from("simple", "level"));
		assertEquals(SignalType.SIMPLE, SignalType.from("SIMPLE", null));
		assertEquals(SignalType.PRICE, SignalType.from("ELECTRICITY_PRICE", "price"));
		assertEquals(SignalType.PRICE, SignalType.from("anything", "price"));
		assertEquals(SignalType.UNKNOWN, SignalType.from("LOAD_DISPATCH", "setpoint"));
		assertEquals(SignalType.UNKNOWN, SignalType.from(null, null));
	}
}
