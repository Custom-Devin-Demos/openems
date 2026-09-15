package io.openems.edge.timeofusetariff.comed;

import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.LongReadChannel;
import io.openems.edge.common.channel.StringReadChannel;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.timeofusetariff.api.TimeOfUseTariff;

public interface TimeOfUseTariffComEd extends TimeOfUseTariff, OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		/**
		 * HTTP status code of the last request.
		 *
		 * <ul>
		 * <li>Interface: TimeOfUseTariffComEd
		 * <li>Type: Integer
		 * </ul>
		 */
		HTTP_STATUS_CODE(Doc.of(OpenemsType.INTEGER) //
				.text("Displays the HTTP status code of the last request")), //
		/**
		 * Timestamp of the last successful poll.
		 *
		 * <ul>
		 * <li>Interface: TimeOfUseTariffComEd
		 * <li>Type: Long
		 * <li>Unit: seconds since epoch
		 * </ul>
		 */
		LAST_SUCCESSFUL_UPDATE(Doc.of(OpenemsType.LONG) //
				.unit(Unit.SECONDS) //
				.text("Epoch seconds of the last successful update")), //
		/**
		 * Number of consecutive failed polls.
		 *
		 * <ul>
		 * <li>Interface: TimeOfUseTariffComEd
		 * <li>Type: Integer
		 * </ul>
		 */
		CONSECUTIVE_FAILURES(Doc.of(OpenemsType.INTEGER) //
				.text("Number of consecutive failed polls")), //
		/**
		 * The configured {@link Feed}.
		 *
		 * <ul>
		 * <li>Interface: TimeOfUseTariffComEd
		 * <li>Type: String
		 * </ul>
		 */
		FEED(Doc.of(OpenemsType.STRING) //
				.text("The configured ComEd feed")), //
		;

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	/**
	 * Gets the Channel for {@link ChannelId#HTTP_STATUS_CODE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getHttpStatusCodeChannel() {
		return this.channel(ChannelId.HTTP_STATUS_CODE);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#HTTP_STATUS_CODE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setHttpStatusCode(Integer value) {
		this.getHttpStatusCodeChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#LAST_SUCCESSFUL_UPDATE}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getLastSuccessfulUpdateChannel() {
		return this.channel(ChannelId.LAST_SUCCESSFUL_UPDATE);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#LAST_SUCCESSFUL_UPDATE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setLastSuccessfulUpdate(Long value) {
		this.getLastSuccessfulUpdateChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#CONSECUTIVE_FAILURES}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getConsecutiveFailuresChannel() {
		return this.channel(ChannelId.CONSECUTIVE_FAILURES);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#CONSECUTIVE_FAILURES} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setConsecutiveFailures(Integer value) {
		this.getConsecutiveFailuresChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#FEED}.
	 *
	 * @return the Channel
	 */
	public default StringReadChannel getFeedChannel() {
		return this.channel(ChannelId.FEED);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#FEED} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setFeed(String value) {
		this.getFeedChannel().setNextValue(value);
	}
}
