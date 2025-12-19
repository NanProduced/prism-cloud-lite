package nan.produced.prism.core.device.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "设备动作下发状态（仅表示服务端已处理请求）")
public enum DeviceActionDispatchStatus {

    @Schema(description = "已下发/已进入 device-service 投递流程（后续进展通过 SSE 的 operation.updated 推送）")
    DISPATCHED,

    @Schema(description = "未被接受（例如缓存失败等）")
    REJECTED
}

