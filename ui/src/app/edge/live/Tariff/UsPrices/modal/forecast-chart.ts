import { ChangeDetectionStrategy, Component, Input } from "@angular/core";
import { TranslateService } from "@ngx-translate/core";
import { ChartDataset, ChartOptions } from "chart.js";
import { DateTimeFormats, DateTimeUtils } from "src/app/shared/utils/datetime/datetime-utils";
import { UsPricesUtils } from "../shared/shared";

@Component({
    selector: "oe-us-prices-forecast-chart",
    template: `
        <div [style.height.px]="height" class="semi-transparent-background">
            <canvas baseChart [datasets]="datasets" [labels]="labels" [options]="options" type="bar"></canvas>
        </div>
    `,
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false,
})
export class ForecastChartComponent {

    @Input() public height: number = 250;
    @Input() public currency: string | null = null;

    protected datasets: ChartDataset<"bar", (number | null)[]>[] = [];
    protected labels: string[] = [];
    protected options: ChartOptions<"bar"> = ForecastChartComponent.getOptions("");

    constructor(private translate: TranslateService) { }

    @Input() public set hourly(value: UsPricesUtils.HourlyPrice[] | null) {
        const data = UsPricesUtils.getForecastChartData(value ?? [], this.translate, this.currency);
        this.labels = data.labels.map((date) => DateTimeUtils.format(date, DateTimeFormats.HOUR_MINUTE) ?? "");
        this.datasets = data.datasets;
        this.options = ForecastChartComponent.getOptions(data.datasets[0]?.label ?? "");
    }

    private static getOptions(unitLabel: string): ChartOptions<"bar"> {
        return {
            responsive: true,
            maintainAspectRatio: false,
            animation: false,
            plugins: {
                legend: { display: false },
                datalabels: { display: false },
            },
            scales: {
                x: { ticks: { maxRotation: 0, autoSkip: true, maxTicksLimit: 12 } },
                y: { beginAtZero: true, title: { display: true, text: unitLabel } },
            },
        };
    }
}
