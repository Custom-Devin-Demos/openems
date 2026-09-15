package io.openems.edge.controller.api.openadr;

import io.openems.common.test.AbstractComponentConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	public static class Builder {
		private String id;
		private String vtnUrl = "http://localhost:1/OpenADR2/Simple/2.0b";
		private String venName = "test-ven";
		private String venId = "";
		private String registrationId = "";
		private int pollIntervalSeconds = 10;
		private Tls tls = Tls.SYSTEM_TRUSTSTORE;
		private String trustStorePath = "";
		private String trustStorePassword = "";
		private String clientCertPath = "";
		private String clientKeyPassword = "";
		private String marketContext = "";
		private boolean autoOptIn = true;
		private String essId = "";
		private String[] evcsIds = {};
		private String[] evseIds = {};
		private String[] heatPumpIds = {};
		private int curtailmentEssDischargeLimitW = 0;
		private int curtailmentEvcsChargeLimitW = 0;
		private double priceSignalThresholdPerMwh = 100;
		private int reportIntervalSeconds = 60;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setVtnUrl(String vtnUrl) {
			this.vtnUrl = vtnUrl;
			return this;
		}

		public Builder setVenName(String venName) {
			this.venName = venName;
			return this;
		}

		public Builder setVenId(String venId) {
			this.venId = venId;
			return this;
		}

		public Builder setRegistrationId(String registrationId) {
			this.registrationId = registrationId;
			return this;
		}

		public Builder setPollIntervalSeconds(int pollIntervalSeconds) {
			this.pollIntervalSeconds = pollIntervalSeconds;
			return this;
		}

		public Builder setTls(Tls tls) {
			this.tls = tls;
			return this;
		}

		public Builder setMarketContext(String marketContext) {
			this.marketContext = marketContext;
			return this;
		}

		public Builder setAutoOptIn(boolean autoOptIn) {
			this.autoOptIn = autoOptIn;
			return this;
		}

		public Builder setEssId(String essId) {
			this.essId = essId;
			return this;
		}

		public Builder setEvcsIds(String... evcsIds) {
			this.evcsIds = evcsIds;
			return this;
		}

		public Builder setEvseIds(String... evseIds) {
			this.evseIds = evseIds;
			return this;
		}

		public Builder setHeatPumpIds(String... heatPumpIds) {
			this.heatPumpIds = heatPumpIds;
			return this;
		}

		public Builder setCurtailmentEssDischargeLimitW(int curtailmentEssDischargeLimitW) {
			this.curtailmentEssDischargeLimitW = curtailmentEssDischargeLimitW;
			return this;
		}

		public Builder setCurtailmentEvcsChargeLimitW(int curtailmentEvcsChargeLimitW) {
			this.curtailmentEvcsChargeLimitW = curtailmentEvcsChargeLimitW;
			return this;
		}

		public Builder setPriceSignalThresholdPerMwh(double priceSignalThresholdPerMwh) {
			this.priceSignalThresholdPerMwh = priceSignalThresholdPerMwh;
			return this;
		}

		public Builder setReportIntervalSeconds(int reportIntervalSeconds) {
			this.reportIntervalSeconds = reportIntervalSeconds;
			return this;
		}

		public MyConfig build() {
			return new MyConfig(this);
		}
	}

	/**
	 * Create a Config builder.
	 *
	 * @return a {@link Builder}
	 */
	public static Builder create() {
		return new Builder();
	}

	private final Builder builder;

	private MyConfig(Builder builder) {
		super(Config.class, builder.id);
		this.builder = builder;
	}

	@Override
	public String vtnUrl() {
		return this.builder.vtnUrl;
	}

	@Override
	public String venName() {
		return this.builder.venName;
	}

	@Override
	public String venId() {
		return this.builder.venId;
	}

	@Override
	public String registrationId() {
		return this.builder.registrationId;
	}

	@Override
	public int pollIntervalSeconds() {
		return this.builder.pollIntervalSeconds;
	}

	@Override
	public Tls tls() {
		return this.builder.tls;
	}

	@Override
	public String trustStorePath() {
		return this.builder.trustStorePath;
	}

	@Override
	public String trustStorePassword() {
		return this.builder.trustStorePassword;
	}

	@Override
	public String clientCertPath() {
		return this.builder.clientCertPath;
	}

	@Override
	public String clientKeyPassword() {
		return this.builder.clientKeyPassword;
	}

	@Override
	public String marketContext() {
		return this.builder.marketContext;
	}

	@Override
	public boolean autoOptIn() {
		return this.builder.autoOptIn;
	}

	@Override
	public String ess_id() {
		return this.builder.essId;
	}

	@Override
	public String[] evcs_ids() {
		return this.builder.evcsIds;
	}

	@Override
	public String[] evse_ids() {
		return this.builder.evseIds;
	}

	@Override
	public String[] heatPump_ids() {
		return this.builder.heatPumpIds;
	}

	@Override
	public int curtailmentEssDischargeLimitW() {
		return this.builder.curtailmentEssDischargeLimitW;
	}

	@Override
	public int curtailmentEvcsChargeLimitW() {
		return this.builder.curtailmentEvcsChargeLimitW;
	}

	@Override
	public double priceSignalThresholdPerMwh() {
		return this.builder.priceSignalThresholdPerMwh;
	}

	@Override
	public int reportIntervalSeconds() {
		return this.builder.reportIntervalSeconds;
	}
}
