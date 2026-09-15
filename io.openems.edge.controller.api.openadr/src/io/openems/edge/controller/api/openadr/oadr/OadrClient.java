package io.openems.edge.controller.api.openadr.oadr;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import io.openems.common.exceptions.OpenemsException;

/**
 * HTTP client for the OpenADR 2.0b simpleHttp transport (VEN pull model).
 */
public class OadrClient {

	public static final String SERVICE_REGISTER_PARTY = "EiRegisterParty";
	public static final String SERVICE_EVENT = "EiEvent";
	public static final String SERVICE_REPORT = "EiReport";
	public static final String SERVICE_OPT = "EiOpt";
	public static final String SERVICE_POLL = "OadrPoll";

	private static final String CONTENT_TYPE = "application/xml";

	/**
	 * Trust configuration.
	 */
	public enum TrustMode {
		SYSTEM_TRUSTSTORE, TRUST_ALL, CUSTOM
	}

	/**
	 * TLS settings for the client.
	 * 
	 * @param trustMode          the {@link TrustMode}
	 * @param trustStorePath     path of a PKCS12 trust store (CUSTOM)
	 * @param trustStorePassword the trust store password
	 * @param clientCertPath     path of a PKCS12 key store with the client
	 *                           certificate; empty for none
	 * @param clientKeyPassword  the key store password
	 */
	public record TlsConfig(TrustMode trustMode, String trustStorePath, String trustStorePassword,
			String clientCertPath, String clientKeyPassword) {

		public static final TlsConfig SYSTEM = new TlsConfig(TrustMode.SYSTEM_TRUSTSTORE, "", "", "", "");
	}

	/**
	 * A HTTP response.
	 * 
	 * @param statusCode the HTTP status code
	 * @param body       the body
	 */
	public record Response(int statusCode, String body) {

		/**
		 * Is the status code 2xx?.
		 * 
		 * @return true if ok
		 */
		public boolean isOk() {
			return this.statusCode / 100 == 2;
		}
	}

	private final String baseUrl;
	private final HttpClient httpClient;
	private final Duration timeout;

	/**
	 * Creates a client.
	 * 
	 * @param baseUrl the VTN base URL, e.g.
	 *                {@code https://vtn:8443/OpenADR2/Simple/2.0b}
	 * @param tls     the {@link TlsConfig}
	 * @param timeout the request timeout
	 * @throws OpenemsException if the TLS configuration is invalid
	 */
	public OadrClient(String baseUrl, TlsConfig tls, Duration timeout) throws OpenemsException {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.timeout = timeout;
		this.httpClient = HttpClient.newBuilder() //
				.connectTimeout(timeout) //
				.sslContext(createSslContext(tls)) //
				.build();
	}

	/**
	 * Gets the full URL of a service.
	 * 
	 * @param service the service name, e.g. {@link #SERVICE_EVENT}
	 * @return the URL
	 */
	public String url(String service) {
		return this.baseUrl + "/" + service;
	}

	/**
	 * POSTs an OpenADR payload to a service.
	 * 
	 * @param service the service name
	 * @param xml     the payload
	 * @return the {@link Response}
	 * @throws OpenemsException on I/O error
	 */
	public Response post(String service, String xml) throws OpenemsException {
		var request = HttpRequest.newBuilder(URI.create(this.url(service))) //
				.timeout(this.timeout) //
				.header("Content-Type", CONTENT_TYPE) //
				.header("Accept", CONTENT_TYPE) //
				.POST(HttpRequest.BodyPublishers.ofString(xml, StandardCharsets.UTF_8)) //
				.build();
		try {
			var response = this.httpClient.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
			return new Response(response.statusCode(), response.body());
		} catch (IOException e) {
			throw new OpenemsException("HTTP request to [" + this.url(service) + "] failed: " + e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new OpenemsException("HTTP request to [" + this.url(service) + "] interrupted");
		}
	}

	/**
	 * Creates the {@link SSLContext} for the given {@link TlsConfig}.
	 * 
	 * @param tls the {@link TlsConfig}
	 * @return the {@link SSLContext}
	 * @throws OpenemsException on invalid configuration
	 */
	protected static SSLContext createSslContext(TlsConfig tls) throws OpenemsException {
		try {
			KeyManager[] keyManagers = null;
			if (tls.clientCertPath() != null && !tls.clientCertPath().isBlank()) {
				var keyStore = loadKeyStore(tls.clientCertPath(), tls.clientKeyPassword());
				var kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				kmf.init(keyStore, passwordChars(tls.clientKeyPassword()));
				keyManagers = kmf.getKeyManagers();
			}

			TrustManager[] trustManagers = switch (tls.trustMode()) {
			case SYSTEM_TRUSTSTORE -> null;
			case TRUST_ALL -> new TrustManager[] { new TrustAllManager() };
			case CUSTOM -> {
				var trustStore = loadKeyStore(tls.trustStorePath(), tls.trustStorePassword());
				var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
				tmf.init(trustStore);
				yield tmf.getTrustManagers();
			}
			};

			if (keyManagers == null && trustManagers == null) {
				return SSLContext.getDefault();
			}
			var context = SSLContext.getInstance("TLS");
			context.init(keyManagers, trustManagers, new SecureRandom());
			return context;
		} catch (GeneralSecurityException | IOException e) {
			throw new OpenemsException("Invalid TLS configuration: " + e.getMessage());
		}
	}

	private static KeyStore loadKeyStore(String path, String password)
			throws GeneralSecurityException, IOException {
		var keyStore = KeyStore.getInstance("PKCS12");
		try (var is = new FileInputStream(path)) {
			keyStore.load(is, passwordChars(password));
		}
		return keyStore;
	}

	private static char[] passwordChars(String password) {
		return password == null ? new char[0] : password.toCharArray();
	}

	private static final class TrustAllManager implements X509TrustManager {
		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) {
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}
	}
}
