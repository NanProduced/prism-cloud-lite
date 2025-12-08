package nan.produced.prism.device.application.domain.status;

import lombok.Getter;

/**
 * 设备上报来源枚举
 *
 * @author Nan
 */
@Getter
public enum ReportSource {

    /**
     * HTTP轮询请求
     */
    HTTP("HTTP"),

    /**
     * WebSocket心跳或消息
     */
    WEBSOCKET("WebSocket");

    private final String description;

    ReportSource(String description) {
        this.description = description;
    }
}
