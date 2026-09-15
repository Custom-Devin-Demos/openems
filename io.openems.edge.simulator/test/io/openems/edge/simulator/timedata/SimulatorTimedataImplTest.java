package io.openems.edge.simulator.timedata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.timedata.Resolution;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.simulator.CsvFormat;

public class SimulatorTimedataImplTest {

	private static final ChannelAddress CONSUMPTION = new ChannelAddress("_sum", "ConsumptionActiveEnergy");
	private static final ChannelAddress OTHER = new ChannelAddress("_sum", "GridActivePower");

	@Test
	void test() throws OpenemsException, Exception {
		new ComponentTest(new SimulatorTimedataImpl()) //
				.activate(MyConfig.create() //
						.setId("thermometer0") //
						.setFilename("") //
						.setFormat(CsvFormat.ENGLISH) //
						.build()) //
				.next(new TestCase()) //
				.deactivate();
	}

	private static SimulatorTimedataImpl activate() throws Exception {
		var sut = new SimulatorTimedataImpl();
		new ComponentTest(sut) //
				.activate(MyConfig.create() //
						.setId("timedata0") //
						.setFilename("") //
						.setFormat(CsvFormat.ENGLISH) //
						.build());
		return sut;
	}

	@Test
	void testQueryWithoutCsvAndWithoutImportFails() throws Exception {
		var sut = activate();
		var from = ZonedDateTime.of(2024, 3, 1, 0, 0, 0, 0, ZoneId.of("UTC"));
		assertThrows(OpenemsNamedException.class,
				() -> sut.queryHistoricData(null, from, from.plusHours(1), Set.of(CONSUMPTION), new Resolution(15,
						ChronoUnit.MINUTES)));
	}

	@Test
	void testHistoricWriteAndQueryRoundTrip() throws Exception {
		var from = ZonedDateTime.of(2024, 3, 1, 8, 0, 0, 0, ZoneId.of("UTC"));
		var data = new TreeMap<ZonedDateTime, JsonElement>();
		data.put(from, new JsonPrimitive(125));
		data.put(from.plusMinutes(15), new JsonPrimitive(255));
		data.put(from.plusMinutes(30), new JsonPrimitive(380));
		data.put(from.plusMinutes(45), new JsonPrimitive(510));

		var sut = activate();
		sut.writeHistoricData(CONSUMPTION, data);

		assertEquals(data, sut.getHistoricData(CONSUMPTION));
		assertTrue(sut.getHistoricData(OTHER).isEmpty());

		var result = sut.queryHistoricData(null, from, from.plusHours(1), Set.of(CONSUMPTION, OTHER),
				new Resolution(15, ChronoUnit.MINUTES));
		assertEquals(4, result.size());
		assertEquals(125, result.get(from).get(CONSUMPTION).getAsInt());
		assertEquals(510, result.get(from.plusMinutes(45)).get(CONSUMPTION).getAsInt());
		assertEquals(JsonNull.INSTANCE, result.get(from).get(OTHER));

		// coarser resolution returns the first imported value per period
		var hourly = sut.queryHistoricData(null, from, from.plusHours(2), Set.of(CONSUMPTION),
				new Resolution(1, ChronoUnit.HOURS));
		assertEquals(2, hourly.size());
		assertEquals(125, hourly.get(from).get(CONSUMPTION).getAsInt());
		assertEquals(JsonNull.INSTANCE, hourly.get(from.plusHours(1)).get(CONSUMPTION));

		var energy = sut.queryHistoricEnergy(null, from, from.plusHours(1), Set.of(CONSUMPTION, OTHER));
		assertEquals(385, energy.get(CONSUMPTION).getAsInt());
		assertEquals(JsonNull.INSTANCE, energy.get(OTHER));
	}

	@Test
	void testHistoricWriteOverwritesSameTimestamp() throws Exception {
		var sut = activate();
		var at = ZonedDateTime.of(2024, 3, 1, 8, 0, 0, 0, ZoneId.of("UTC"));
		var data = new TreeMap<ZonedDateTime, JsonElement>();
		data.put(at, new JsonPrimitive(1));
		sut.writeHistoricData(CONSUMPTION, data);
		data.put(at, new JsonPrimitive(2));
		sut.writeHistoricData(CONSUMPTION, data);

		assertEquals(1, sut.getHistoricData(CONSUMPTION).size());
		assertEquals(2, sut.getHistoricData(CONSUMPTION).get(at).getAsInt());
	}
}
