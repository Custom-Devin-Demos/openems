package io.openems.edge.greenbutton;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.common.utils.ConfigUtils;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	public static class Builder {
		private String id;
		private Source source = Source.FILE;
		private String path = "";
		private String url = "";
		private String authorizationHeader = "";
		private String targetChannel = "_sum/ConsumptionActiveEnergy";
		private boolean cumulative = true;
		private String powerChannel = "";
		private String timedataId = "timedata0";
		private int pollingIntervalHours = 0;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setSource(Source source) {
			this.source = source;
			return this;
		}

		public Builder setPath(String path) {
			this.path = path;
			return this;
		}

		public Builder setUrl(String url) {
			this.url = url;
			return this;
		}

		public Builder setAuthorizationHeader(String authorizationHeader) {
			this.authorizationHeader = authorizationHeader;
			return this;
		}

		public Builder setTargetChannel(String targetChannel) {
			this.targetChannel = targetChannel;
			return this;
		}

		public Builder setCumulative(boolean cumulative) {
			this.cumulative = cumulative;
			return this;
		}

		public Builder setPowerChannel(String powerChannel) {
			this.powerChannel = powerChannel;
			return this;
		}

		public Builder setTimedataId(String timedataId) {
			this.timedataId = timedataId;
			return this;
		}

		public Builder setPollingIntervalHours(int pollingIntervalHours) {
			this.pollingIntervalHours = pollingIntervalHours;
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
	public Source source() {
		return this.builder.source;
	}

	@Override
	public String path() {
		return this.builder.path;
	}

	@Override
	public String url() {
		return this.builder.url;
	}

	@Override
	public String authorizationHeader() {
		return this.builder.authorizationHeader;
	}

	@Override
	public String targetChannel() {
		return this.builder.targetChannel;
	}

	@Override
	public boolean cumulative() {
		return this.builder.cumulative;
	}

	@Override
	public String powerChannel() {
		return this.builder.powerChannel;
	}

	@Override
	public String timedata_id() {
		return this.builder.timedataId;
	}

	@Override
	public String timedata_target() {
		return ConfigUtils.generateReferenceTargetFilter(this.id(), this.timedata_id());
	}

	@Override
	public int pollingIntervalHours() {
		return this.builder.pollingIntervalHours;
	}
}
