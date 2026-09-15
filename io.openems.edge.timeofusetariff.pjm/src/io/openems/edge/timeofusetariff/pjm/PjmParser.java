package io.openems.edge.timeofusetariff.pjm;

import static io.openems.common.utils.JsonUtils.getAsDouble;
import static io.openems.common.utils.JsonUtils.getAsInt;
import static io.openems.common.utils.JsonUtils.getAsJsonArray;
import static io.openems.common.utils.JsonUtils.getAsJsonObject;
import static io.openems.common.utils.JsonUtils.getAsLong;
import static io.openems.common.utils.JsonUtils.getAsOptionalJsonArray;
import static io.openems.common.utils.JsonUtils.getAsOptionalString;
import static io.openems.common.utils.JsonUtils.getAsString;
import static io.openems.common.utils.JsonUtils.parseToJsonObject;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import com.google.common.collect.ImmutableSortedMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.timeofusetariff.api.TimeOfUsePrices;

/**
 * Parses PJM Data Miner 2 {@code da_hrl_lmps} JSON responses.
 */
public final class PjmParser {

	/** Timezone of PJM "Eastern Prevailing Time" (EPT) timestamps. */
	public static final ZoneId EPT = ZoneId.of("America/New_York");

	private static final DateTimeFormatter PJM_TIMESTAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	/**
	 * One hourly day-ahead LMP item.
	 * 
	 * @param start      start of the hour
	 * @param pnodeId    the pricing node ID
	 * @param pnodeName  the pricing node name
	 * @param totalLmpDa the total day-ahead LMP in Currency/MWh
	 */
	public record Item(Instant start, long pnodeId, String pnodeName, double totalLmpDa) {
	}

	/**
	 * One page of a Data Miner 2 response.
	 * 
	 * @param items     the items of this page
	 * @param totalRows the total number of rows of the query
	 * @param nextLink  URL of the next page; null if this is the last page
	 */
	public record Page(List<Item> items, int totalRows, String nextLink) {
	}

	private PjmParser() {
	}

	/**
	 * Parses one page of a {@code da_hrl_lmps} response.
	 * 
	 * @param json the response body
	 * @return the {@link Page}
	 * @throws OpenemsNamedException on invalid JSON
	 */
	public static Page parsePage(String json) throws OpenemsNamedException {
		if (json == null || json.isBlank()) {
			throw new OpenemsException("Empty PJM response");
		}
		final var root = parseToJsonObject(json);
		final var items = new ArrayList<Item>();
		for (var element : getAsJsonArray(root, "items")) {
			var item = getAsJsonObject(element);
			items.add(new Item(//
					parseStart(item), //
					getAsLong(item, "pnode_id"), //
					getAsOptionalString(item, "pnode_name").orElse(""), //
					getAsDouble(item, "total_lmp_da")));
		}
		var totalRows = root.has("totalRows") ? getAsInt(root, "totalRows") : items.size();
		return new Page(items, totalRows, findNextLink(root));
	}

	private static String findNextLink(JsonObject root) throws OpenemsNamedException {
		var links = getAsOptionalJsonArray(root, "links");
		if (links.isEmpty()) {
			return null;
		}
		for (JsonElement element : links.get()) {
			var link = getAsJsonObject(element);
			if ("next".equals(getAsOptionalString(link, "rel").orElse(null))) {
				var href = getAsString(link, "href");
				return href.isBlank() ? null : href;
			}
		}
		return null;
	}

	/**
	 * Reads the start of the hour; prefers {@code datetime_beginning_utc} and
	 * falls back to {@code datetime_beginning_ept} interpreted in
	 * {@link #EPT}.
	 *
	 * @param item the JSON item
	 * @return the start {@link Instant}
	 * @throws OpenemsNamedException if neither timestamp is present or parseable
	 */
	private static Instant parseStart(JsonObject item) throws OpenemsNamedException {
		var utc = getAsOptionalString(item, "datetime_beginning_utc");
		if (utc.isPresent() && !utc.get().isBlank()) {
			return LocalDateTime.parse(utc.get(), PJM_TIMESTAMP).toInstant(ZoneOffset.UTC);
		}
		var ept = getAsString(item, "datetime_beginning_ept");
		return LocalDateTime.parse(ept, PJM_TIMESTAMP).atZone(EPT).toInstant();
	}

	/**
	 * Converts hourly items to quarter-hourly {@link TimeOfUsePrices}.
	 * 
	 * <p>
	 * Items are filtered by {@code pnodeId}; if {@code pnodeId} is not positive,
	 * items are filtered by {@code pnode_name} equal to {@code zone} instead.
	 * {@code ancillaryCostsPerMwh} is added to every price.
	 * 
	 * @param items                the hourly items
	 * @param pnodeId              the pricing node ID to keep; <= 0 to match by
	 *                             zone
	 * @param zone                 the pricing node name to keep if no pnodeId is
	 *                             given
	 * @param ancillaryCostsPerMwh costs added to every price
	 * @return the prices; {@link TimeOfUsePrices#EMPTY_PRICES} if no item matches
	 */
	public static TimeOfUsePrices toPrices(List<Item> items, long pnodeId, String zone,
			double ancillaryCostsPerMwh) {
		var result = new TreeMap<Instant, Double>();
		for (var item : items) {
			if (!matches(item, pnodeId, zone)) {
				continue;
			}
			var price = item.totalLmpDa() + ancillaryCostsPerMwh;
			var start = item.start().truncatedTo(HOURS);
			result.put(start, price);
			result.put(start.plus(15, MINUTES), price);
			result.put(start.plus(30, MINUTES), price);
			result.put(start.plus(45, MINUTES), price);
		}
		if (result.isEmpty()) {
			return TimeOfUsePrices.EMPTY_PRICES;
		}
		return TimeOfUsePrices.from(ImmutableSortedMap.copyOf(result));
	}

	private static boolean matches(Item item, long pnodeId, String zone) {
		if (pnodeId > 0) {
			return item.pnodeId() == pnodeId;
		}
		return zone != null && zone.equalsIgnoreCase(item.pnodeName());
	}

	/**
	 * Parses a single-page response directly to {@link TimeOfUsePrices}.
	 * 
	 * @param json                 the response body
	 * @param pnodeId              see {@link #toPrices(List, long, String, double)}
	 * @param zone                 see {@link #toPrices(List, long, String, double)}
	 * @param ancillaryCostsPerMwh see {@link #toPrices(List, long, String, double)}
	 * @return the prices
	 * @throws OpenemsNamedException on invalid JSON
	 */
	public static TimeOfUsePrices parsePrices(String json, long pnodeId, String zone, double ancillaryCostsPerMwh)
			throws OpenemsNamedException {
		return toPrices(parsePage(json).items(), pnodeId, zone, ancillaryCostsPerMwh);
	}
}
