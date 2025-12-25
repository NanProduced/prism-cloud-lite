package nan.produced.prism.core.device.api.event;

import java.util.List;
import java.util.UUID;

/**
 * 设备上报“互联网内容列表”（VSN 文件名列表）事件。
 *
 * <p>由 device 模块发布，program 模块消费，用于对账 deployment 事实状态。</p>
 */
public record DeviceInternetProgramVsnsReportedEvent(
        UUID userId,
        Long deviceId,
        List<String> vsnNames,
        String traceId
) {
}

