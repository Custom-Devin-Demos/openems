package io.openems.edge.timedata.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

import org.junit.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.timedata.Resolution;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.test.AbstractDummyOpenemsComponent;
import io.openems.edge.timedata.test.DummyTimedata;

public class TimedataWriteHistoricDataTest {

	private static final ChannelAddress ADDRESS = new ChannelAddress("_sum", "ConsumptionActiveEnergy");

	/**
	 * A {@link Timedata} that only implements the abstract methods, so the default
	 * {@link Timedata#writeHistoricData} applies.
	 */
	private static class ReadOnlyTimedata extends AbstractDummyOpenemsComponent<ReadOnlyTimedata>
			implements Timedata {

		private ReadOnlyTimedata(String id) {
			super(id, OpenemsComponent.ChannelId.values(), Timedata.ChannelId.values());
		}

		@Override
		protected ReadOnlyTimedata self() {
			return this;
		}

		@Override
		public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricData(String edgeId,
				ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution) {
			return new TreeMap<>();
		}

		@Override
		public SortedMap<ChannelAddress, JsonElement> queryHistoricEnergy(String edgeId, ZonedDateTime fromDate,
				ZonedDateTime toDate, Set<ChannelAddress> channels) {
			return new TreeMap<>();
		}

		@Override
		public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricEnergyPerPeriod(
				String edgeId, ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels,
				Resolution resolution) {
			return new TreeMap<>();
		}

		@Override
		public SortedMap<Long, SortedMap<ChannelAddress, JsonElement>> queryResendData(ZonedDateTime fromDate,
				ZonedDateTime toDate, Set<ChannelAddress> channels) {
			return new TreeMap<>();
		}

		@Override
		public CompletableFuture<Optional<Object>> getLatestValue(ChannelAddress channelAddress) {
			return CompletableFuture.completedFuture(Optional.empty());
		}

		@Override
		public Timeranges getResendTimeranges(ChannelAddress notSendChannel, long lastResendTimestamp) {
			return new Timeranges();
		}
	}

	@Test
	public void testDefaultThrowsUnsupported() {
		var timedata = new ReadOnlyTimedata("timedata0");
		var data = new TreeMap<ZonedDateTime, JsonElement>();
		data.put(ZonedDateTime.now(), new JsonPrimitive(1));

		var e = assertThrows(OpenemsNamedException.class, () -> timedata.writeHistoricData(ADDRESS, data));
		assertEquals("Historic write not supported by timedata0", e.getMessage());
	}

	@Test
	public void testDummyTimedataStoresWrittenValues() throws OpenemsNamedException {
		var timedata = new DummyTimedata("timedata0");
		var t0 = ZonedDateTime.of(2024, 3, 1, 8, 0, 0, 0, ZoneId.of("UTC"));
		var data = new TreeMap<ZonedDateTime, JsonElement>();
		data.put(t0, new JsonPrimitive(125));
		data.put(t0.plusMinutes(15), new JsonPrimitive(255));

		timedata.writeHistoricData(ADDRESS, data);

		assertEquals(data, timedata.getValues(ADDRESS));
		assertEquals(0, timedata.getValues(new ChannelAddress("_sum", "GridActivePower")).size());
	}
}
