// @ts-strict-ignore
import { ChangeDetectionStrategy, Component } from "@angular/core";
import { CommonUiModule } from "src/app/shared/common-ui.module";
import { ComponentsBaseModule } from "src/app/shared/components/components.module";
import { AbstractFlatWidget } from "src/app/shared/components/flat/abstract-flat-widget";
import { ChannelAddress, CurrentData } from "src/app/shared/shared";
import { OpenAdrUtils } from "../../shared/shared";

@Component({
    selector: "oe-controller-api-openadr-history-widget",
    templateUrl: "./flat.html",
    standalone: true,
    changeDetection: ChangeDetectionStrategy.Eager,
    imports: [CommonUiModule, ComponentsBaseModule],
})
export class ControllerApiOpenAdrHistoryFlatComponent extends AbstractFlatWidget {

    protected readonly OpenAdrUtils = OpenAdrUtils;
    protected values: OpenAdrUtils.Values = OpenAdrUtils.EMPTY_VALUES;

    protected override getChannelAddresses(): ChannelAddress[] {
        return this.component == null ? [] : OpenAdrUtils.getChannelAddresses(this.component.id);
    }

    protected override onCurrentData(currentData: CurrentData): void {
        if (this.component == null) {
            return;
        }
        this.values = OpenAdrUtils.readValues(this.component.id, currentData.allComponents);
    }
}
