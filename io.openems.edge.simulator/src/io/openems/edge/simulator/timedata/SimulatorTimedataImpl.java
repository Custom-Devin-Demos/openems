package io.openems.edge.simulator.timedata;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

import io.openems.common.exceptions.NotImplementedException;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.timedata.Resolution;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.simulator.CsvUtils;
import io.openems.edge.simulator.DataContainer;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.Timeranges;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Simulator.Timedata", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
@EventTopics({ //
		EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE //
})
public class SimulatorTimedataImpl extends AbstractOpenemsComponent
		implements SimulatorTimedata, Timedata, OpenemsComponent {

	@Reference
	private ConfigurationAdmin cm;

	private Config config;

	/** Values imported via {@link #writeHistoricData}; take precedence over CSV. */
	private final SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> historicData = new TreeMap<>();

	public SimulatorTimedataImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Timedata.ChannelId.values(), //
				SimulatorTimedata.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws IOException {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricData(String edgeId,
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution)
			throws OpenemsNamedException {
		try {
			// CSV is optional if data was imported via writeHistoricData()
			var csvFile = this.getPath();
			var data = csvFile.isFile() ? CsvUtils.readCsvFile(csvFile, this.config.format(), 1) : null;
			if (data == null && this.historicData.isEmpty()) {
				throw new IOException("Timedata CSV file [" + csvFile + "] not found");
			}
			SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> result = new TreeMap<>();
			var time = fromDate;
			while (time.isBefore(toDate)) {
				var next = time.plusSeconds(resolution.toSeconds());
				// read Channel values
				SortedMap<ChannelAddress, JsonElement> timeMap = new TreeMap<>();
				for (ChannelAddress channel : channels) {
					var imported = this.getHistoricValue(time, next, channel);
					if (imported != null) {
						timeMap.put(channel, imported);
					} else if (data != null) {
						timeMap.put(channel, getValueAsJson(data, channel));
					} else {
						timeMap.put(channel, JsonNull.INSTANCE);
					}
				}

				// add to result
				result.put(time, timeMap);

				// prepare next time + data
				time = next;
				if (data != null) {
					data.nextRecord();
				}
			}
			return result;
		} catch (NumberFormatException | IOException e) {
			e.printStackTrace();
			throw new OpenemsException(e.getMessage());
		}
	}

	@Override
	public synchronized void writeHistoricData(ChannelAddress address, SortedMap<ZonedDateTime, JsonElement> data)
			throws OpenemsNamedException {
		for (var entry : data.entrySet()) {
			this.historicData //
					.computeIfAbsent(entry.getKey(), t -> new TreeMap<>()) //
					.put(address, entry.getValue());
		}
	}

	/**
	 * Gets the imported historic values of the given {@link ChannelAddress}.
	 *
	 * @param address the {@link ChannelAddress}
	 * @return the values by timestamp; empty if nothing was imported
	 */
	public synchronized SortedMap<ZonedDateTime, JsonElement> getHistoricData(ChannelAddress address) {
		SortedMap<ZonedDateTime, JsonElement> result = new TreeMap<>();
		for (var entry : this.historicData.entrySet()) {
			var value = entry.getValue().get(address);
			if (value != null) {
				result.put(entry.getKey(), value);
			}
		}
		return result;
	}

	/**
	 * Gets the first imported value of the channel in [from, to).
	 *
	 * @param from    the start timestamp (inclusive)
	 * @param to      the end timestamp (exclusive)
	 * @param address the {@link ChannelAddress}
	 * @return the value or null
	 */
	private synchronized JsonElement getHistoricValue(ZonedDateTime from, ZonedDateTime to, ChannelAddress address) {
		for (var perTime : this.historicData.subMap(from, to).values()) {
			var value = perTime.get(address);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	/**
	 * Gets the energy of an imported cumulative channel in [from, to) as the
	 * difference between the last and the first value.
	 *
	 * @param from    the start timestamp (inclusive)
	 * @param to      the end timestamp (exclusive)
	 * @param address the {@link ChannelAddress}
	 * @return the energy or null if no numeric values were imported
	 */
	private synchronized JsonElement getHistoricEnergy(ZonedDateTime from, ZonedDateTime to, ChannelAddress address) {
		Double first = null;
		Double last = null;
		for (var perTime : this.historicData.subMap(from, to).values()) {
			var value = perTime.get(address);
			if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
				continue;
			}
			if (first == null) {
				first = value.getAsDouble();
			}
			last = value.getAsDouble();
		}
		if (first == null) {
			return null;
		}
		return new JsonPrimitive(last - first);
	}

	@Override
	public SortedMap<ChannelAddress, JsonElement> queryHistoricEnergy(String edgeId, ZonedDateTime fromDate,
			ZonedDateTime toDate, Set<ChannelAddress> channels) throws OpenemsNamedException {
		try {
			var csvFile = this.getPath();
			var data = csvFile.isFile() ? CsvUtils.readCsvFile(csvFile, this.config.format(), 1) : null;
			if (data == null && this.historicData.isEmpty()) {
				throw new IOException("Timedata CSV file [" + csvFile + "] not found");
			}
			SortedMap<ChannelAddress, JsonElement> result = new TreeMap<>();
			for (ChannelAddress channel : channels) {
				var imported = this.getHistoricEnergy(fromDate, toDate, channel);
				if (imported != null) {
					result.put(channel, imported);
				} else if (data != null) {
					result.put(channel, getValueAsJson(data, channel));
				} else {
					result.put(channel, JsonNull.INSTANCE);
				}
			}
			return result;
		} catch (NumberFormatException | IOException e) {
			e.printStackTrace();
			throw new OpenemsException(e.getMessage());
		}
	}

	@Override
	public SortedMap<ZonedDateTime, SortedMap<ChannelAddress, JsonElement>> queryHistoricEnergyPerPeriod(String edgeId,
			ZonedDateTime fromDate, ZonedDateTime toDate, Set<ChannelAddress> channels, Resolution resolution)
			throws OpenemsNamedException {
		throw new NotImplementedException("QueryHistoryEnergyPerPeriod is not implemented for Simulator");
	}

	@Override
	public SortedMap<Long, SortedMap<ChannelAddress, JsonElement>> queryResendData(ZonedDateTime fromDate,
			ZonedDateTime toDate, Set<ChannelAddress> channels) throws OpenemsNamedException {
		throw new NotImplementedException("QueryResendData is not implemented for Simulator-App");
	}

	/**
	 * Gets the value of the record for the given Channel-Address as Json.
	 *
	 * @param data    the {@link DataContainer}
	 * @param channel the {@link ChannelAddress}
	 * @return the value as JsonElement
	 */
	private static JsonElement getValueAsJson(DataContainer data, ChannelAddress channel) {
		var value = data.getValue(channel.toString());
		if (value.isPresent()) {
			return new JsonPrimitive(value.get());
		}
		return JsonNull.INSTANCE;
	}

	/**
	 * Gets the path to the timedata CSV file.
	 *
	 * @return the absolute path
	 */
	private File getPath() {
		return new File(System.getProperty("user.home"), this.config.filename());
	}

	@Override
	public CompletableFuture<Optional<Object>> getLatestValue(ChannelAddress channelAddress) {
		// TODO implement this method
		return CompletableFuture.completedFuture(Optional.empty());
	}

	@Override
	public Timeranges getResendTimeranges(ChannelAddress notSendChannel, long lastResendTimestamp)
			throws OpenemsNamedException {
		throw new NotImplementedException("GetResendTimeranges is not implemented for Simulator-App");
	}

}
