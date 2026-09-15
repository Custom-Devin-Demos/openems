// @ts-strict-ignore
import { ChangeDetectionStrategy, Component, OnDestroy } from "@angular/core";
import { AbstractModal } from "src/app/shared/components/modal/abstractModal";
import { ComponentJsonApiRequest } from "src/app/shared/jsonrpc/request/componentJsonApiRequest";
import { SetChannelValueRequest } from "src/app/shared/jsonrpc/request/setChannelValueRequest";
import { ChannelAddress, CurrentData, EdgeConfig } from "src/app/shared/shared";
import { GetActiveEventsRequest, GetActiveEventsResponse, OpenAdrEvent, OpenAdrOptState, SetOptStateRequest } from "../shared/jsonrpc";
import { OpenAdrUtils } from "../shared/shared";

@Component({
    selector: "oe-controller-api-openadr-modal",
    templateUrl: "./modal.html",
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: false,
})
export class ModalComponent extends AbstractModal implements OnDestroy {

    protected readonly OpenAdrUtils = OpenAdrUtils;
    protected values: OpenAdrUtils.Values = OpenAdrUtils.EMPTY_VALUES;
    protected currency: string | null = null;
    protected countdown: string = "-";
    protected events: OpenAdrEvent[] = [];
    protected eventsLoading: boolean = false;
    protected eventsError: boolean = false;
    protected convertRegistrationState = OpenAdrUtils.registrationStateConverter(this.translate);
    protected convertOptState = OpenAdrUtils.optStateConverter(this.translate);
    private countdownTimer: ReturnType<typeof setInterval> | null = null;

    /** Whether the VEN is currently opted in (channel `OptState`). */
    protected get isOptedIn(): boolean {
        return OpenAdrUtils.toOptState(this.values.optState) === "OPT_IN";
    }

    public override ngOnDestroy(): void {
        if (this.countdownTimer != null) {
            clearInterval(this.countdownTimer);
        }
        super.ngOnDestroy();
    }

    protected override getChannelAddresses(): ChannelAddress[] {
        return this.component == null ? [] : OpenAdrUtils.getChannelAddresses(this.component.id);
    }

    protected override onIsInitialized(): void {
        const meta: EdgeConfig.Component = this.config?.getComponent("_meta");
        this.currency = this.config?.getPropertyFromComponent<string>(meta, "currency") ?? null;
        this.countdownTimer = setInterval(() => this.updateCountdown(), 1000);
        this.loadEvents();
    }

    protected override onCurrentData(currentData: CurrentData): void {
        if (this.component == null) {
            return;
        }
        this.values = OpenAdrUtils.readValues(this.component.id, currentData.allComponents);
        this.updateCountdown();
    }


    /**
     * Writes the global opt state to channel `SetOptState`.
     *
     * @param optIn true for OPT_IN, false for OPT_OUT
     */
    protected async setGlobalOptState(optIn: boolean): Promise<void> {
        if (this.edge == null || this.component == null) {
            return;
        }
        const optState: OpenAdrOptState = optIn ? "OPT_IN" : "OPT_OUT";
        try {
            await this.edge.sendRequest(this.websocket, new SetChannelValueRequest({
                componentId: this.component.id,
                channelId: OpenAdrUtils.CHANNEL_SET_OPT_STATE,
                value: optState,
            }));
            this.service.toast(this.translate.instant("EDGE.INDEX.WIDGETS.OPEN_ADR.OPT_STATE_UPDATED"), "success");
        } catch {
            this.service.toast(this.translate.instant("GENERAL.CHANGE_FAILED"), "danger");
        }
    }

    /**
     * Opts in or out of a single event via JSON-RPC `setOptState`.
     *
     * @param event the event
     * @param optState the new opt state
     */
    protected async setEventOptState(event: OpenAdrEvent, optState: OpenAdrOptState): Promise<void> {
        if (this.edge == null || this.component == null) {
            return;
        }
        try {
            await this.edge.sendRequest(this.websocket, new ComponentJsonApiRequest({
                componentId: this.component.id,
                payload: new SetOptStateRequest({ eventId: event.eventId, optState: optState }),
            }));
            event.optState = optState;
            this.service.toast(this.translate.instant("EDGE.INDEX.WIDGETS.OPEN_ADR.OPT_STATE_UPDATED"), "success");
        } catch {
            this.service.toast(this.translate.instant("GENERAL.CHANGE_FAILED"), "danger");
        }
    }

    protected async loadEvents(): Promise<void> {
        if (this.edge == null || this.component == null) {
            return;
        }
        this.eventsLoading = true;
        this.eventsError = false;
        try {
            const response = (await this.edge.sendRequest(this.websocket, new ComponentJsonApiRequest({
                componentId: this.component.id,
                payload: new GetActiveEventsRequest(),
            }))) as GetActiveEventsResponse;
            this.events = response?.result?.events ?? [];
        } catch {
            this.events = [];
            this.eventsError = true;
        } finally {
            this.eventsLoading = false;
        }
    }

    private updateCountdown(): void {
        this.countdown = OpenAdrUtils.formatCountdown(this.values, new Date(), this.translate);
    }
}
