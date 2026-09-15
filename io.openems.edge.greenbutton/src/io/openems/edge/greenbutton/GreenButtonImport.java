package io.openems.edge.greenbutton;

import io.openems.common.channel.Level;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.EnumReadChannel;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.LongReadChannel;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.channel.StringReadChannel;
import io.openems.edge.common.component.OpenemsComponent;

public interface GreenButtonImport extends OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		/**
		 * State of the import.
		 */
		IMPORT_STATE(Doc.of(ImportState.values()) //
				.text("State of the last/current import")),
		/**
		 * Timestamp of the last successful import.
		 */
		LAST_IMPORT(Doc.of(OpenemsType.LONG) //
				.unit(Unit.SECONDS) //
				.text("Timestamp (epoch seconds) of the last successful import")),
		/**
		 * Number of readings written during the last import.
		 */
		IMPORTED_READINGS(Doc.of(OpenemsType.INTEGER) //
				.text("Number of interval readings written by the last import")),
		/**
		 * Last error message.
		 */
		LAST_ERROR(Doc.of(OpenemsType.STRING) //
				.text("Error message of the last failed import")),
		/**
		 * Start of the first imported interval.
		 */
		FIRST_READING(Doc.of(OpenemsType.LONG) //
				.unit(Unit.SECONDS) //
				.text("Start (epoch seconds) of the first imported interval")),
		/**
		 * Start of the last imported interval.
		 */
		LAST_READING(Doc.of(OpenemsType.LONG) //
				.unit(Unit.SECONDS) //
				.text("Start (epoch seconds) of the last imported interval")),
		/**
		 * The last import failed.
		 */
		IMPORT_FAILED(Doc.of(Level.WARNING) //
				.text("The last Green Button import failed; see LastError")),
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
	 * Gets the Channel for {@link ChannelId#IMPORT_STATE}.
	 *
	 * @return the Channel
	 */
	public default EnumReadChannel getImportStateChannel() {
		return this.channel(ChannelId.IMPORT_STATE);
	}

	/**
	 * Gets the {@link ImportState}.
	 *
	 * @return the value
	 */
	public default ImportState getImportState() {
		return this.getImportStateChannel().value().asEnum();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#IMPORT_STATE}.
	 *
	 * @param value the next value
	 */
	public default void _setImportState(ImportState value) {
		this.getImportStateChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#LAST_IMPORT}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getLastImportChannel() {
		return this.channel(ChannelId.LAST_IMPORT);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#LAST_IMPORT}.
	 *
	 * @param value the next value
	 */
	public default void _setLastImport(Long value) {
		this.getLastImportChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#IMPORTED_READINGS}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getImportedReadingsChannel() {
		return this.channel(ChannelId.IMPORTED_READINGS);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#IMPORTED_READINGS}.
	 *
	 * @param value the next value
	 */
	public default void _setImportedReadings(Integer value) {
		this.getImportedReadingsChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#LAST_ERROR}.
	 *
	 * @return the Channel
	 */
	public default StringReadChannel getLastErrorChannel() {
		return this.channel(ChannelId.LAST_ERROR);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#LAST_ERROR}.
	 *
	 * @param value the next value
	 */
	public default void _setLastError(String value) {
		this.getLastErrorChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#FIRST_READING}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getFirstReadingChannel() {
		return this.channel(ChannelId.FIRST_READING);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#FIRST_READING}.
	 *
	 * @param value the next value
	 */
	public default void _setFirstReading(Long value) {
		this.getFirstReadingChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#LAST_READING}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getLastReadingChannel() {
		return this.channel(ChannelId.LAST_READING);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#LAST_READING}.
	 *
	 * @param value the next value
	 */
	public default void _setLastReading(Long value) {
		this.getLastReadingChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#IMPORT_FAILED}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getImportFailedChannel() {
		return this.channel(ChannelId.IMPORT_FAILED);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#IMPORT_FAILED}.
	 *
	 * @param value the next value
	 */
	public default void _setImportFailed(boolean value) {
		this.getImportFailedChannel().setNextValue(value);
	}
}
