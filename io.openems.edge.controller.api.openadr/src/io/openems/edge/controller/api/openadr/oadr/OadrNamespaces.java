package io.openems.edge.controller.api.openadr.oadr;

/**
 * XML namespaces of OpenADR 2.0b (profile B) payloads.
 */
public final class OadrNamespaces {

	public static final String OADR = "http://openadr.org/oadr-2.0b/2012/07";
	public static final String EI = "http://docs.oasis-open.org/ns/energyinterop/201110";
	public static final String PYLD = "http://docs.oasis-open.org/ns/energyinterop/201110/payloads";
	public static final String XCAL = "urn:ietf:params:xml:ns:icalendar-2.0";
	public static final String EMIX = "http://docs.oasis-open.org/ns/emix/2011/06";
	public static final String ATOM = "http://www.w3.org/2005/Atom";
	public static final String STRM = "urn:ietf:params:xml:ns:icalendar-2.0:stream";
	public static final String POWER = "http://docs.oasis-open.org/ns/emix/2011/06/power";
	public static final String SCALE = "http://docs.oasis-open.org/ns/emix/2011/06/siscale";

	public static final String PREFIX_OADR = "oadr";
	public static final String PREFIX_EI = "ei";
	public static final String PREFIX_PYLD = "pyld";
	public static final String PREFIX_XCAL = "xcal";
	public static final String PREFIX_EMIX = "emix";
	public static final String PREFIX_ATOM = "atom";
	public static final String PREFIX_STRM = "strm";
	public static final String PREFIX_POWER = "power";
	public static final String PREFIX_SCALE = "scale";

	public static final String SCHEMA_VERSION = "2.0b";

	private OadrNamespaces() {
	}

	/**
	 * Resolves a well-known prefix to its namespace URI.
	 * 
	 * @param prefix the prefix, e.g. "ei"
	 * @return the namespace URI or null
	 */
	public static String uriForPrefix(String prefix) {
		return switch (prefix) {
		case PREFIX_OADR -> OADR;
		case PREFIX_EI -> EI;
		case PREFIX_PYLD -> PYLD;
		case PREFIX_XCAL -> XCAL;
		case PREFIX_EMIX -> EMIX;
		case PREFIX_ATOM -> ATOM;
		case PREFIX_STRM -> STRM;
		case PREFIX_POWER -> POWER;
		case PREFIX_SCALE -> SCALE;
		default -> null;
		};
	}
}
