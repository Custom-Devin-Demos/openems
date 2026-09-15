import { TranslateService } from "@ngx-translate/core";
import { TestingUtils } from "src/app/shared/components/shared/testing/utils.spec";
import { ComponentJsonApiRequest } from "src/app/shared/jsonrpc/request/componentJsonApiRequest";
import { SetChannelValueRequest } from "src/app/shared/jsonrpc/request/setChannelValueRequest";
import { ChannelAddress, CurrentData, EdgeConfig } from "src/app/shared/shared";
import { FlatComponent } from "./flat/flat";
import { ControllerApiOpenAdrChartComponent } from "./history/chart/chart";
import { ModalComponent } from "./modal/modal";
import { OpenAdrEvent } from "./shared/jsonrpc";
import { OpenAdrUtils } from "./shared/shared";

describe("Controller/Api/OpenAdr", () => {

    let translate: TranslateService;

    const COMPONENT = new EdgeConfig.Component("ctrlOpenAdr0", "OpenADR", true, false, OpenAdrUtils.FACTORY_ID, {});
    const NOW = new Date(2026, 5, 1, 12, 0, 0, 0);
    const NOW_S = NOW.getTime() / 1000;

    const CURRENT_DATA = <CurrentData>{
        allComponents: {
            "ctrlOpenAdr0/RegistrationState": "REGISTERED",
            "ctrlOpenAdr0/VenId": "ven-1",
            "ctrlOpenAdr0/ActiveEventId": "evt-1",
            "ctrlOpenAdr0/ActiveEventSignalLevel": 2,
            "ctrlOpenAdr0/ActiveEventPrice": 350,
            "ctrlOpenAdr0/ActiveEventStart": NOW_S - 600,
            "ctrlOpenAdr0/ActiveEventEnd": NOW_S + 1800,
            "ctrlOpenAdr0/EventCount": 1,
            "ctrlOpenAdr0/OptState": "OPT_IN",
            "ctrlOpenAdr0/CurtailmentActive": true,
            "ctrlOpenAdr0/LastPoll": NOW_S,
            "ctrlOpenAdr0/HttpStatusCode": 200,
            "ctrlOpenAdr0/CommunicationFailed": false,
        },
    };

    function stubEdge(): { edge: any; requests: any[] } {
        const requests: any[] = [];
        return {
            requests,
            edge: {
                id: "edge0",
                sendRequest: (_ws: any, request: any) => {
                    requests.push(request);
                    return Promise.resolve({ result: { events: [] } });
                },
            },
        };
    }

    function modalStub(): { modal: any; requests: any[]; toasts: string[] } {
        const { edge, requests } = stubEdge();
        const toasts: string[] = [];
        const modal: any = Object.create(ModalComponent.prototype);
        modal.component = COMPONENT;
        modal.edge = edge;
        modal.websocket = {};
        modal.translate = translate;
        modal.service = { toast: (_msg: string, level: string) => toasts.push(level) };
        modal.values = OpenAdrUtils.EMPTY_VALUES;
        return { modal, requests, toasts };
    }

    beforeEach(async () => {
        translate = (await TestingUtils.sharedSetup()).translate;
    });

    describe("FlatComponent", () => {
        it("subscribes all OpenADR status channels", () => {
            const s: any = Object.create(FlatComponent.prototype);
            s.component = COMPONENT;
            const channels: ChannelAddress[] = s.getChannelAddresses();
            expect(channels.map((c) => c.channelId)).toEqual([
                "RegistrationState", "VenId", "ActiveEventId", "ActiveEventSignalLevel", "ActiveEventPrice",
                "ActiveEventStart", "ActiveEventEnd", "EventCount", "OptState", "CurtailmentActive",
                "LastPoll", "HttpStatusCode", "CommunicationFailed",
            ]);
        });

        it("shows registration, active event, signal, countdown and curtailment", () => {
            const s: any = Object.create(FlatComponent.prototype);
            s.component = COMPONENT;
            s.translate = translate;
            s.values = OpenAdrUtils.EMPTY_VALUES;
            s.onCurrentData(CURRENT_DATA);

            expect(s.values.registrationState).toBe("REGISTERED");
            expect(OpenAdrUtils.registrationStateConverter(translate)(s.values.registrationState))
                .toBe(translate.instant("EDGE.INDEX.WIDGETS.OPEN_ADR.REGISTRATION_REGISTERED"));
            expect(OpenAdrUtils.hasActiveEvent(s.values)).toBeTrue();
            expect(OpenAdrUtils.formatSignal(s.values.activeEventSignalLevel, s.values.activeEventPrice, "USD"))
                .toBe("Level 2 / 35 ¢/kWh");
            expect(OpenAdrUtils.formatCountdown(s.values, NOW, translate))
                .toBe(translate.instant("EDGE.INDEX.WIDGETS.OPEN_ADR.ENDS_IN", { duration: "0:30:00" }));
            expect(s.values.curtailmentActive).toBeTrue();
        });

        it("shows a start countdown before the event and ENDED afterwards", () => {
            const values = OpenAdrUtils.readValues("ctrlOpenAdr0", CURRENT_DATA.allComponents);
            expect(OpenAdrUtils.formatCountdown(values, new Date(NOW.getTime() - 3600_000), translate))
                .toBe(translate.instant("EDGE.INDEX.WIDGETS.OPEN_ADR.STARTS_IN", { duration: "0:50:00" }));
            expect(OpenAdrUtils.formatCountdown(values, new Date(NOW.getTime() + 3600_000), translate))
                .toBe(translate.instant("EDGE.INDEX.WIDGETS.OPEN_ADR.ENDED"));
            expect(OpenAdrUtils.formatCountdown(OpenAdrUtils.EMPTY_VALUES, NOW, translate)).toBe("-");
        });
    });

    describe("ModalComponent", () => {
        it("loads active events via JSON-RPC getActiveEvents", async () => {
            const { modal, requests } = modalStub();
            const event: OpenAdrEvent = {
                eventId: "evt-1", signalType: "SIMPLE", level: 2, price: null,
                start: NOW_S - 600, end: NOW_S + 1800, optState: "OPT_IN",
            };
            modal.edge.sendRequest = (_ws: any, request: any) => {
                requests.push(request);
                return Promise.resolve({ result: { events: [event] } });
            };
            await modal.loadEvents();

            expect(requests.length).toBe(1);
            const request = requests[0] as ComponentJsonApiRequest;
            expect(request.params.componentId).toBe("ctrlOpenAdr0");
            expect(request.params.payload.method).toBe("getActiveEvents");
            expect(modal.events).toEqual([event]);
            expect(modal.eventsError).toBeFalse();
            expect(OpenAdrUtils.formatEventWindow(event)).toContain(" - ");
        });

        it("opt-in toggle writes OPT_IN / OPT_OUT to channel SetOptState", async () => {
            const { modal, requests, toasts } = modalStub();
            await modal.setGlobalOptState(false);
            await modal.setGlobalOptState(true);

            expect(requests.length).toBe(2);
            const optOut = requests[0] as SetChannelValueRequest;
            expect(optOut.method).toBe("setChannelValue");
            expect(optOut.params).toEqual({ componentId: "ctrlOpenAdr0", channelId: "SetOptState", value: "OPT_OUT" });
            expect((requests[1] as SetChannelValueRequest).params.value).toBe("OPT_IN");
            expect(toasts).toEqual(["success", "success"]);
        });

        it("per-event opt state calls JSON-RPC setOptState and updates the event", async () => {
            const { modal, requests } = modalStub();
            const event: OpenAdrEvent = {
                eventId: "evt-1", signalType: "PRICE", level: -1, price: 350,
                start: NOW_S, end: NOW_S + 900, optState: "OPT_IN",
            };
            await modal.setEventOptState(event, "OPT_OUT");

            const request = requests[0] as ComponentJsonApiRequest;
            expect(request.params.payload.method).toBe("setOptState");
            expect(request.params.payload.params).toEqual({ eventId: "evt-1", optState: "OPT_OUT" });
            expect(event.optState).toBe("OPT_OUT");
        });

        it("shows a failure toast when the write is rejected", async () => {
            const { modal, toasts } = modalStub();
            modal.edge.sendRequest = () => Promise.reject(new Error("rejected"));
            await modal.setGlobalOptState(true);
            expect(toasts).toEqual(["danger"]);
        });
    });

    describe("History chart", () => {
        it("maps curtailment and signal level channels to event window datasets", () => {
            const chartData = ControllerApiOpenAdrChartComponent.getChartData(COMPONENT, translate);
            expect(chartData.input.map((el) => el.powerChannel.toString())).toEqual([
                "ctrlOpenAdr0/CurtailmentActive", "ctrlOpenAdr0/ActiveEventSignalLevel",
            ]);
            const output = chartData.output({
                CurtailmentActive: [null, 0, 1, 1, 0],
                ActiveEventSignalLevel: [-1, -1, 2, 2, -1],
            });
            expect(output[0].converter()).toEqual([null, 0, 1, 1, 0]);
            expect(output[1].converter()).toEqual([null, null, 2, 2, null]);
            expect(chartData.yAxes.length).toBe(2);
        });
    });
});
