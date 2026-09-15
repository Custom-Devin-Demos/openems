// @ts-strict-ignore
import { ChangeDetectionStrategy, Component } from "@angular/core";
import { AbstractModal } from "src/app/shared/components/modal/abstractModal";
import { ComponentJsonApiRequest } from "src/app/shared/jsonrpc/request/componentJsonApiRequest";
import { GetScheduleRequest } from "src/app/shared/jsonrpc/request/getScheduleRequest";
import { GetScheduleResponse } from "src/app/shared/jsonrpc/response/getScheduleResponse";
import { ChannelAddress, CurrentData, EdgeConfig } from "src/app/shared/shared";
import { UsPricesUtils } from "../shared/shared";

@Component({
    selector: "oe-tariff-us-prices-modal",
    templateUrl: "./modal.html",
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: false,
})
export class ModalComponent extends AbstractModal {

    protected provider: UsPricesUtils.Provider | null = null;
    protected providerDetail: string | null = null;
    protected currency: string | null = null;
    protected hourly: UsPricesUtils.HourlyPrice[] = [];
    protected summary: UsPricesUtils.Summary = { current: null, nextHour: null, todayMin: null, todayMax: null };
    protected lastSuccessfulUpdate: string = "-";
    protected consecutiveFailures: number | null = null;
    protected httpStatusCode: number | null = null;
    protected hasTouController: boolean = false;
    protected chartHours: 24 | 48 = 24;

    protected get visibleHourly(): UsPricesUtils.HourlyPrice[] {
        return this.hourly.slice(0, this.chartHours);
    }

    protected formatPrice: (value: number | null) => string = () => "-";

    protected override getChannelAddresses(): ChannelAddress[] {
        this.provider = UsPricesUtils.getProvider(this.component?.factoryId);
        if (this.component == null || this.provider == null) {
            return [];
        }
        return UsPricesUtils.getChannelAddresses(this.component.id, this.provider);
    }

    protected override onIsInitialized(): void {
        const meta: EdgeConfig.Component = this.config?.getComponent("_meta");
        this.currency = this.config?.getPropertyFromComponent<string>(meta, "currency") ?? null;
        this.formatPrice = UsPricesUtils.priceFormatter(this.currency);
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

    protected setChartHours(hours: 24 | 48): void {
        this.chartHours = hours;
    }

    private async loadForecast(): Promise<void> {
        const controller = UsPricesUtils.findTouController(this.config);
        this.hasTouController = controller != null;
        if (controller == null || this.edge == null) {
            return;
        }
        try {
            const response = (await this.edge.sendRequest(
                this.websocket,
                new ComponentJsonApiRequest({ componentId: controller.id, payload: new GetScheduleRequest() }),
            )) as GetScheduleResponse;
            this.hourly = UsPricesUtils.aggregateHourly(response?.result?.schedule ?? []);
            this.summary = UsPricesUtils.summarize(this.hourly, new Date());
        } catch {
            this.hourly = [];
        }
    }
}
