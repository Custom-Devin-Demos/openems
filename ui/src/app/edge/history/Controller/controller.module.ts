import { NgModule } from "@angular/core";
import { ControllerApiOpenAdrHistory } from "../../live/Controller/Api/OpenAdr/history/openadr-history.module";
import { ChannelThreshold } from "../../live/Controller/Channelthreshold/history/channelThreshold.module";
import { ControllerChpHistory } from "../../live/Controller/ChpSoc/history/chp.module";
import { ControllerEnerixControlControlHistory } from "../../live/Controller/EnerixControl/history/enerixControl.module";
import { ControllerEssGridOptimizedCharge } from "../../live/Controller/Ess/GridOptimizedCharge/history/gridOptimizeCharge.module";
import { ControllerHeat } from "../../live/Controller/Heat/history/heat-history";
import { ControllerIoSingleThreshold } from "../../live/Controller/Io/ChannelSingleThreshold/history/channelSingleThreshold.module";
import { FixDigitalOutputHistory } from "../../live/Controller/Io/FixDigitalOutput/history/fix-digital-output-history.module";
import { ControllerModbusTcpApi } from "../../live/Controller/ModbusTcpApi/history/modbusTcpApi.module";
import { ControllerEss } from "./Ess/ess.module";
import { ControllerIo } from "./Io/Io.module";

@NgModule({
    imports: [
        ChannelThreshold,
        ControllerApiOpenAdrHistory,
        ControllerChpHistory,
        ControllerEnerixControlControlHistory,
        ControllerEss,
        ControllerEssGridOptimizedCharge,
        ControllerHeat,
        ControllerIo,
        ControllerIoSingleThreshold,
        ControllerModbusTcpApi,
        FixDigitalOutputHistory,
    ],
    exports: [
        ChannelThreshold,
        ControllerApiOpenAdrHistory,
        ControllerChpHistory,
        ControllerEnerixControlControlHistory,
        ControllerEss,
        ControllerEssGridOptimizedCharge,
        ControllerHeat,
        ControllerIo,
        ControllerIoSingleThreshold,
        ControllerModbusTcpApi,
        FixDigitalOutputHistory,
    ],
})
export class Controller {}
