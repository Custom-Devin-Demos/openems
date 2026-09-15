package io.openems.edge.timeofusetariff.pjm;

import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.LongReadChannel;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.timeofusetariff.api.TimeOfUseTariff;

public interface TimeOfUseTariffPjm extends TimeOfUseTariff, OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		/**
		 * HTTP status code of the last PJM API request; -1 if no response was received.
		 */
		HTTP_STATUS_CODE(Doc.of(OpenemsType.INTEGER) //
				.text("Displays the HTTP status code")), //
		/**
		 * Timestamp (epoch seconds) of the last successful price update.
		 */
		LAST_SUCCESSFUL_UPDATE(Doc.of(OpenemsType.LONG) //
				.unit(Unit.SECONDS) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Epoch seconds of the last successful price update")), //
		/**
		 * Number of consecutive failed PJM API requests.
		 */
		CONSECUTIVE_FAILURES(Doc.of(OpenemsType.INTEGER) //
				.persistencePriority(PersistencePriority.HIGH) //
				.text("Number of consecutive failed price updates")), //
		/**
		 * Configured PJM pricing node ID.
		 */
		PNODE_ID(Doc.of(OpenemsType.LONG) //
				.text("PJM pricing node ID")), //
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
	default IntegerReadChannel getHttpStatusCodeChannel() {
		return this.channel(ChannelId.HTTP_STATUS_CODE);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#HTTP_STATUS_CODE} Channel.
	 *
	 * @param value the next value
	 */
	default void _setHttpStatusCode(int value) {
		this.getHttpStatusCodeChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#LAST_SUCCESSFUL_UPDATE}.
	 *
	 * @return the Channel
	 */
	default LongReadChannel getLastSuccessfulUpdateChannel() {
		return this.channel(ChannelId.LAST_SUCCESSFUL_UPDATE);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#LAST_SUCCESSFUL_UPDATE} Channel.
	 *
	 * @param value the next value
	 */
	default void _setLastSuccessfulUpdate(long value) {
		this.getLastSuccessfulUpdateChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#CONSECUTIVE_FAILURES}.
	 *
	 * @return the Channel
	 */
	default IntegerReadChannel getConsecutiveFailuresChannel() {
		return this.channel(ChannelId.CONSECUTIVE_FAILURES);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#CONSECUTIVE_FAILURES} Channel.
	 *
	 * @param value the next value
	 */
	default void _setConsecutiveFailures(int value) {
		this.getConsecutiveFailuresChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#PNODE_ID}.
	 *
	 * @return the Channel
	 */
	default LongReadChannel getPnodeIdChannel() {
		return this.channel(ChannelId.PNODE_ID);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#PNODE_ID}
	 * Channel.
	 *
	 * @param value the next value
	 */
	default void _setPnodeId(long value) {
		this.getPnodeIdChannel().setNextValue(value);
	}

}
