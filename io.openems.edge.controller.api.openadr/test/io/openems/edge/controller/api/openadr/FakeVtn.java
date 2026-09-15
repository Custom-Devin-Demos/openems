package io.openems.edge.controller.api.openadr;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.controller.api.openadr.oadr.Fixtures;
import io.openems.edge.controller.api.openadr.oadr.OadrMessages;
import io.openems.edge.controller.api.openadr.oadr.OadrXml;

/**
 * A minimal OpenADR 2.0b VTN on an ephemeral port for integration tests.
 */
public class FakeVtn implements AutoCloseable {

	public static final String PATH = "/OpenADR2/Simple/2.0b";

	/**
	 * One received request.
	 * 
	 * @param service     the service path, e.g. "EiRegisterParty"
	 * @param contentType the Content-Type header
	 * @param message     the OpenADR message name, e.g. "oadrPoll"
	 * @param body        the raw body
	 */
	public record Received(String service, String contentType, String message, String body) {
	}

	private final HttpServer server;
	private final List<Received> received = new CopyOnWriteArrayList<>();
	private final AtomicReference<String> nextPollResponse = new AtomicReference<>(null);
	private volatile int pollStatusCode = 200;

	public FakeVtn() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext(PATH + "/EiRegisterParty", ex -> this.handle("EiRegisterParty", ex));
		this.server.createContext(PATH + "/OadrPoll", ex -> this.handle("OadrPoll", ex));
		this.server.createContext(PATH + "/EiEvent", ex -> this.handle("EiEvent", ex));
		this.server.createContext(PATH + "/EiReport", ex -> this.handle("EiReport", ex));
		this.server.createContext(PATH + "/EiOpt", ex -> this.handle("EiOpt", ex));
		this.server.start();
	}

	/**
	 * Gets the base URL of this VTN, to be used as {@code vtnUrl}.
	 * 
	 * @return the URL
	 */
	public String url() {
		return "http://127.0.0.1:" + this.server.getAddress().getPort() + PATH;
	}

	/**
	 * Sets the payload returned on the next oadrPoll; afterwards an empty
	 * oadrResponse is returned again.
	 * 
	 * @param xml the payload
	 */
	public void enqueuePollResponse(String xml) {
		this.nextPollResponse.set(xml);
	}

	/**
	 * Makes all oadrPoll requests fail with the given HTTP status code (200 to
	 * recover).
	 * 
	 * @param statusCode the status code
	 */
	public void setPollStatusCode(int statusCode) {
		this.pollStatusCode = statusCode;
	}

	/**
	 * Gets all received requests.
	 * 
	 * @return the requests
	 */
	public List<Received> received() {
		return this.received;
	}

	/**
	 * Gets the received requests with the given message name.
	 * 
	 * @param message the message name, e.g. "oadrCreatedEvent"
	 * @return the requests
	 */
	public List<Received> received(String message) {
		return this.received.stream().filter(r -> r.message().equals(message)).toList();
	}

	/**
	 * Waits until at least one request with the given message name was received.
	 * 
	 * @param message   the message name
	 * @param timeoutMs the timeout in milliseconds
	 * @return the first matching request
	 * @throws InterruptedException on interrupt
	 */
	public Received awaitMessage(String message, long timeoutMs) throws InterruptedException {
		var deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			var matches = this.received(message);
			if (!matches.isEmpty()) {
				return matches.get(0);
			}
			Thread.sleep(20);
		}
		throw new AssertionError("Timeout waiting for [" + message + "]; received: "
				+ this.received.stream().map(Received::message).toList());
	}

	private void handle(String service, HttpExchange ex) throws IOException {
		var body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		String message;
		try {
			message = OadrMessages.messageName(OadrXml.parse(body));
		} catch (OpenemsException e) {
			message = "invalid";
		}
		this.received.add(new Received(service, ex.getRequestHeaders().getFirst("Content-Type"), message, body));

		try {
			var statusCode = 200;
			var response = switch (message) {
			case "oadrQueryRegistration", "oadrCreatePartyRegistration" -> Fixtures
					.read(Fixtures.CREATED_PARTY_REGISTRATION);
			case "oadrRegisterReport" -> Fixtures.read(Fixtures.CREATE_REPORT);
			case "oadrPoll" -> {
				statusCode = this.pollStatusCode;
				var next = this.nextPollResponse.getAndSet(null);
				yield next != null ? next : OadrMessages.response("", "ven-openems-1", 200, "OK");
			}
			default -> OadrMessages.response("", "ven-openems-1", 200, "OK");
			};
			var bytes = response.getBytes(StandardCharsets.UTF_8);
			ex.getResponseHeaders().add("Content-Type", "application/xml");
			ex.sendResponseHeaders(statusCode, bytes.length);
			ex.getResponseBody().write(bytes);
		} catch (OpenemsException e) {
			ex.sendResponseHeaders(500, -1);
		} finally {
			ex.close();
		}
	}

	@Override
	public void close() {
		this.server.stop(0);
	}
}
