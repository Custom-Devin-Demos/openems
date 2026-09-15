package io.openems.edge.timeofusetariff.pjm;

import io.openems.common.test.AbstractComponentConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	public static class Builder {
		private String id;
		private String apiKey = "";
		private long pnodeId = 33092371L;
		private String zone = "COMED";
		private int pollingIntervalMinutes = 60;
		private double ancillaryCostsPerMwh = 0;
		private int httpTimeoutSeconds = 15;
		private int maxRetries = 3;
		private String useFixtureFile = "";

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setApiKey(String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		public Builder setPnodeId(long pnodeId) {
			this.pnodeId = pnodeId;
			return this;
		}

		public Builder setZone(String zone) {
			this.zone = zone;
			return this;
		}

		public Builder setPollingIntervalMinutes(int pollingIntervalMinutes) {
			this.pollingIntervalMinutes = pollingIntervalMinutes;
			return this;
		}

		public Builder setAncillaryCostsPerMwh(double ancillaryCostsPerMwh) {
			this.ancillaryCostsPerMwh = ancillaryCostsPerMwh;
			return this;
		}

		public Builder setHttpTimeoutSeconds(int httpTimeoutSeconds) {
			this.httpTimeoutSeconds = httpTimeoutSeconds;
			return this;
		}

		public Builder setMaxRetries(int maxRetries) {
			this.maxRetries = maxRetries;
			return this;
		}

		public Builder setUseFixtureFile(String useFixtureFile) {
			this.useFixtureFile = useFixtureFile;
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
	public String apiKey() {
		return this.builder.apiKey;
	}

	@Override
	public long pnodeId() {
		return this.builder.pnodeId;
	}

	@Override
	public String zone() {
		return this.builder.zone;
	}

	@Override
	public int pollingIntervalMinutes() {
		return this.builder.pollingIntervalMinutes;
	}

	@Override
	public double ancillaryCostsPerMwh() {
		return this.builder.ancillaryCostsPerMwh;
	}

	@Override
	public int httpTimeoutSeconds() {
		return this.builder.httpTimeoutSeconds;
	}

	@Override
	public int maxRetries() {
		return this.builder.maxRetries;
	}

	@Override
	public String useFixtureFile() {
		return this.builder.useFixtureFile;
	}

}
