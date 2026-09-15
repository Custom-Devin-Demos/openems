import { formatNumber } from "@angular/common";
import { TranslateService } from "@ngx-translate/core";
import { ChartDataset } from "chart.js";
import { ChartConstants } from "src/app/shared/components/chart/chart.constants";
import { ChannelAddress, Currency, EdgeConfig } from "src/app/shared/shared";
import { Language } from "src/app/shared/type/language";
import { TimeOfUseTariffUtils } from "src/app/shared/utils/utils";

export namespace UsPricesUtils {

    export const FACTORY_COMED = "TimeOfUseTariff.ComEd";
    export const FACTORY_PJM = "TimeOfUseTariff.PJM";
    export const FACTORY_TOU_CONTROLLER = "Controller.Ess.Time-Of-Use-Tariff";

    export const CHANNEL_HTTP_STATUS_CODE = "HttpStatusCode";
    export const CHANNEL_LAST_SUCCESSFUL_UPDATE = "LastSuccessfulUpdate";
    export const CHANNEL_CONSECUTIVE_FAILURES = "ConsecutiveFailures";
    export const CHANNEL_FEED = "Feed";
    export const CHANNEL_PNODE_ID = "PnodeId";

    export type Provider = "COMED" | "PJM";

    export type HourlyPrice = {
        /** start of the hour */
        timestamp: Date;
        /** price in Currency/MWh */
        price: number;
    };

    export type Summary = {
        current: number | null;
        nextHour: number | null;
        todayMin: number | null;
        todayMax: number | null;
    };

    /**
     * Gets the provider for a factory id.
     *
     * @param factoryId the factory id
     * @returns the provider or null
     */
    export function getProvider(factoryId: string | null | undefined): Provider | null {
        switch (factoryId) {
            case FACTORY_COMED:
                return "COMED";
            case FACTORY_PJM:
                return "PJM";
            default:
                return null;
        }
    }

    /**
     * Gets the channel id holding the provider detail (ComEd feed or PJM pricing node).
     *
     * @param provider the provider
     * @returns the channel id
     */
    export function getProviderChannelId(provider: Provider): string {
        return provider === "PJM" ? CHANNEL_PNODE_ID : CHANNEL_FEED;
    }

    /**
     * Gets the channel addresses subscribed by flat widget and modal.
     *
     * @param componentId the component id
     * @param provider the provider
     * @returns the channel addresses
     */
    export function getChannelAddresses(componentId: string, provider: Provider): ChannelAddress[] {
        return [
            new ChannelAddress(componentId, CHANNEL_HTTP_STATUS_CODE),
            new ChannelAddress(componentId, CHANNEL_LAST_SUCCESSFUL_UPDATE),
            new ChannelAddress(componentId, CHANNEL_CONSECUTIVE_FAILURES),
            new ChannelAddress(componentId, getProviderChannelId(provider)),
        ];
    }

    /**
     * Finds the Time-of-Use-Tariff controller which exposes the price schedule via JSON-RPC.
     *
     * @param config the edge config
     * @returns the controller component or null
     */
    export function findTouController(config: EdgeConfig | null): EdgeConfig.Component | null {
        if (config == null) {
            return null;
        }
        const ids = config.getComponentIdsByFactory(FACTORY_TOU_CONTROLLER);
        if (ids.length === 0) {
            return null;
        }
        return config.components[ids[0]] ?? null;
    }

    /**
     * Aggregates quarterly schedule entries into hourly averages.
     *
     * @param schedule entries with timestamp and price in Currency/MWh
     * @returns hourly prices sorted by time
     */
    export function aggregateHourly(schedule: { timestamp: string; price: number | null }[]): HourlyPrice[] {
        const buckets = new Map<number, { sum: number; count: number }>();
        for (const entry of schedule) {
            if (entry.price == null || entry.timestamp == null) {
                continue;
            }
            const date = new Date(entry.timestamp);
            if (isNaN(date.getTime())) {
                continue;
            }
            date.setMinutes(0, 0, 0);
            const key = date.getTime();
            const bucket = buckets.get(key) ?? { sum: 0, count: 0 };
            bucket.sum += entry.price;
            bucket.count += 1;
            buckets.set(key, bucket);
        }
        return Array.from(buckets.entries())
            .sort(([a], [b]) => a - b)
            .map(([key, bucket]) => ({ timestamp: new Date(key), price: bucket.sum / bucket.count }));
    }

    /**
     * Summarizes hourly prices: current hour, next hour, today's min and max.
     *
     * @param hourly the hourly prices
     * @param now the reference time
     * @returns the summary
     */
    export function summarize(hourly: HourlyPrice[], now: Date): Summary {
        const currentHour = new Date(now);
        currentHour.setMinutes(0, 0, 0);
        const nextHour = new Date(currentHour.getTime() + 60 * 60 * 1000);

        const today = hourly.filter((el) =>
            el.timestamp.getFullYear() === now.getFullYear()
            && el.timestamp.getMonth() === now.getMonth()
            && el.timestamp.getDate() === now.getDate());

        const find = (date: Date) => hourly.find((el) => el.timestamp.getTime() === date.getTime())?.price ?? null;

        return {
            current: find(currentHour),
            nextHour: find(nextHour),
            todayMin: today.length > 0 ? Math.min(...today.map((el) => el.price)) : null,
            todayMax: today.length > 0 ? Math.max(...today.map((el) => el.price)) : null,
        };
    }

    /**
     * Formats a price given in Currency/MWh as cent-scale per kWh (divide by 10).
     *
     * @param currency the currency code, e.g. "USD"
     * @returns a converter
     */
    export function priceFormatter(currency: string | null): (value: number | null | undefined) => string {
        const label = Currency.getCurrencyLabelByCurrency(currency);
        const locale = Language.getCurrentLanguage().i18nLocaleKey;
        return (value) => (value == null ? "-" : formatNumber(value / 10, locale, "1.0-2") + " " + label);
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
     * Maps hourly prices to a bar chart dataset (values in cent-scale per kWh).
     *
     * @param hourly the hourly prices
     * @param translate the translate service
     * @param currency the currency code
     * @returns labels and datasets
     */
    export function getForecastChartData(
        hourly: HourlyPrice[],
        translate: TranslateService,
        currency: string | null,
    ): { labels: Date[]; datasets: ChartDataset<"bar", (number | null)[]>[] } {
        const unit = Currency.getChartCurrencyUnitLabel(currency ?? "");
        return {
            labels: hourly.map((el) => el.timestamp),
            datasets: [
                {
                    label: translate.instant("EDGE.INDEX.WIDGETS.US_PRICES.PRICE_PER_KWH", { unit: unit }),
                    data: hourly.map((el) => TimeOfUseTariffUtils.formatPrice(el.price)),
                    backgroundColor: ChartConstants.Colors.BLUE_GREY,
                    borderColor: ChartConstants.Colors.BLUE_GREY,
                },
            ],
        };
    }
}
