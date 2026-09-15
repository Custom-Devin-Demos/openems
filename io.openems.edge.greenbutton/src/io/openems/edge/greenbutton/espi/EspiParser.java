package io.openems.edge.greenbutton.espi;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.utils.XmlUtils;

/**
 * Parses a Green Button (NAESB ESPI, REQ.21) Atom feed.
 *
 * <p>
 * Element names are matched by their local name, so documents using the
 * classic {@code espi:} prefix ({@code http://naesb.org/espi}), a default
 * namespace or no namespace at all are all accepted. Entries are linked via
 * their Atom {@code <link rel="self|up|related">} elements: a MeterReading
 * refers to its ReadingType and its IntervalBlock collection with
 * {@code related} links, every IntervalBlock points back to that collection
 * with its {@code up} link.
 */
public final class EspiParser {

	private static final String FEED = "feed";
	private static final String ENTRY = "entry";
	private static final String LINK = "link";
	private static final String CONTENT = "content";
	private static final String USAGE_POINT = "UsagePoint";
	private static final String METER_READING = "MeterReading";
	private static final String READING_TYPE = "ReadingType";
	private static final String INTERVAL_BLOCK = "IntervalBlock";
	private static final String INTERVAL_READING = "IntervalReading";

	private EspiParser() {
	}

	/**
	 * Parses the given ESPI Atom XML.
	 *
	 * @param xml the XML document
	 * @return the {@link GreenButtonDocument}
	 * @throws OpenemsNamedException if the document is malformed
	 */
	public static GreenButtonDocument parse(String xml) throws OpenemsNamedException {
		if (xml == null || xml.isBlank()) {
			throw new OpenemsException("ESPI document is empty");
		}
		final Element root;
		try {
			root = XmlUtils.getXmlRootDocument(xml);
		} catch (ParserConfigurationException | SAXException | IOException e) {
			throw new OpenemsException("Unable to parse ESPI document: " + e.getMessage());
		}
		if (!FEED.equals(localName(root))) {
			throw new OpenemsException("ESPI document root is not an Atom <feed> but <" + root.getNodeName() + ">");
		}

		var entries = children(root, ENTRY).map(EspiParser::parseEntry).toList();

		var readingTypes = new LinkedHashMap<String, ReadingType>();
		var usagePoints = new ArrayList<Entry>();
		var meterReadings = new ArrayList<Entry>();
		var intervalBlocks = new ArrayList<Entry>();
		for (var entry : entries) {
			if (entry.content == null) {
				continue;
			}
			switch (entry.kind) {
			case READING_TYPE -> readingTypes.put(entry.self, parseReadingType(entry.content));
			case USAGE_POINT -> usagePoints.add(entry);
			case METER_READING -> meterReadings.add(entry);
			case INTERVAL_BLOCK -> intervalBlocks.add(entry);
			default -> {
				// ignore ElectricPowerUsageSummary, LocalTimeParameters, ...
			}
			}
		}

		var series = new ArrayList<IntervalSeries>();
		var assignedBlocks = new HashSet<Entry>();
		for (var meterReading : meterReadings) {
			var type = resolveReadingType(meterReading, readingTypes);
			var usagePointId = resolveUsagePointId(meterReading, usagePoints);
			var readings = new TreeMap<Instant, IntervalReading>();
			for (var block : intervalBlocks) {
				if (belongsTo(block, meterReading)) {
					assignedBlocks.add(block);
					parseIntervalBlock(block.content, type, readings);
				}
			}
			series.add(new IntervalSeries(usagePointId, meterReading.self, type, readings));
		}

		// IntervalBlocks without a MeterReading (e.g. minimal exports)
		var orphanReadings = new TreeMap<Instant, IntervalReading>();
		var orphanType = readingTypes.size() == 1 //
				? readingTypes.values().iterator().next() //
				: ReadingType.DEFAULT;
		for (var block : intervalBlocks) {
			if (!assignedBlocks.contains(block)) {
				parseIntervalBlock(block.content, orphanType, orphanReadings);
			}
		}
		if (!orphanReadings.isEmpty()) {
			series.add(new IntervalSeries("", "", orphanType, orphanReadings));
		}

		return new GreenButtonDocument(List.copyOf(series));
	}

	/**
	 * Parses a single {@code ReadingType} element.
	 *
	 * @param readingType the element
	 * @return the {@link ReadingType}
	 */
	protected static ReadingType parseReadingType(Element readingType) {
		return new ReadingType(//
				UnitOfMeasure.fromCode(childInt(readingType, "uom", UnitOfMeasure.WATT_HOURS.getCode())), //
				childInt(readingType, "powerOfTenMultiplier", 0), //
				childInt(readingType, "accumulationBehaviour", 0), //
				childInt(readingType, "flowDirection", 0), //
				childInt(readingType, "commodity", 0), //
				childInt(readingType, "intervalLength", 0), //
				childInt(readingType, "currency", 0) //
		);
	}

	private static void parseIntervalBlock(Element block, ReadingType type,
			SortedMap<Instant, IntervalReading> readings) throws OpenemsNamedException {
		try {
			var blockDuration = child(block, "interval") //
					.map(interval -> childLong(interval, "duration", 0L)) //
					.orElse(0L);
			for (var reading : children(block, INTERVAL_READING).toList()) {
				var timePeriod = child(reading, "timePeriod");
				if (timePeriod.isEmpty()) {
					throw new OpenemsException("IntervalReading without timePeriod");
				}
				var start = Instant.ofEpochSecond(requiredLong(timePeriod.get(), "start"));
				var duration = Duration.ofSeconds(childLong(timePeriod.get(), "duration", blockDuration));
				var rawValue = requiredLong(reading, "value");
				var cost = child(reading, "cost").map(EspiParser::text).map(Long::parseLong).orElse(null);
				var quality = child(reading, "ReadingQuality") //
						.flatMap(q -> child(q, "quality")) //
						.map(EspiParser::text) //
						.map(Integer::parseInt) //
						.orElse(null);
				readings.put(start, new IntervalReading(start, duration, type.scale(rawValue), cost, quality));
			}
		} catch (NumberFormatException e) {
			throw new OpenemsException("Invalid number in IntervalBlock: " + e.getMessage());
		}
	}

	private static ReadingType resolveReadingType(Entry meterReading, Map<String, ReadingType> readingTypes) {
		for (var related : meterReading.related) {
			var type = readingTypes.get(related);
			if (type != null) {
				return type;
			}
		}
		if (readingTypes.size() == 1) {
			return readingTypes.values().iterator().next();
		}
		return ReadingType.DEFAULT;
	}

	private static String resolveUsagePointId(Entry meterReading, List<Entry> usagePoints) {
		for (var usagePoint : usagePoints) {
			if (usagePoint.related.contains(meterReading.up) //
					|| !usagePoint.self.isEmpty() && meterReading.self.startsWith(usagePoint.self + "/")) {
				return usagePoint.self;
			}
		}
		return stripSuffix(meterReading.up, "/" + METER_READING);
	}

	private static boolean belongsTo(Entry block, Entry meterReading) {
		if (!block.up.isEmpty() && meterReading.related.contains(block.up)) {
			return true;
		}
		return !meterReading.self.isEmpty() && block.self.startsWith(meterReading.self + "/" + INTERVAL_BLOCK);
	}

	private record Entry(String kind, Element content, String self, String up, List<String> related) {
	}

	private static Entry parseEntry(Element entry) {
		var self = "";
		var up = "";
		var related = new ArrayList<String>();
		for (var link : children(entry, LINK).toList()) {
			var href = normalizeHref(link.getAttribute("href"));
			switch (link.getAttribute("rel")) {
			case "self" -> self = href;
			case "up" -> up = href;
			case "related" -> related.add(href);
			default -> {
				// ignore
			}
			}
		}
		var content = child(entry, CONTENT) //
				.flatMap(c -> elements(c).findFirst()) //
				.orElse(null);
		var kind = content != null ? localName(content) : "";
		return new Entry(kind, content, self, up, List.copyOf(related));
	}

	private static String normalizeHref(String href) {
		return stripSuffix(href.trim(), "/");
	}

	private static String stripSuffix(String value, String suffix) {
		if (value.endsWith(suffix)) {
			return value.substring(0, value.length() - suffix.length());
		}
		return value;
	}

	private static String localName(Node node) {
		var name = node.getNodeName();
		var colon = name.indexOf(':');
		return colon >= 0 ? name.substring(colon + 1) : name;
	}

	private static Stream<Element> elements(Node parent) {
		return XmlUtils.stream(parent) //
				.filter(n -> n.getNodeType() == Node.ELEMENT_NODE) //
				.map(Element.class::cast);
	}

	private static Stream<Element> children(Node parent, String localName) {
		return elements(parent).filter(e -> localName.equals(localName(e)));
	}

	private static Optional<Element> child(Node parent, String localName) {
		return children(parent, localName).findFirst();
	}

	private static String text(Node node) {
		return node.getTextContent().trim();
	}

	private static int childInt(Element parent, String localName, int def) {
		return child(parent, localName).map(EspiParser::text).filter(s -> !s.isEmpty()).map(Integer::parseInt)
				.orElse(def);
	}

	private static long childLong(Element parent, String localName, long def) {
		return child(parent, localName).map(EspiParser::text).filter(s -> !s.isEmpty()).map(Long::parseLong)
				.orElse(def);
	}

	private static long requiredLong(Element parent, String localName) throws OpenemsNamedException {
		var value = child(parent, localName).map(EspiParser::text).orElse(null);
		if (value == null || value.isEmpty()) {
			throw new OpenemsException("Missing <" + localName + "> in <" + parent.getNodeName() + ">");
		}
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw new OpenemsException("Invalid <" + localName + "> value [" + value + "]");
		}
	}
}
