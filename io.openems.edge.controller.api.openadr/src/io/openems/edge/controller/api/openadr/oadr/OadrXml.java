package io.openems.edge.controller.api.openadr.oadr;

import static io.openems.edge.controller.api.openadr.oadr.OadrNamespaces.ATOM;
import static io.openems.edge.controller.api.openadr.oadr.OadrNamespaces.EI;
import static io.openems.edge.controller.api.openadr.oadr.OadrNamespaces.EMIX;
import static io.openems.edge.controller.api.openadr.oadr.OadrNamespaces.OADR;
import static io.openems.edge.controller.api.openadr.oadr.OadrNamespaces.POWER;
import static io.openems.edge.controller.api.openadr.oadr.OadrNamespaces.PREFIX_ATOM;
import static io.openems.edge.controller.api.openadr.oadr.OadrNamespaces.PREFIX_EI;
import static io.openems.edge.controller.api.openadr.oadr.OadrNamespaces.PREFIX_EMIX;
import static io.openems.edge.controller.api.openadr.oadr.OadrNamespaces.PREFIX_OADR;
import static io.openems.edge.controller.api.openadr.oadr.OadrNamespaces.PREFIX_POWER;
import static io.openems.edge.controller.api.openadr.oadr.OadrNamespaces.PREFIX_PYLD;
import static io.openems.edge.controller.api.openadr.oadr.OadrNamespaces.PREFIX_SCALE;
import static io.openems.edge.controller.api.openadr.oadr.OadrNamespaces.PREFIX_STRM;
import static io.openems.edge.controller.api.openadr.oadr.OadrNamespaces.PREFIX_XCAL;
import static io.openems.edge.controller.api.openadr.oadr.OadrNamespaces.PYLD;
import static io.openems.edge.controller.api.openadr.oadr.OadrNamespaces.SCALE;
import static io.openems.edge.controller.api.openadr.oadr.OadrNamespaces.STRM;
import static io.openems.edge.controller.api.openadr.oadr.OadrNamespaces.XCAL;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.utils.XmlUtils;

/**
 * Helpers to build and read OpenADR XML documents with the JDK DOM API.
 */
public final class OadrXml {

	private OadrXml() {
	}

	/**
	 * Fluent builder for namespaced XML elements.
	 */
	public static final class Builder {
		private final Document document;
		private final Element element;

		private Builder(Document document, Element element) {
			this.document = document;
			this.element = element;
		}

		/**
		 * Creates a new document with the given root element in the oadr namespace.
		 * 
		 * @param localName the local name of the root element
		 * @return the root {@link Builder}
		 * @throws OpenemsException on parser configuration error
		 */
		public static Builder root(String localName) throws OpenemsException {
			try {
				var factory = DocumentBuilderFactory.newInstance();
				factory.setNamespaceAware(true);
				factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
				var document = factory.newDocumentBuilder().newDocument();
				var root = document.createElementNS(OADR, PREFIX_OADR + ":" + localName);
				root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:" + PREFIX_OADR, OADR);
				root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:" + PREFIX_EI, EI);
				root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:" + PREFIX_PYLD, PYLD);
				root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:" + PREFIX_XCAL, XCAL);
				root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:" + PREFIX_EMIX, EMIX);
				root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:" + PREFIX_ATOM, ATOM);
				root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:" + PREFIX_STRM, STRM);
				root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:" + PREFIX_POWER, POWER);
				root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:" + PREFIX_SCALE, SCALE);
				document.appendChild(root);
				return new Builder(document, root);
			} catch (ParserConfigurationException e) {
				throw new OpenemsException("Unable to create XML document: " + e.getMessage());
			}
		}

		/**
		 * Appends a child element and returns its builder.
		 * 
		 * @param namespace the namespace URI
		 * @param prefix    the namespace prefix
		 * @param localName the local name
		 * @return the child {@link Builder}
		 */
		public Builder child(String namespace, String prefix, String localName) {
			var child = this.document.createElementNS(namespace, prefix + ":" + localName);
			this.element.appendChild(child);
			return new Builder(this.document, child);
		}

		/**
		 * Appends a child element in the oadr namespace.
		 * 
		 * @param localName the local name
		 * @return the child {@link Builder}
		 */
		public Builder oadr(String localName) {
			return this.child(OADR, PREFIX_OADR, localName);
		}

		/**
		 * Appends a child element in the ei namespace.
		 * 
		 * @param localName the local name
		 * @return the child {@link Builder}
		 */
		public Builder ei(String localName) {
			return this.child(EI, PREFIX_EI, localName);
		}

		/**
		 * Appends a child element in the pyld namespace.
		 * 
		 * @param localName the local name
		 * @return the child {@link Builder}
		 */
		public Builder pyld(String localName) {
			return this.child(PYLD, PREFIX_PYLD, localName);
		}

		/**
		 * Appends a child element in the xcal namespace.
		 * 
		 * @param localName the local name
		 * @return the child {@link Builder}
		 */
		public Builder xcal(String localName) {
			return this.child(XCAL, PREFIX_XCAL, localName);
		}

		/**
		 * Appends a child element in the emix namespace.
		 * 
		 * @param localName the local name
		 * @return the child {@link Builder}
		 */
		public Builder emix(String localName) {
			return this.child(EMIX, PREFIX_EMIX, localName);
		}

		/**
		 * Appends a child element in the strm namespace.
		 * 
		 * @param localName the local name
		 * @return the child {@link Builder}
		 */
		public Builder strm(String localName) {
			return this.child(STRM, PREFIX_STRM, localName);
		}

		/**
		 * Appends a child element in the power namespace.
		 * 
		 * @param localName the local name
		 * @return the child {@link Builder}
		 */
		public Builder power(String localName) {
			return this.child(POWER, PREFIX_POWER, localName);
		}

		/**
		 * Appends a child element in the scale namespace.
		 * 
		 * @param localName the local name
		 * @return the child {@link Builder}
		 */
		public Builder scale(String localName) {
			return this.child(SCALE, PREFIX_SCALE, localName);
		}

		/**
		 * Sets the text content of this element.
		 * 
		 * @param text the text
		 * @return this
		 */
		public Builder text(String text) {
			this.element.setTextContent(text);
			return this;
		}

		/**
		 * Sets the text content of this element.
		 * 
		 * @param value the value
		 * @return this
		 */
		public Builder text(long value) {
			return this.text(Long.toString(value));
		}

		/**
		 * Sets an attribute on this element.
		 * 
		 * @param name  the attribute name
		 * @param value the attribute value
		 * @return this
		 */
		public Builder attribute(String name, String value) {
			var colon = name.indexOf(':');
			if (colon > 0) {
				var ns = OadrNamespaces.uriForPrefix(name.substring(0, colon));
				if (ns != null) {
					this.element.setAttributeNS(ns, name, value);
					return this;
				}
			}
			this.element.setAttribute(name, value);
			return this;
		}

		/**
		 * Serializes the document to a string.
		 * 
		 * @return the XML string
		 * @throws OpenemsException on transformation error
		 */
		public String toXml() throws OpenemsException {
			try {
				var transformer = TransformerFactory.newInstance().newTransformer();
				transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
				transformer.setOutputProperty(OutputKeys.INDENT, "no");
				var writer = new StringWriter();
				transformer.transform(new DOMSource(this.document), new StreamResult(writer));
				return writer.toString();
			} catch (TransformerException e) {
				throw new OpenemsException("Unable to serialize XML: " + e.getMessage());
			}
		}
	}

	/**
	 * Parses the XML string and returns the root element.
	 * 
	 * @param xml the XML
	 * @return the root {@link Element}
	 * @throws OpenemsException on parse error
	 */
	public static Element parse(String xml) throws OpenemsException {
		try {
			var factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
			return document.getDocumentElement();
		} catch (Exception e) {
			throw new OpenemsException("Unable to parse XML: " + e.getMessage());
		}
	}

	/**
	 * Gets the local name of a node, ignoring the namespace prefix.
	 * 
	 * @param node the {@link Node}
	 * @return the local name
	 */
	public static String localName(Node node) {
		var name = node.getNodeName();
		var i = name.indexOf(':');
		return i < 0 ? name : name.substring(i + 1);
	}

	/**
	 * Streams the direct child elements with the given local name.
	 * 
	 * @param parent    the parent node
	 * @param localName the local name
	 * @return the child elements
	 */
	public static Stream<Element> children(Node parent, String localName) {
		return XmlUtils.stream(parent) //
				.filter(n -> n.getNodeType() == Node.ELEMENT_NODE && localName(n).equals(localName)) //
				.map(Element.class::cast);
	}

	/**
	 * Gets the first direct child element with the given local name.
	 * 
	 * @param parent    the parent node
	 * @param localName the local name
	 * @return the child element
	 */
	public static Optional<Element> child(Node parent, String localName) {
		return children(parent, localName).findFirst();
	}

	/**
	 * Finds the first element with the given local name anywhere below the parent
	 * (depth-first).
	 * 
	 * @param parent    the parent node
	 * @param localName the local name
	 * @return the element
	 */
	public static Optional<Element> descendant(Node parent, String localName) {
		for (var node : XmlUtils.list(parent)) {
			if (node.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			if (localName(node).equals(localName)) {
				return Optional.of((Element) node);
			}
			var result = descendant(node, localName);
			if (result.isPresent()) {
				return result;
			}
		}
		return Optional.empty();
	}

	/**
	 * Gets the trimmed text content of the first descendant with the given local
	 * name.
	 * 
	 * @param parent    the parent node
	 * @param localName the local name
	 * @return the text or empty
	 */
	public static Optional<String> descendantText(Node parent, String localName) {
		return descendant(parent, localName).map(e -> e.getTextContent().trim()).filter(s -> !s.isEmpty());
	}

	/**
	 * Gets the trimmed text content of the first direct child with the given local
	 * name.
	 * 
	 * @param parent    the parent node
	 * @param localName the local name
	 * @return the text or empty
	 */
	public static Optional<String> childText(Node parent, String localName) {
		return child(parent, localName).map(e -> e.getTextContent().trim()).filter(s -> !s.isEmpty());
	}

	/**
	 * Parses an ISO-8601 duration, e.g. "PT1H". Empty or invalid values yield
	 * {@link Duration#ZERO}.
	 * 
	 * @param text the text
	 * @return the {@link Duration}
	 */
	public static Duration parseDuration(String text) {
		if (text == null || text.isBlank()) {
			return Duration.ZERO;
		}
		try {
			var negative = text.startsWith("-");
			var d = Duration.parse(negative ? text.substring(1) : text);
			return negative ? d.negated() : d;
		} catch (DateTimeParseException e) {
			return Duration.ZERO;
		}
	}

	/**
	 * Parses an xcal date-time (ISO-8601 instant).
	 * 
	 * @param text the text
	 * @return the {@link Instant}
	 * @throws OpenemsException on parse error
	 */
	public static Instant parseInstant(String text) throws OpenemsException {
		try {
			return Instant.parse(text.trim());
		} catch (DateTimeParseException | NullPointerException e) {
			throw new OpenemsException("Invalid date-time [" + text + "]");
		}
	}

	/**
	 * Formats a {@link Duration} as ISO-8601 (xcal duration).
	 * 
	 * @param duration the {@link Duration}
	 * @return the text
	 */
	public static String formatDuration(Duration duration) {
		return duration.toString();
	}

	/**
	 * Formats an {@link Instant} as xcal date-time with second precision.
	 * 
	 * @param instant the {@link Instant}
	 * @return the text
	 */
	public static String formatInstant(Instant instant) {
		return instant.toString().replaceAll("\\.\\d+Z$", "Z");
	}
}
