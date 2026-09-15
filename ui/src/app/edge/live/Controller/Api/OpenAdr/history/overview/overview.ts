import { ChangeDetectionStrategy, Component } from "@angular/core";
import { ReactiveFormsModule } from "@angular/forms";
import { IonicModule } from "@ionic/angular";
import { TranslateModule } from "@ngx-translate/core";
import { AbstractHistoryChartOverview } from "src/app/shared/components/chart/abstractHistoryChartOverview";
import { ChartComponentsModule } from "src/app/shared/components/chart/chart.module";
import { HistoryDataErrorModule } from "src/app/shared/components/history-data-error/history-data-error.module";
import { PickdateComponentModule } from "src/app/shared/components/pickdate/pickdate.module";
import { ControllerApiOpenAdrChartComponent } from "../chart/chart";

@Component({
    template: `
        @if (isInitialized) {
            <oe-chart [title]="component.alias">
                <oe-controller-api-openadr-chart [isOnlyChart]="true" [component]="component"></oe-controller-api-openadr-chart>
            </oe-chart>
        }
    `,
    standalone: true,
    changeDetection: ChangeDetectionStrategy.Eager,
    imports: [
        ReactiveFormsModule,
        IonicModule,
        TranslateModule,
        ChartComponentsModule,
        PickdateComponentModule,
        HistoryDataErrorModule,
        ControllerApiOpenAdrChartComponent,
    ],
})
export class ControllerApiOpenAdrOverviewComponent extends AbstractHistoryChartOverview { }
