import { TranslateService } from "@ngx-translate/core";
import { TestingUtils } from "src/app/shared/components/shared/testing/utils.spec";
import { ChannelAddress, CurrentData, EdgeConfig } from "src/app/shared/shared";
import { DefaultTypes } from "src/app/shared/type/defaulttypes";
import { FlatComponent } from "./flat/flat";
import { ModalComponent } from "./modal/modal";
import { GreenButtonUtils } from "./shared/shared";

describe("GreenButton/Import", () => {

    let translate: TranslateService;

    const COMPONENT = new EdgeConfig.Component("greenButton0", "Green Button", true, false, GreenButtonUtils.FACTORY_ID, {});
    const FIRST = Date.UTC(2026, 0, 1) / 1000;
    const LAST = Date.UTC(2026, 0, 31) / 1000;

    const CURRENT_DATA = <CurrentData>{
        allComponents: {
            "greenButton0/ImportState": "DONE",
            "greenButton0/LastImport": LAST + 3600,
            "greenButton0/ImportedReadings": 2976,
            "greenButton0/LastError": null,
            "greenButton0/FirstReading": FIRST,
            "greenButton0/LastReading": LAST,
        },
    };

    beforeEach(async () => {
        translate = (await TestingUtils.sharedSetup()).translate;
    });

    describe("FlatComponent", () => {
        it("subscribes the import status channels", () => {
            const s: any = Object.create(FlatComponent.prototype);
            s.component = COMPONENT;
            const channels: ChannelAddress[] = s.getChannelAddresses();
            expect(channels).toEqual([
                new ChannelAddress("greenButton0", "ImportState"),
                new ChannelAddress("greenButton0", "LastImport"),
                new ChannelAddress("greenButton0", "ImportedReadings"),
                new ChannelAddress("greenButton0", "LastError"),
                new ChannelAddress("greenButton0", "FirstReading"),
                new ChannelAddress("greenButton0", "LastReading"),
            ]);
        });

        it("shows import state, readings and imported range", () => {
            const s: any = Object.create(FlatComponent.prototype);
            s.component = COMPONENT;
            s.values = GreenButtonUtils.EMPTY_VALUES;
            s.onCurrentData(CURRENT_DATA);

            expect(GreenButtonUtils.importStateConverter(translate)(s.values.importState))
                .toBe(translate.instant("EDGE.INDEX.WIDGETS.GREEN_BUTTON.STATE_DONE"));
            expect(s.values.importedReadings).toBe(2976);
            expect(s.values.lastError).toBeNull();
            expect(GreenButtonUtils.formatRange(s.values)).toContain(" - ");
            expect(GreenButtonUtils.formatRange(GreenButtonUtils.EMPTY_VALUES)).toBe("-");
        });

        it("translates all import states including the failed state with error", () => {
            const convert = GreenButtonUtils.importStateConverter(translate);
            expect(convert("IDLE")).toBe(translate.instant("EDGE.INDEX.WIDGETS.GREEN_BUTTON.STATE_IDLE"));
            expect(convert("RUNNING")).toBe(translate.instant("EDGE.INDEX.WIDGETS.GREEN_BUTTON.STATE_RUNNING"));
            expect(convert("FAILED")).toBe(translate.instant("EDGE.INDEX.WIDGETS.GREEN_BUTTON.STATE_FAILED"));
            expect(convert(null)).toBe("-");
            const values = GreenButtonUtils.readValues("greenButton0", {
                "greenButton0/ImportState": "FAILED",
                "greenButton0/LastError": "HTTP 401",
            });
            expect(values.lastError).toBe("HTTP 401");
        });
    });

    describe("ModalComponent", () => {
        it("opens the consumption history for the imported range", async () => {
            const navigations: any[] = [];
            const historyPeriods: DefaultTypes.HistoryPeriod[] = [];
            const s: any = Object.create(ModalComponent.prototype);
            s.component = COMPONENT;
            s.edge = { id: "edge0" };
            s.service = {
                historyPeriod: { next: (period: DefaultTypes.HistoryPeriod) => historyPeriods.push(period) },
                periodString: DefaultTypes.PeriodString.DAY,
            };
            s.modalController = { dismiss: () => Promise.resolve(true) };
            s.router = { navigate: (commands: any[]) => navigations.push(commands) };
            s.values = GreenButtonUtils.EMPTY_VALUES;
            s.onCurrentData(CURRENT_DATA);

            expect(s.hasImportedRange).toBeTrue();
            await s.openConsumptionHistory();

            expect(historyPeriods.length).toBe(1);
            expect(historyPeriods[0].from.getTime()).toBe(FIRST * 1000);
            expect(historyPeriods[0].to.getTime()).toBe(LAST * 1000);
            expect(s.service.periodString).toBe(DefaultTypes.PeriodString.CUSTOM);
            expect(navigations).toEqual([["/device", "edge0", "history", "consumptionchart"]]);
        });

        it("does not navigate without an imported range", async () => {
            const navigations: any[] = [];
            const s: any = Object.create(ModalComponent.prototype);
            s.component = COMPONENT;
            s.edge = { id: "edge0" };
            s.router = { navigate: (commands: any[]) => navigations.push(commands) };
            s.values = GreenButtonUtils.EMPTY_VALUES;

            expect(s.hasImportedRange).toBeFalse();
            await s.openConsumptionHistory();
            expect(navigations).toEqual([]);
        });
    });
});
