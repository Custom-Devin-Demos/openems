package io.openems.edge.timeofusetariff.comed;

import io.openems.common.test.AbstractComponentConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	public static class Builder {
		private String id;
		private Feed feed = Feed.DAY_AHEAD;
		private int pollingIntervalMinutes = 5;
		private double ancillaryCostsPerMwh = 0;
		private int httpTimeoutSeconds = 15;
		private int maxRetries = 3;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setFeed(Feed feed) {
			this.feed = feed;
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
	public Feed feed() {
		return this.builder.feed;
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

}
