import { TranslateService } from "@ngx-translate/core";
import { TestingUtils } from "src/app/shared/components/shared/testing/utils.spec";
import { ChannelAddress, Currency, CurrentData, EdgeConfig } from "src/app/shared/shared";
import { FlatComponent } from "./flat/flat";
import { ModalComponent } from "./modal/modal";
import { UsPricesUtils } from "./shared/shared";

describe("Tariff/UsPrices", () => {

    let translate: TranslateService;

    const COMED = new EdgeConfig.Component("timeOfUseTariff0", "ComEd", true, false, UsPricesUtils.FACTORY_COMED, {});
    const PJM = new EdgeConfig.Component("timeOfUseTariff1", "PJM", true, false, UsPricesUtils.FACTORY_PJM, {});

    /** 2026-06-01T10:00 local as reference "now" */
    const NOW = new Date(2026, 5, 1, 10, 30, 0, 0);

    function hour(offset: number): Date {
        return new Date(2026, 5, 1, 10 + offset, 0, 0, 0);
    }

    function quarterSchedule(): { timestamp: string; price: number | null }[] {
        const entries: { timestamp: string; price: number | null }[] = [];
        // 10:00 -> 40, 44, 36, 40 (avg 40); 11:00 -> 60 x4; 12:00 -> 20 x4
        const prices = [[40, 44, 36, 40], [60, 60, 60, 60], [20, 20, 20, 20]];
        prices.forEach((quarters, h) => quarters.forEach((price, q) => {
            entries.push({ timestamp: new Date(2026, 5, 1, 10 + h, q * 15).toISOString(), price: price });
        }));
        entries.push({ timestamp: new Date(2026, 5, 1, 13, 0).toISOString(), price: null });
        return entries;
    }

    beforeEach(async () => {
        translate = (await TestingUtils.sharedSetup()).translate;
    });

    describe("Currency USD", () => {
        it("uses ¢/kWh as price label and ¢ as chart unit for USD", () => {
            expect(Currency.getCurrencyLabelByCurrency("USD")).toBe(Currency.Label.US_CENT_PER_KWH);
            expect(Currency.getChartCurrencyUnitLabel("USD")).toBe(Currency.Unit.US_CENT);
            expect(Currency.getCurrencyLabelByCurrency("EUR")).toBe(Currency.Label.CENT_PER_KWH);
        });

        it("formats USD/MWh prices as ¢/kWh by dividing by 10", () => {
            const format = UsPricesUtils.priceFormatter("USD");
            expect(format(42.5)).toMatch(/^4[.,]25 ¢\/kWh$/);
            expect(format(0)).toBe("0 ¢/kWh");
            expect(format(null)).toBe("-");
        });

        it("uses $ as USD currency symbol for the source unit", () => {
            expect(Currency.getCurrencySymbol("USD")).toBe("$");
            expect(UsPricesUtils.getSourceUnit("USD")).toBe("$/MWh");
            expect(UsPricesUtils.getSourceUnit("EUR")).toBe("€/MWh");
            expect(UsPricesUtils.getSourceUnit("SEK")).toBe("kr/MWh");
        });
    });

    describe("UsPricesUtils", () => {
        it("resolves the provider and provider channel from the factory id", () => {
            expect(UsPricesUtils.getProvider(UsPricesUtils.FACTORY_COMED)).toBe("COMED");
            expect(UsPricesUtils.getProvider(UsPricesUtils.FACTORY_PJM)).toBe("PJM");
            expect(UsPricesUtils.getProvider("TimeOfUseTariff.Awattar")).toBeNull();
            expect(UsPricesUtils.getProviderChannelId("COMED")).toBe("Feed");
            expect(UsPricesUtils.getProviderChannelId("PJM")).toBe("PnodeId");
        });

        it("aggregates quarterly schedule prices into hourly averages and skips null prices", () => {
            const hourly = UsPricesUtils.aggregateHourly(quarterSchedule());
            expect(hourly.length).toBe(3);
            expect(hourly.map((el) => el.timestamp.getTime())).toEqual([hour(0), hour(1), hour(2)].map((d) => d.getTime()));
            expect(hourly.map((el) => el.price)).toEqual([40, 60, 20]);
        });

        it("summarizes current, next-hour and today's min/max price", () => {
            const summary = UsPricesUtils.summarize(UsPricesUtils.aggregateHourly(quarterSchedule()), NOW);
            expect(summary).toEqual({ current: 40, nextHour: 60, todayMin: 20, todayMax: 60 });
        });

        it("maps hourly prices to a bar chart dataset in ¢/kWh", () => {
            const hourly = UsPricesUtils.aggregateHourly(quarterSchedule());
            const chart = UsPricesUtils.getForecastChartData(hourly, translate, "USD");
            expect(chart.labels).toEqual([hour(0), hour(1), hour(2)]);
            expect(chart.datasets.length).toBe(1);
            expect(chart.datasets[0].data).toEqual([4, 6, 2]);
            expect(chart.datasets[0].label).toContain("¢");
        });
    });

    describe("FlatComponent", () => {
        function stub(component: EdgeConfig.Component): any {
            const s: any = Object.create(FlatComponent.prototype);
            s.component = component;
            s.formatPrice = UsPricesUtils.priceFormatter("USD");
            return s;
        }

        it("subscribes the status channels and the ComEd feed channel", () => {
            const s = stub(COMED);
            const channels: ChannelAddress[] = s.getChannelAddresses();
            expect(channels).toEqual([
                new ChannelAddress("timeOfUseTariff0", "HttpStatusCode"),
                new ChannelAddress("timeOfUseTariff0", "LastSuccessfulUpdate"),
                new ChannelAddress("timeOfUseTariff0", "ConsecutiveFailures"),
                new ChannelAddress("timeOfUseTariff0", "Feed"),
            ]);
            expect(s.provider).toBe("COMED");
        });

        it("shows PJM node id, status code and failures from current data", () => {
            const s = stub(PJM);
            s.getChannelAddresses();
            s.onCurrentData(<CurrentData>{
                allComponents: {
                    "timeOfUseTariff1/HttpStatusCode": 200,
                    "timeOfUseTariff1/ConsecutiveFailures": 2,
                    "timeOfUseTariff1/LastSuccessfulUpdate": 1780000000,
                    "timeOfUseTariff1/PnodeId": 33092371,
                },
            });
            expect(s.provider).toBe("PJM");
            expect(s.providerDetail).toBe("33092371");
            expect(s.httpStatusCode).toBe(200);
            expect(s.consecutiveFailures).toBe(2);
            expect(s.lastSuccessfulUpdate).not.toBe("-");
        });
    });

    describe("ModalComponent", () => {
        it("slices the hourly forecast to 24 h or 48 h", () => {
            const s: any = Object.create(ModalComponent.prototype);
            s.hourly = Array.from({ length: 60 }, (_, i) => ({ timestamp: hour(i), price: i }));
            s.chartHours = 24;
            expect(s.visibleHourly.length).toBe(24);
            s.setChartHours(48);
            expect(s.visibleHourly.length).toBe(48);
            expect(s.visibleHourly[47].price).toBe(47);
        });
    });
});
