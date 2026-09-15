import { JsonrpcRequest, JsonrpcResponseSuccess } from "src/app/shared/jsonrpc/base";

export type OpenAdrOptState = "OPT_IN" | "OPT_OUT";

export type OpenAdrEvent = {
    eventId: string;
    signalType: "SIMPLE" | "PRICE" | string;
    level: number | null;
    price: number | null;
    /** ISO-8601 or epoch seconds */
    start: string | number;
    /** ISO-8601 or epoch seconds */
    end: string | number;
    optState: OpenAdrOptState | null;
};

/**
 * Payload for `componentJsonApi`: lists the events currently known to the OpenADR VEN.
 */
export class GetActiveEventsRequest extends JsonrpcRequest {
    private static readonly METHOD: string = "getActiveEvents";

    public constructor() {
        super(GetActiveEventsRequest.METHOD, {});
    }
}

export class GetActiveEventsResponse extends JsonrpcResponseSuccess {
    public constructor(
        public override readonly id: string,
        public override readonly result: {
            events: OpenAdrEvent[];
        },
    ) {
        super(id, result);
    }
}

/**
 * Payload for `componentJsonApi`: opts in or out of a single event.
 */
export class SetOptStateRequest extends JsonrpcRequest {
    private static readonly METHOD: string = "setOptState";

    public constructor(
        public override readonly params: {
            eventId: string;
            optState: OpenAdrOptState;
        },
    ) {
        super(SetOptStateRequest.METHOD, params);
    }
}
