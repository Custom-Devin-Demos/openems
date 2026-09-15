package io.openems.edge.timedata.influxdb;

import static io.openems.common.channel.PersistencePriority.MEDIUM;
import static io.openems.shared.influxdb.QueryLanguageConfig.INFLUX_QL;
import static org.junit.Assert.assertEquals;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TreeMap;

import org.junit.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

import io.openems.common.types.ChannelAddress;

import io.openems.common.oem.DummyOpenemsEdgeOem;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.common.test.DummyCycle;

public class TimedataInfluxDbImplTest {

	@Test
	public void test() throws Exception {
		new ComponentTest(new TimedataInfluxDbImpl()) //
				.addReference("componentManager", new DummyComponentManager()) //
				.addReference("cycle", new DummyCycle(1000)) //
				.addReference("oem", new DummyOpenemsEdgeOem()) //
				.activate(MyConfig.create() //
						.setId("influx0") //
						.setQueryLanguage(INFLUX_QL) //
						.setUrl("http://localhost:8086") //
						.setOrg("-") //
						.setApiKey("username:password") //
						.setBucket("database/retentionPolicy") //
						.setMeasurement("data") //
						.setNoOfCycles(1) //
						.setMaxQueueSize(5000) //
						.setReadOnly(false) //
						.setPersistencePriority(MEDIUM) //
						.build()) //
				.next(new TestCase()) //
		;
	}

	@Test
	public void testBuildHistoricPoints() {
		var address = new ChannelAddress("_sum", "ConsumptionActiveEnergy");
		var t0 = ZonedDateTime.of(2024, 3, 1, 8, 0, 0, 0, ZoneId.of("UTC"));
		var data = new TreeMap<ZonedDateTime, JsonElement>();
		data.put(t0, new JsonPrimitive(125));
		data.put(t0.plusMinutes(15), new JsonPrimitive(255.5));
		data.put(t0.plusMinutes(30), new JsonPrimitive(true));
		data.put(t0.plusMinutes(45), JsonNull.INSTANCE);
		data.put(t0.plusMinutes(60), new JsonPrimitive("text"));

		var points = TimedataInfluxDbImpl.buildHistoricPoints("data", address, data);

		assertEquals(4, points.size());
		assertEquals("data _sum/ConsumptionActiveEnergy=125i 1709280000000", points.get(0).toLineProtocol());
		assertEquals("data _sum/ConsumptionActiveEnergy=255.5 1709280900000", points.get(1).toLineProtocol());
		assertEquals("data _sum/ConsumptionActiveEnergy=1i 1709281800000", points.get(2).toLineProtocol());
		assertEquals("data _sum/ConsumptionActiveEnergy=\"text\" 1709283600000", points.get(3).toLineProtocol());
	}

}
