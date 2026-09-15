// @ts-strict-ignore
import { ChangeDetectionStrategy, Component } from "@angular/core";
import { AbstractFlatWidget } from "src/app/shared/components/flat/abstract-flat-widget";
import { Modal } from "src/app/shared/components/flat/flat";
import { ChannelAddress, CurrentData } from "src/app/shared/shared";
import { ModalComponent } from "../modal/modal";
import { GreenButtonUtils } from "../shared/shared";

@Component({
    selector: "oe-green-button-import",
    templateUrl: "./flat.html",
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false,
})
export class FlatComponent extends AbstractFlatWidget {

    protected modalComponent: Modal | null = null;
    protected readonly GreenButtonUtils = GreenButtonUtils;
    protected values: GreenButtonUtils.Values = GreenButtonUtils.EMPTY_VALUES;
    protected convertImportState = GreenButtonUtils.importStateConverter(this.translate);

    protected override afterIsInitialized(): void {
        this.modalComponent = {
            component: ModalComponent,
            componentProps: {
                component: this.component,
            },
        };
    }

    protected override getChannelAddresses(): ChannelAddress[] {
        return this.component == null ? [] : GreenButtonUtils.getChannelAddresses(this.component.id);
    }

    protected override onCurrentData(currentData: CurrentData): void {
        if (this.component == null) {
            return;
        }
        this.values = GreenButtonUtils.readValues(this.component.id, currentData.allComponents);
    }
}
