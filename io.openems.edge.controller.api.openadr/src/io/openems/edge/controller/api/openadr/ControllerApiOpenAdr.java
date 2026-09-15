package io.openems.edge.controller.api.openadr;

import static io.openems.common.channel.PersistencePriority.HIGH;
import static io.openems.common.types.OpenemsType.BOOLEAN;
import static io.openems.common.types.OpenemsType.DOUBLE;
import static io.openems.common.types.OpenemsType.INTEGER;
import static io.openems.common.types.OpenemsType.LONG;
import static io.openems.common.types.OpenemsType.STRING;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Level;
import io.openems.common.channel.Unit;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.EnumWriteChannel;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.component.OpenemsComponent;

public interface ControllerApiOpenAdr extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		REGISTRATION_STATE(Doc.of(RegistrationState.values()) //
				.persistencePriority(HIGH) //
				.text("OpenADR party registration state")), //
		VEN_ID(Doc.of(STRING) //
				.persistencePriority(HIGH) //
				.text("VEN-ID assigned by the VTN")), //
		ACTIVE_EVENT_ID(Doc.of(STRING) //
				.persistencePriority(HIGH) //
				.text("ID of the currently active event")), //
		ACTIVE_EVENT_SIGNAL_LEVEL(Doc.of(INTEGER) //
				.persistencePriority(HIGH) //
				.text("SIMPLE signal level of the active event; -1 if none")), //
		ACTIVE_EVENT_PRICE(Doc.of(DOUBLE) //
				.persistencePriority(HIGH) //
				.text("PRICE signal value of the active event in Currency/MWh")), //
		ACTIVE_EVENT_START(Doc.of(LONG) //
				.unit(Unit.UNIX_TIMESTAMP_SECONDS) //
				.persistencePriority(HIGH)), //
		ACTIVE_EVENT_END(Doc.of(LONG) //
				.unit(Unit.UNIX_TIMESTAMP_SECONDS) //
				.persistencePriority(HIGH)), //
		EVENT_COUNT(Doc.of(INTEGER) //
				.persistencePriority(HIGH) //
				.text("Number of known, not cancelled events")), //
		OPT_STATE(Doc.of(OptState.values()) //
				.persistencePriority(HIGH) //
				.text("Opt state of the active event")), //
		SET_OPT_STATE(Doc.of(OptState.values()) //
				.accessMode(AccessMode.WRITE_ONLY) //
				.text("Sets the opt state of the active event")), //
		CURTAILMENT_ACTIVE(Doc.of(BOOLEAN) //
				.persistencePriority(HIGH)), //
		LAST_POLL(Doc.of(LONG) //
				.unit(Unit.UNIX_TIMESTAMP_SECONDS)), //
		HTTP_STATUS_CODE(Doc.of(INTEGER) //
				.text("Status code of the last VTN response")), //
		COMMUNICATION_FAILED(Doc.of(Level.FAULT) //
				.text("Communication with the VTN failed or a referenced component is missing")); //

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
	 * Gets the Channel for {@link ChannelId#REGISTRATION_STATE}.
	 *
	 * @return the Channel
	 */
	public default Channel<RegistrationState> getRegistrationStateChannel() {
		return this.channel(ChannelId.REGISTRATION_STATE);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#REGISTRATION_STATE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setRegistrationState(RegistrationState value) {
		this.getRegistrationStateChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#SET_OPT_STATE}.
	 *
	 * @return the Channel
	 */
	public default EnumWriteChannel getSetOptStateChannel() {
		return this.channel(ChannelId.SET_OPT_STATE);
	}

	/**
	 * Gets the Channel for {@link ChannelId#COMMUNICATION_FAILED}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getCommunicationFailedChannel() {
		return this.channel(ChannelId.COMMUNICATION_FAILED);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#COMMUNICATION_FAILED} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setCommunicationFailed(boolean value) {
		this.getCommunicationFailedChannel().setNextValue(value);
	}
}
