package nan.produced.prism.core.device.domain.command;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 动作追踪等级（面向用户体验的“操作进度”）
 * <p>
 * - ACK_ONLY：仅追踪指令被设备接收/过期，不追踪执行完成（不依赖属性上报）
 * - PROPERTY_MATCH：通过设备属性上报推断执行完成（限少量常用动作）
 * - EXPLICIT_RESULT：设备协议显式返回执行结果（未来扩展）
 */
@Schema(description = "动作追踪等级")
public enum DeviceActionTrackingLevel {

    @Schema(description = "仅根据推送消息更新渲染页面数据")
    UPDATE_ONLY,

    @Schema(description = "仅追踪接收/过期")
    ACK_ONLY,

    @Schema(description = "通过属性上报匹配完成")
    PROPERTY_MATCH,

    @Schema(description = "设备显式回执执行结果")
    EXPLICIT_RESULT
}

