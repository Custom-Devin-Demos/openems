import { ChangeDetectionStrategy, Component } from "@angular/core";
import { ReactiveFormsModule } from "@angular/forms";
import { TranslateService } from "@ngx-translate/core";
import { BaseChartDirective } from "ng2-charts";
import { NgxSpinnerModule } from "ngx-spinner";
import { CommonUiModule } from "src/app/shared/common-ui.module";
import { AbstractHistoryChart } from "src/app/shared/components/chart/abstracthistorychart";
import { ChartComponentsModule } from "src/app/shared/components/chart/chart.module";
import { HistoryDataErrorModule } from "src/app/shared/components/history-data-error/history-data-error.module";
import { ChannelAddress, ChartConstants, EdgeConfig } from "src/app/shared/shared";
import { AssertionUtils } from "src/app/shared/utils/assertions/assertions.utils";
import { ChartAxis, HistoryUtils, YAxisType } from "src/app/shared/utils/utils";
import { OpenAdrUtils } from "../../shared/shared";

@Component({
    selector: "oe-controller-api-openadr-chart",
    templateUrl: "../../../../../../../shared/components/chart/abstracthistorychart.html",
    changeDetection: ChangeDetectionStrategy.Eager,
    imports: [
        CommonUiModule,
        BaseChartDirective,
        ReactiveFormsModule,
        ChartComponentsModule,
        HistoryDataErrorModule,
        NgxSpinnerModule,
    ],
})
export class ControllerApiOpenAdrChartComponent extends AbstractHistoryChart {

    /**
     * Chart of the event windows: curtailment on/off and the active event's signal level over time.
     *
     * @param component the OpenADR component
     * @param translate the translate service
     * @returns the chart data
     */
    public static getChartData(component: EdgeConfig.Component, translate: TranslateService): HistoryUtils.ChartData {
        return {
            input: [
                {
                    name: OpenAdrUtils.CHANNEL_CURTAILMENT_ACTIVE,
                    powerChannel: new ChannelAddress(component.id, OpenAdrUtils.CHANNEL_CURTAILMENT_ACTIVE),
                },
                {
                    name: OpenAdrUtils.CHANNEL_ACTIVE_EVENT_SIGNAL_LEVEL,
                    powerChannel: new ChannelAddress(component.id, OpenAdrUtils.CHANNEL_ACTIVE_EVENT_SIGNAL_LEVEL),
                },
            ],
            output: (data: HistoryUtils.ChannelData) => [
                {
                    name: translate.instant("EDGE.INDEX.WIDGETS.OPEN_ADR.CURTAILMENT"),
                    converter: () => data[OpenAdrUtils.CHANNEL_CURTAILMENT_ACTIVE]
                        ?.map((value) => value == null ? null : (value ? 1 : 0)) ?? [],
                    color: ChartConstants.Colors.RED,
                    yAxisId: ChartAxis.LEFT,
                    stack: 0,
                },
                {
                    name: translate.instant("EDGE.INDEX.WIDGETS.OPEN_ADR.SIGNAL_LEVEL"),
                    converter: () => data[OpenAdrUtils.CHANNEL_ACTIVE_EVENT_SIGNAL_LEVEL]
                        ?.map((value) => value == null || value < 0 ? null : Math.round(value * 1000)) ?? [],
                    color: ChartConstants.Colors.BLUE_GREY,
                    yAxisId: ChartAxis.RIGHT,
                    borderDash: [10, 10],
                },
            ],
            tooltip: {
                formatNumber: "1.0-0",
            },
            yAxes: [
                {
                    unit: YAxisType.RELAY,
                    position: "left",
                    yAxisId: ChartAxis.LEFT,
                    customTitle: translate.instant("EDGE.INDEX.WIDGETS.OPEN_ADR.CURTAILMENT"),
                },
                {
                    unit: YAxisType.NONE,
                    position: "right",
                    yAxisId: ChartAxis.RIGHT,
                    customTitle: translate.instant("EDGE.INDEX.WIDGETS.OPEN_ADR.SIGNAL_LEVEL"),
                },
            ],
        };
    }

    public override getChartData(): HistoryUtils.ChartData {
        const component = this.config.getComponentSafely(this.routeService.getRouteParam<string>("componentId"));
        AssertionUtils.assertIsDefined(component);
        return ControllerApiOpenAdrChartComponent.getChartData(component, this.translate);
    }
}
