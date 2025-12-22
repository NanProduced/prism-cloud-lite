package nan.produced.prism.core.device.domain.command;

/**
 * 指令执行状态 - 用于core-service追踪指令以使用SSE进行相关信息推送
 *
 * @author Nan
 */
public enum DeviceCommandStatus {

    PUBLISHED,

    CONFIRMED,

    COMPLETED,

    EXPIRED,

    FAILED;
}
