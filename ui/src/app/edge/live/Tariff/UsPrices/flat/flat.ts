// @ts-strict-ignore
import { ChangeDetectionStrategy, Component } from "@angular/core";
import { AbstractFlatWidget } from "src/app/shared/components/flat/abstract-flat-widget";
import { Modal } from "src/app/shared/components/flat/flat";
import { ComponentJsonApiRequest } from "src/app/shared/jsonrpc/request/componentJsonApiRequest";
import { GetScheduleRequest } from "src/app/shared/jsonrpc/request/getScheduleRequest";
import { GetScheduleResponse } from "src/app/shared/jsonrpc/response/getScheduleResponse";
import { ChannelAddress, CurrentData, EdgeConfig } from "src/app/shared/shared";
import { ModalComponent } from "../modal/modal";
import { UsPricesUtils } from "../shared/shared";

@Component({
    selector: "oe-tariff-us-prices",
    templateUrl: "./flat.html",
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false,
})
export class FlatComponent extends AbstractFlatWidget {

    protected modalComponent: Modal | null = null;
    protected provider: UsPricesUtils.Provider | null = null;
    protected providerDetail: string | null = null;
    protected summary: UsPricesUtils.Summary = { current: null, nextHour: null, todayMin: null, todayMax: null };
    protected lastSuccessfulUpdate: string = "-";
    protected consecutiveFailures: number | null = null;
    protected httpStatusCode: number | null = null;
    protected formatPrice: (value: number | null) => string = () => "-";

    protected override getChannelAddresses(): ChannelAddress[] {
        this.provider = UsPricesUtils.getProvider(this.component?.factoryId);
        if (this.component == null || this.provider == null) {
            return [];
        }
        return UsPricesUtils.getChannelAddresses(this.component.id, this.provider);
    }

    protected override afterIsInitialized(): void {
        this.modalComponent = {
            component: ModalComponent,
            componentProps: {
                component: this.component,
            },
        };
        const meta: EdgeConfig.Component = this.config?.getComponent("_meta");
        const currency = this.config?.getPropertyFromComponent<string>(meta, "currency") ?? null;
        this.formatPrice = UsPricesUtils.priceFormatter(currency);
        this.loadForecast();
    }

    protected override onCurrentData(currentData: CurrentData): void {
        if (this.component == null || this.provider == null) {
            return;
        }
        const id = this.component.id;
        this.httpStatusCode = currentData.allComponents[id + "/" + UsPricesUtils.CHANNEL_HTTP_STATUS_CODE] ?? null;
        this.consecutiveFailures = currentData.allComponents[id + "/" + UsPricesUtils.CHANNEL_CONSECUTIVE_FAILURES] ?? null;
        this.lastSuccessfulUpdate = UsPricesUtils.formatEpochSeconds(
            currentData.allComponents[id + "/" + UsPricesUtils.CHANNEL_LAST_SUCCESSFUL_UPDATE]);
        const detail = currentData.allComponents[id + "/" + UsPricesUtils.getProviderChannelId(this.provider)];
        this.providerDetail = detail == null ? null : String(detail);
    }

    private async loadForecast(): Promise<void> {
        const controller = UsPricesUtils.findTouController(this.config);
        if (controller == null || this.edge == null) {
            return;
        }
        try {
            const response = (await this.edge.sendRequest(
                this.websocket,
                new ComponentJsonApiRequest({ componentId: controller.id, payload: new GetScheduleRequest() }),
            )) as GetScheduleResponse;
            const hourly = UsPricesUtils.aggregateHourly(response?.result?.schedule ?? []);
            this.summary = UsPricesUtils.summarize(hourly, new Date());
        } catch {
            this.summary = { current: null, nextHour: null, todayMin: null, todayMax: null };
        }
    }
}
