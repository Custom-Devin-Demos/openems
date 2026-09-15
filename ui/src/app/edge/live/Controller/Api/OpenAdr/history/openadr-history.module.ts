import { NgModule } from "@angular/core";
import { BrowserModule } from "@angular/platform-browser";
import { SharedModule } from "src/app/shared/shared.module";
import { ControllerApiOpenAdrChartComponent } from "./chart/chart";
import { ControllerApiOpenAdrHistoryFlatComponent } from "./flat/flat";
import { ControllerApiOpenAdrOverviewComponent } from "./overview/overview";

@NgModule({
    imports: [
        BrowserModule,
        SharedModule,
        ControllerApiOpenAdrChartComponent,
        ControllerApiOpenAdrHistoryFlatComponent,
        ControllerApiOpenAdrOverviewComponent,
    ],
    exports: [
        ControllerApiOpenAdrChartComponent,
        ControllerApiOpenAdrHistoryFlatComponent,
        ControllerApiOpenAdrOverviewComponent,
    ],
})
export class ControllerApiOpenAdrHistory { }
