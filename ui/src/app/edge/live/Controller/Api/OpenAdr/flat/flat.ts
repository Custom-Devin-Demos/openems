// @ts-strict-ignore
import { ChangeDetectionStrategy, Component, OnDestroy } from "@angular/core";
import { AbstractFlatWidget } from "src/app/shared/components/flat/abstract-flat-widget";
import { ChannelAddress, CurrentData, EdgeConfig } from "src/app/shared/shared";
import { ModalComponent } from "../modal/modal";
import { OpenAdrUtils } from "../shared/shared";

@Component({
    selector: "oe-controller-api-openadr",
    templateUrl: "./flat.html",
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false,
})
export class FlatComponent extends AbstractFlatWidget implements OnDestroy {

    protected readonly modalComponent = ModalComponent;
    protected readonly OpenAdrUtils = OpenAdrUtils;
    protected values: OpenAdrUtils.Values = OpenAdrUtils.EMPTY_VALUES;
    protected currency: string | null = null;
    protected countdown: string = "-";
    protected convertRegistrationState = OpenAdrUtils.registrationStateConverter(this.translate);
    private countdownTimer: ReturnType<typeof setInterval> | null = null;

    public override ngOnDestroy(): void {
        if (this.countdownTimer != null) {
            clearInterval(this.countdownTimer);
        }
        super.ngOnDestroy();
    }

    protected override getChannelAddresses(): ChannelAddress[] {
        return this.component == null ? [] : OpenAdrUtils.getChannelAddresses(this.component.id);
    }

    protected override afterIsInitialized(): void {
        const meta: EdgeConfig.Component = this.config?.getComponent("_meta");
        this.currency = this.config?.getPropertyFromComponent<string>(meta, "currency") ?? null;
        this.countdownTimer = setInterval(() => this.updateCountdown(), 1000);
    }

    protected override onCurrentData(currentData: CurrentData): void {
        if (this.component == null) {
            return;
        }
        this.values = OpenAdrUtils.readValues(this.component.id, currentData.allComponents);
        this.updateCountdown();
    }


    private updateCountdown(): void {
        this.countdown = OpenAdrUtils.formatCountdown(this.values, new Date(), this.translate);
    }
}
