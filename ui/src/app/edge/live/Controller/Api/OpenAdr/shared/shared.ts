import { formatNumber } from "@angular/common";
import { TranslateService } from "@ngx-translate/core";
import { ChannelAddress, Currency } from "src/app/shared/shared";
import { Language } from "src/app/shared/type/language";
import { OpenAdrEvent, OpenAdrOptState } from "./jsonrpc";

export namespace OpenAdrUtils {

    export const FACTORY_ID = "Controller.Api.OpenADR";

    export const CHANNEL_REGISTRATION_STATE = "RegistrationState";
    export const CHANNEL_VEN_ID = "VenId";
    export const CHANNEL_ACTIVE_EVENT_ID = "ActiveEventId";
    export const CHANNEL_ACTIVE_EVENT_SIGNAL_LEVEL = "ActiveEventSignalLevel";
    export const CHANNEL_ACTIVE_EVENT_PRICE = "ActiveEventPrice";
    export const CHANNEL_ACTIVE_EVENT_START = "ActiveEventStart";
    export const CHANNEL_ACTIVE_EVENT_END = "ActiveEventEnd";
    export const CHANNEL_EVENT_COUNT = "EventCount";
    export const CHANNEL_OPT_STATE = "OptState";
    export const CHANNEL_SET_OPT_STATE = "SetOptState";
    export const CHANNEL_CURTAILMENT_ACTIVE = "CurtailmentActive";
    export const CHANNEL_LAST_POLL = "LastPoll";
    export const CHANNEL_HTTP_STATUS_CODE = "HttpStatusCode";
    export const CHANNEL_COMMUNICATION_FAILED = "CommunicationFailed";

    export enum RegistrationState {
        UNREGISTERED = 0,
        REGISTERING = 1,
        REGISTERED = 2,
        FAILED = 3,
    }

    export enum OptState {
        OPT_IN = 0,
        OPT_OUT = 1,
    }

    export type Values = {
        registrationState: RegistrationState | string | null;
        venId: string | null;
        activeEventId: string | null;
        activeEventSignalLevel: number | null;
        activeEventPrice: number | null;
        activeEventStart: number | null;
        activeEventEnd: number | null;
        eventCount: number | null;
        optState: OptState | string | null;
        curtailmentActive: boolean | null;
        lastPoll: number | null;
        httpStatusCode: number | null;
        communicationFailed: boolean | null;
    };

    export const EMPTY_VALUES: Values = {
        registrationState: null,
        venId: null,
        activeEventId: null,
        activeEventSignalLevel: null,
        activeEventPrice: null,
        activeEventStart: null,
        activeEventEnd: null,
        eventCount: null,
        optState: null,
        curtailmentActive: null,
        lastPoll: null,
        httpStatusCode: null,
        communicationFailed: null,
    };

    /**
     * Gets the channel addresses subscribed by flat widget and modal.
     *
     * @param componentId the component id
     * @returns the channel addresses
     */
    export function getChannelAddresses(componentId: string): ChannelAddress[] {
        return [
            CHANNEL_REGISTRATION_STATE,
            CHANNEL_VEN_ID,
            CHANNEL_ACTIVE_EVENT_ID,
            CHANNEL_ACTIVE_EVENT_SIGNAL_LEVEL,
            CHANNEL_ACTIVE_EVENT_PRICE,
            CHANNEL_ACTIVE_EVENT_START,
            CHANNEL_ACTIVE_EVENT_END,
            CHANNEL_EVENT_COUNT,
            CHANNEL_OPT_STATE,
            CHANNEL_CURTAILMENT_ACTIVE,
            CHANNEL_LAST_POLL,
            CHANNEL_HTTP_STATUS_CODE,
            CHANNEL_COMMUNICATION_FAILED,
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
        const toBoolean = (value: any): boolean | null => value == null ? null : (value === true || value === 1);
        return {
            registrationState: get(CHANNEL_REGISTRATION_STATE),
            venId: get(CHANNEL_VEN_ID),
            activeEventId: get(CHANNEL_ACTIVE_EVENT_ID),
            activeEventSignalLevel: get(CHANNEL_ACTIVE_EVENT_SIGNAL_LEVEL),
            activeEventPrice: get(CHANNEL_ACTIVE_EVENT_PRICE),
            activeEventStart: get(CHANNEL_ACTIVE_EVENT_START),
            activeEventEnd: get(CHANNEL_ACTIVE_EVENT_END),
            eventCount: get(CHANNEL_EVENT_COUNT),
            optState: get(CHANNEL_OPT_STATE),
            curtailmentActive: toBoolean(get(CHANNEL_CURTAILMENT_ACTIVE)),
            lastPoll: get(CHANNEL_LAST_POLL),
            httpStatusCode: get(CHANNEL_HTTP_STATUS_CODE),
            communicationFailed: toBoolean(get(CHANNEL_COMMUNICATION_FAILED)),
        };
    }

    /**
     * Whether an event is currently active (id set and signal level >= 0 or price set).
     *
     * @param values the values
     * @returns true if active
     */
    export function hasActiveEvent(values: Values): boolean {
        return values.activeEventId != null && values.activeEventId !== "";
    }

    /**
     * Converts the registration state to a translated label.
     *
     * @param translate the translate service
     * @returns a converter
     */
    export function registrationStateConverter(translate: TranslateService): (value: RegistrationState | string | null) => string {
        return (value) => {
            switch (value) {
                case RegistrationState.UNREGISTERED:
                case "UNREGISTERED":
                    return translate.instant("EDGE.INDEX.WIDGETS.OPEN_ADR.REGISTRATION_UNREGISTERED");
                case RegistrationState.REGISTERING:
                case "REGISTERING":
                    return translate.instant("EDGE.INDEX.WIDGETS.OPEN_ADR.REGISTRATION_REGISTERING");
                case RegistrationState.REGISTERED:
                case "REGISTERED":
                    return translate.instant("EDGE.INDEX.WIDGETS.OPEN_ADR.REGISTRATION_REGISTERED");
                case RegistrationState.FAILED:
                case "FAILED":
                    return translate.instant("EDGE.INDEX.WIDGETS.OPEN_ADR.REGISTRATION_FAILED");
                default:
                    return "-";
            }
        };
    }

    /**
     * Normalizes the opt state channel value (enum ordinal or name) to its name.
     *
     * @param value the raw value
     * @returns "OPT_IN", "OPT_OUT" or null
     */
    export function toOptState(value: OptState | string | null | undefined): OpenAdrOptState | null {
        switch (value) {
            case OptState.OPT_IN:
            case "OPT_IN":
                return "OPT_IN";
            case OptState.OPT_OUT:
            case "OPT_OUT":
                return "OPT_OUT";
            default:
                return null;
        }
    }

    /**
     * Converts the opt state to a translated label.
     *
     * @param translate the translate service
     * @returns a converter
     */
    export function optStateConverter(translate: TranslateService): (value: OptState | string | null) => string {
        return (value) => {
            switch (toOptState(value)) {
                case "OPT_IN":
                    return translate.instant("EDGE.INDEX.WIDGETS.OPEN_ADR.OPT_IN");
                case "OPT_OUT":
                    return translate.instant("EDGE.INDEX.WIDGETS.OPEN_ADR.OPT_OUT");
                default:
                    return "-";
            }
        };
    }

    /**
     * Formats the signal: level for SIMPLE signals, price (Currency/MWh -> cent-scale per kWh) for PRICE signals.
     *
     * @param level the signal level (-1 or null = none)
     * @param price the price in Currency/MWh
     * @param currency the currency code
     * @returns the formatted signal
     */
    export function formatSignal(level: number | null, price: number | null, currency: string | null): string {
        const parts: string[] = [];
        if (level != null && level >= 0) {
            parts.push("Level " + level);
        }
        if (price != null) {
            const locale = Language.getCurrentLanguage().i18nLocaleKey;
            parts.push(formatNumber(price / 10, locale, "1.0-2") + " " + Currency.getCurrencyLabelByCurrency(currency));
        }
        return parts.length === 0 ? "-" : parts.join(" / ");
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
     * Formats a duration in seconds as "h:mm:ss".
     *
     * @param seconds the duration
     * @returns the formatted duration
     */
    export function formatDuration(seconds: number): string {
        const total = Math.max(0, Math.floor(seconds));
        const h = Math.floor(total / 3600);
        const m = Math.floor((total % 3600) / 60);
        const s = total % 60;
        return h + ":" + String(m).padStart(2, "0") + ":" + String(s).padStart(2, "0");
    }

    /**
     * Builds the countdown text for the active event: time until start, or time until end if running.
     *
     * @param values the values
     * @param now the reference time
     * @param translate the translate service
     * @returns the countdown text
     */
    export function formatCountdown(values: Values, now: Date, translate: TranslateService): string {
        if (!hasActiveEvent(values) || values.activeEventStart == null || values.activeEventEnd == null) {
            return "-";
        }
        const nowSeconds = now.getTime() / 1000;
        if (nowSeconds < values.activeEventStart) {
            return translate.instant("EDGE.INDEX.WIDGETS.OPEN_ADR.STARTS_IN", { duration: formatDuration(values.activeEventStart - nowSeconds) });
        }
        if (nowSeconds < values.activeEventEnd) {
            return translate.instant("EDGE.INDEX.WIDGETS.OPEN_ADR.ENDS_IN", { duration: formatDuration(values.activeEventEnd - nowSeconds) });
        }
        return translate.instant("EDGE.INDEX.WIDGETS.OPEN_ADR.ENDED");
    }

    /**
     * Parses an event start/end (ISO string or epoch seconds) to a Date.
     *
     * @param value the raw value
     * @returns the date or null
     */
    export function toDate(value: string | number | null | undefined): Date | null {
        if (value == null) {
            return null;
        }
        const date = typeof value === "number" ? new Date(value * 1000) : new Date(value);
        return isNaN(date.getTime()) ? null : date;
    }

    /**
     * Formats an event window as "start - end".
     *
     * @param event the event
     * @returns the formatted window
     */
    export function formatEventWindow(event: OpenAdrEvent): string {
        const locale = Language.getCurrentLanguage().i18nLocaleKey;
        const start = toDate(event.start);
        const end = toDate(event.end);
        return (start?.toLocaleString(locale) ?? "-") + " - " + (end?.toLocaleString(locale) ?? "-");
    }
}
