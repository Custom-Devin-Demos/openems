package io.openems.edge.greenbutton.espi;

import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import com.google.common.io.Resources;

public final class Fixtures {

	public static final String PGE_15MIN = "pge-15min.xml";
	public static final String COMED_HOURLY = "comed-hourly.xml";
	public static final String MULTI_USAGEPOINT_COST = "multi-usagepoint-cost.xml";

	private Fixtures() {
	}

	/**
	 * Reads a fixture file from the {@code fixtures} resource folder.
	 *
	 * @param name the file name
	 * @return the file content
	 */
	public static String read(String name) {
		var resource = Fixtures.class.getResource("fixtures/" + name);
		assertNotNull("Fixture not found: " + name, resource);
		try {
			return Resources.toString(resource, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Gets the absolute file system path of a fixture.
	 *
	 * @param name the file name
	 * @return the path
	 */
	public static String path(String name) {
		var resource = Fixtures.class.getResource("fixtures/" + name);
		assertNotNull("Fixture not found: " + name, resource);
		return resource.getPath();
	}
}
