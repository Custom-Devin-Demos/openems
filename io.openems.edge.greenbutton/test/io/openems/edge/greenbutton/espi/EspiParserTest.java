package io.openems.edge.greenbutton.espi;

import static io.openems.edge.greenbutton.espi.Fixtures.COMED_HOURLY;
import static io.openems.edge.greenbutton.espi.Fixtures.MULTI_USAGEPOINT_COST;
import static io.openems.edge.greenbutton.espi.Fixtures.PGE_15MIN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;

public class EspiParserTest {

	private static final double DELTA = 0.0001;

	@Test
	public void testPge15MinuteIntervals() throws OpenemsNamedException {
		var document = EspiParser.parse(Fixtures.read(PGE_15MIN));

		assertEquals(1, document.series().size());
		var series = document.series().get(0);
		assertEquals(UnitOfMeasure.WATT_HOURS, series.type().uom());
		assertEquals(0, series.type().powerOfTenMultiplier());
		assertEquals(900, series.type().intervalLength());
		assertEquals(840, series.type().currency());
		assertEquals(ReadingType.ACCUMULATION_DELTA_DATA, series.type().accumulationBehaviour());
		assertEquals(8, series.readings().size());

		var first = series.readings().firstEntry().getValue();
		assertEquals(Instant.ofEpochSecond(1709280000), first.start());
		assertEquals(Duration.ofMinutes(15), first.duration());
		assertEquals(125d, first.value(), DELTA);
		assertNull(first.cost());
		assertNull(first.quality());
		assertEquals(500d, first.averagePower(), DELTA);

		assertEquals(132d, series.readings().lastEntry().getValue().value(), DELTA);
		assertEquals(1030d, series.cumulativeWh().lastEntry().getValue(), DELTA);
	}

	@Test
	public void testComedHourlyKwhWithDefaultNamespace() throws OpenemsNamedException {
		var document = EspiParser.parse(Fixtures.read(COMED_HOURLY));

		assertEquals(1, document.series().size());
		var series = document.series().get(0);
		assertEquals(3, series.type().powerOfTenMultiplier());
		assertEquals(3600, series.type().intervalLength());
		// two IntervalBlocks are merged into one series
		assertEquals(5, series.readings().size());
		var values = series.values();
		assertEquals(1000d, values.get(Instant.ofEpochSecond(1717218000)), DELTA);
		assertEquals(2000d, values.get(Instant.ofEpochSecond(1717221600)), DELTA);
		assertEquals(3000d, values.get(Instant.ofEpochSecond(1717304400)), DELTA);
		assertEquals(9000d, series.cumulativeWh().lastEntry().getValue(), DELTA);
		assertEquals(2000d, series.readings().get(Instant.ofEpochSecond(1717221600)).averagePower(), DELTA);
	}

	@Test
	public void testMultiUsagePointWithCostAndQuality() throws OpenemsNamedException {
		var document = EspiParser.parse(Fixtures.read(MULTI_USAGEPOINT_COST));

		assertEquals(2, document.series().size());
		assertEquals(3, document.readingCount());

		var consumption = document.series().get(0);
		assertEquals("/espi/1_1/resource/RetailCustomer/2/UsagePoint/10", consumption.usagePointId());
		assertTrue(consumption.type().isConsumption());
		var reading = consumption.readings().firstEntry().getValue();
		assertEquals(2000d, reading.value(), DELTA);
		assertEquals(Long.valueOf(150000), reading.cost());
		assertEquals(Integer.valueOf(0), reading.quality());
		assertEquals(Integer.valueOf(8), consumption.readings().lastEntry().getValue().quality());
		assertEquals(2, consumption.costs().size());

		var solar = document.series().get(1);
		assertEquals("/espi/1_1/resource/RetailCustomer/2/UsagePoint/11", solar.usagePointId());
		assertEquals(ReadingType.FLOW_DIRECTION_REVERSE, solar.type().flowDirection());
		assertTrue(!solar.type().isConsumption());
		assertEquals(-3, solar.type().powerOfTenMultiplier());
		assertEquals(500d, solar.readings().firstEntry().getValue().value(), DELTA);

		assertEquals(consumption, document.consumptionEnergySeries().orElseThrow());
	}

	@Test
	public void testMultiplierMinusThree() {
		var type = new ReadingType(UnitOfMeasure.WATT_HOURS, -3, 0, 0, 0, 0, 0);
		assertEquals(0.5, type.scale(500), DELTA);
	}

	@Test
	public void testMultiplierZero() {
		var type = new ReadingType(UnitOfMeasure.WATT_HOURS, 0, 0, 0, 0, 0, 0);
		assertEquals(500d, type.scale(500), DELTA);
	}

	@Test
	public void testMultiplierThree() {
		var type = new ReadingType(UnitOfMeasure.WATT_HOURS, 3, 0, 0, 0, 0, 0);
		assertEquals(500_000d, type.scale(500), DELTA);
	}

	@Test
	public void testUnitMapping() {
		assertEquals(UnitOfMeasure.WATT_HOURS, UnitOfMeasure.fromCode(72));
		assertEquals(UnitOfMeasure.WATT, UnitOfMeasure.fromCode(38));
		assertEquals(UnitOfMeasure.AMPERE_HOURS, UnitOfMeasure.fromCode(106));
		assertEquals(UnitOfMeasure.CUBIC_FEET, UnitOfMeasure.fromCode(119));
		assertEquals(UnitOfMeasure.VAR_HOURS, UnitOfMeasure.fromCode(73));
		assertEquals(UnitOfMeasure.UNKNOWN, UnitOfMeasure.fromCode(9999));

		assertTrue(UnitOfMeasure.WATT_HOURS.isEnergy());
		assertTrue(UnitOfMeasure.JOULE.isEnergy());
		assertTrue(!UnitOfMeasure.WATT.isEnergy());
		assertTrue(new ReadingType(UnitOfMeasure.WATT, 0, 0, 0, 0, 0, 0).isPower());
	}

	@Test
	public void testEnergyConversionToWattHours() {
		assertEquals(1d, UnitOfMeasure.JOULE.toWattHours(3600), DELTA);
		assertEquals(1000d, new ReadingType(UnitOfMeasure.JOULE, 3, 0, 0, 0, 0, 0).scale(3600), DELTA);
		assertEquals(0.29307107, UnitOfMeasure.BTU.toWattHours(1), DELTA);
		assertEquals(5d, new ReadingType(UnitOfMeasure.WATT, 0, 0, 0, 0, 0, 0).scale(5), DELTA);
	}

	@Test
	public void testLinkResolutionAssignsReadingTypePerMeterReading() throws OpenemsNamedException {
		var xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<feed xmlns="http://www.w3.org/2005/Atom" xmlns:espi="http://naesb.org/espi">
				  <entry>
				    <link rel="self" href="/r/ReadingType/A"/>
				    <content><espi:ReadingType><espi:uom>72</espi:uom><espi:powerOfTenMultiplier>0</espi:powerOfTenMultiplier></espi:ReadingType></content>
				  </entry>
				  <entry>
				    <link rel="self" href="/r/ReadingType/B"/>
				    <content><espi:ReadingType><espi:uom>72</espi:uom><espi:powerOfTenMultiplier>3</espi:powerOfTenMultiplier></espi:ReadingType></content>
				  </entry>
				  <entry>
				    <link rel="self" href="/r/UsagePoint/1/MeterReading/1"/>
				    <link rel="related" href="/r/UsagePoint/1/MeterReading/1/IntervalBlock"/>
				    <link rel="related" href="/r/ReadingType/B"/>
				    <content><espi:MeterReading/></content>
				  </entry>
				  <entry>
				    <link rel="self" href="/r/UsagePoint/1/MeterReading/2"/>
				    <link rel="related" href="/r/UsagePoint/1/MeterReading/2/IntervalBlock"/>
				    <link rel="related" href="/r/ReadingType/A"/>
				    <content><espi:MeterReading/></content>
				  </entry>
				  <entry>
				    <link rel="self" href="/r/UsagePoint/1/MeterReading/1/IntervalBlock/1"/>
				    <link rel="up" href="/r/UsagePoint/1/MeterReading/1/IntervalBlock"/>
				    <content><espi:IntervalBlock>
				      <espi:IntervalReading><espi:timePeriod><espi:duration>3600</espi:duration><espi:start>1000</espi:start></espi:timePeriod><espi:value>7</espi:value></espi:IntervalReading>
				    </espi:IntervalBlock></content>
				  </entry>
				  <entry>
				    <link rel="self" href="/r/UsagePoint/1/MeterReading/2/IntervalBlock/1"/>
				    <link rel="up" href="/r/UsagePoint/1/MeterReading/2/IntervalBlock"/>
				    <content><espi:IntervalBlock>
				      <espi:IntervalReading><espi:timePeriod><espi:duration>3600</espi:duration><espi:start>1000</espi:start></espi:timePeriod><espi:value>7</espi:value></espi:IntervalReading>
				    </espi:IntervalBlock></content>
				  </entry>
				</feed>
				""";
		var document = EspiParser.parse(xml);

		assertEquals(2, document.series().size());
		assertEquals(7000d, document.series().get(0).readings().firstEntry().getValue().value(), DELTA);
		assertEquals(7d, document.series().get(1).readings().firstEntry().getValue().value(), DELTA);
	}

	@Test
	public void testIntervalBlockWithoutMeterReadingUsesSingleReadingType() throws OpenemsNamedException {
		var xml = """
				<feed xmlns="http://www.w3.org/2005/Atom" xmlns:espi="http://naesb.org/espi">
				  <entry>
				    <link rel="self" href="/r/ReadingType/A"/>
				    <content><espi:ReadingType><espi:uom>72</espi:uom><espi:powerOfTenMultiplier>3</espi:powerOfTenMultiplier></espi:ReadingType></content>
				  </entry>
				  <entry>
				    <content><espi:IntervalBlock>
				      <espi:interval><espi:duration>1800</espi:duration><espi:start>0</espi:start></espi:interval>
				      <espi:IntervalReading><espi:timePeriod><espi:start>0</espi:start></espi:timePeriod><espi:value>1</espi:value></espi:IntervalReading>
				    </espi:IntervalBlock></content>
				  </entry>
				</feed>
				""";
		var document = EspiParser.parse(xml);

		assertEquals(1, document.series().size());
		var reading = document.series().get(0).readings().firstEntry().getValue();
		assertEquals(1000d, reading.value(), DELTA);
		// duration falls back to the IntervalBlock interval
		assertEquals(Duration.ofMinutes(30), reading.duration());
	}

	@Test
	public void testMalformedXml() {
		var e = assertThrows(OpenemsNamedException.class, () -> EspiParser.parse("<feed><entry></feed>"));
		assertTrue(e.getMessage().startsWith("Unable to parse ESPI document"));
	}

	@Test
	public void testNotAFeed() {
		var e = assertThrows(OpenemsNamedException.class,
				() -> EspiParser.parse("<espi:IntervalBlock xmlns:espi=\"http://naesb.org/espi\"/>"));
		assertTrue(e.getMessage().contains("not an Atom <feed>"));
	}

	@Test
	public void testEmptyFeed() throws OpenemsNamedException {
		var document = EspiParser.parse("<feed xmlns=\"http://www.w3.org/2005/Atom\"><title>empty</title></feed>");
		assertTrue(document.series().isEmpty());
		assertEquals(0, document.readingCount());
		assertTrue(document.consumptionEnergySeries().isEmpty());
	}

	@Test
	public void testBlankDocument() {
		assertThrows(OpenemsNamedException.class, () -> EspiParser.parse("   "));
		assertThrows(OpenemsNamedException.class, () -> EspiParser.parse(null));
	}

	@Test
	public void testMissingValueIsRejected() {
		var xml = """
				<feed xmlns="http://www.w3.org/2005/Atom" xmlns:espi="http://naesb.org/espi">
				  <entry><content><espi:IntervalBlock>
				    <espi:IntervalReading><espi:timePeriod><espi:start>0</espi:start></espi:timePeriod></espi:IntervalReading>
				  </espi:IntervalBlock></content></entry>
				</feed>
				""";
		assertThrows(OpenemsNamedException.class, () -> EspiParser.parse(xml));
	}

	@Test
	public void testCumulativeRegisterValuesAreConvertedToPerInterval() {
		var type = new ReadingType(UnitOfMeasure.WATT_HOURS, 0, ReadingType.ACCUMULATION_CUMULATIVE, 1, 1, 3600, 0);
		var readings = new java.util.TreeMap<Instant, IntervalReading>();
		for (var i = 0; i < 3; i++) {
			var start = Instant.ofEpochSecond(i * 3600L);
			readings.put(start, new IntervalReading(start, Duration.ofHours(1), 1000 + i * 100, null, null));
		}
		var series = new IntervalSeries("", "", type, readings);

		var perInterval = series.perIntervalWh();
		assertEquals(2, perInterval.size());
		assertEquals(100d, perInterval.get(Instant.ofEpochSecond(3600)), DELTA);
		assertEquals(100d, perInterval.get(Instant.ofEpochSecond(7200)), DELTA);
		assertEquals(1200d, series.cumulativeWh().lastEntry().getValue(), DELTA);
	}
}
