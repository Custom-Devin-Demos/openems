// @ts-strict-ignore
import { ChangeDetectionStrategy, ChangeDetectorRef, Component } from "@angular/core";
import { FormBuilder } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { ModalController } from "@ionic/angular";
import { TranslateService } from "@ngx-translate/core";
import { AbstractModal } from "src/app/shared/components/modal/abstractModal";
import { ChannelAddress, CurrentData, Service, Websocket } from "src/app/shared/shared";
import { DefaultTypes } from "src/app/shared/type/defaulttypes";
import { GreenButtonUtils } from "../shared/shared";

@Component({
    selector: "oe-green-button-import-modal",
    templateUrl: "./modal.html",
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: false,
})
export class ModalComponent extends AbstractModal {

    protected readonly GreenButtonUtils = GreenButtonUtils;
    protected values: GreenButtonUtils.Values = GreenButtonUtils.EMPTY_VALUES;
    protected convertImportState = GreenButtonUtils.importStateConverter(this.translate);

    constructor(
        protected override websocket: Websocket,
        protected override route: ActivatedRoute,
        protected override service: Service,
        public override modalController: ModalController,
        protected override translate: TranslateService,
        public override formBuilder: FormBuilder,
        public override ref: ChangeDetectorRef,
        private router: Router,
    ) {
        super(websocket, route, service, modalController, translate, formBuilder, ref);
    }

    protected get hasImportedRange(): boolean {
        return this.values.firstReading != null && this.values.lastReading != null;
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

    /**
     * Sets the history period to the imported range and opens the consumption history.
     */
    protected async openConsumptionHistory(): Promise<void> {
        if (!this.hasImportedRange || this.edge == null) {
            return;
        }
        this.service.historyPeriod.next(new DefaultTypes.HistoryPeriod(
            new Date(this.values.firstReading * 1000),
            new Date(this.values.lastReading * 1000),
        ));
        this.service.periodString = DefaultTypes.PeriodString.CUSTOM;
        await this.modalController.dismiss();
        this.router.navigate(["/device", this.edge.id, "history", "consumptionchart"]);
    }
}
