import { TranslateService } from "@ngx-translate/core";
import { ChannelAddress } from "src/app/shared/shared";
import { Language } from "src/app/shared/type/language";

export namespace GreenButtonUtils {

    export const FACTORY_ID = "GreenButton.Import";

    export const CHANNEL_IMPORT_STATE = "ImportState";
    export const CHANNEL_LAST_IMPORT = "LastImport";
    export const CHANNEL_IMPORTED_READINGS = "ImportedReadings";
    export const CHANNEL_LAST_ERROR = "LastError";
    export const CHANNEL_FIRST_READING = "FirstReading";
    export const CHANNEL_LAST_READING = "LastReading";

    export enum ImportState {
        IDLE = 0,
        RUNNING = 1,
        DONE = 2,
        FAILED = 3,
    }

    export type Values = {
        importState: ImportState | null;
        lastImport: number | null;
        importedReadings: number | null;
        lastError: string | null;
        firstReading: number | null;
        lastReading: number | null;
    };

    export const EMPTY_VALUES: Values = {
        importState: null,
        lastImport: null,
        importedReadings: null,
        lastError: null,
        firstReading: null,
        lastReading: null,
    };

    /**
     * Gets the channel addresses subscribed by flat widget and modal.
     *
     * @param componentId the component id
     * @returns the channel addresses
     */
    export function getChannelAddresses(componentId: string): ChannelAddress[] {
        return [
            CHANNEL_IMPORT_STATE,
            CHANNEL_LAST_IMPORT,
            CHANNEL_IMPORTED_READINGS,
            CHANNEL_LAST_ERROR,
            CHANNEL_FIRST_READING,
            CHANNEL_LAST_READING,
        ].map((channelId) => new ChannelAddress(componentId, channelId));
    }

    /**
     * Reads the widget values from the current channel data.
     *
     * @param componentId the component id
     * @param allComponents the channel values keyed by channel address
     * @returns the values
     */
    export function readValues(componentId: string, allComponents: { [channelAddress: string]: any }): Values {
        const get = (channelId: string) => allComponents[componentId + "/" + channelId] ?? null;
        return {
            importState: get(CHANNEL_IMPORT_STATE),
            lastImport: get(CHANNEL_LAST_IMPORT),
            importedReadings: get(CHANNEL_IMPORTED_READINGS),
            lastError: get(CHANNEL_LAST_ERROR),
            firstReading: get(CHANNEL_FIRST_READING),
            lastReading: get(CHANNEL_LAST_READING),
        };
    }

    /**
     * Converts the import state to a translated label.
     *
     * @param translate the translate service
     * @returns a converter
     */
    export function importStateConverter(translate: TranslateService): (value: ImportState | string | null) => string {
        return (value) => {
            switch (value) {
                case ImportState.IDLE:
                case "IDLE":
                    return translate.instant("EDGE.INDEX.WIDGETS.GREEN_BUTTON.STATE_IDLE");
                case ImportState.RUNNING:
                case "RUNNING":
                    return translate.instant("EDGE.INDEX.WIDGETS.GREEN_BUTTON.STATE_RUNNING");
                case ImportState.DONE:
                case "DONE":
                    return translate.instant("EDGE.INDEX.WIDGETS.GREEN_BUTTON.STATE_DONE");
                case ImportState.FAILED:
                case "FAILED":
                    return translate.instant("EDGE.INDEX.WIDGETS.GREEN_BUTTON.STATE_FAILED");
                default:
                    return "-";
            }
        };
    }

    /**
     * Formats an epoch-seconds timestamp as a locale date/time string.
     *
     * @param epochSeconds the epoch seconds
     * @returns the formatted string or "-"
     */
    export function formatEpochSeconds(epochSeconds: number | null | undefined): string {
        if (epochSeconds == null) {
            return "-";
        }
        return new Date(epochSeconds * 1000).toLocaleString(Language.getCurrentLanguage().i18nLocaleKey);
    }

    /**
     * Formats the imported date range.
     *
     * @param values the values
     * @returns the formatted range or "-"
     */
    export function formatRange(values: Values): string {
        if (values.firstReading == null || values.lastReading == null) {
            return "-";
        }
        const locale = Language.getCurrentLanguage().i18nLocaleKey;
        return new Date(values.firstReading * 1000).toLocaleDateString(locale)
            + " - " + new Date(values.lastReading * 1000).toLocaleDateString(locale);
    }
}
