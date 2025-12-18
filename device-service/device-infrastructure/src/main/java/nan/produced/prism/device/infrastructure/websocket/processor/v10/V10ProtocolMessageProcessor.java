package nan.produced.prism.device.infrastructure.websocket.processor.v10;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.domain.websocket.ProtocolVersion;
import nan.produced.prism.device.application.domain.websocket.WsMessageProcessingContext;
import nan.produced.prism.device.application.dto.websocket.v10.V10WebsocketMessage;
import nan.produced.prism.device.application.port.inbound.status.DeviceReportUseCase;
import nan.produced.prism.device.application.port.outbound.websocket.WsProtocolMessageProcessor;
import nan.produced.prism.device.common.utils.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * V1.0协议消息处理器 - Infrastructure层实现
 *
 * <p>V1.0协议特点：</p>
 * <ul>
 *   <li>消息格式：标准JSON格式，包含content和gps字段</li>
 *   <li>业务处理：主要处理GPS传感器数据上报</li>
 * </ul>
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class V10ProtocolMessageProcessor implements WsProtocolMessageProcessor {

    /**
     * V1.0协议的心跳响应消息
     */
    private static final String HEARTBEAT_FIELD = "heartbeat";
    private final DeviceReportUseCase deviceReportUseCase;

    @Override
    public ProtocolVersion getSupportedVersion() {
        return ProtocolVersion.V1_0;
    }

    @Override
    public TextMessageProcessResult processTextMessage(WsMessageProcessingContext context) {
        try {
            log.debug("V10ProtocolMessageProcessor - 处理V1.0文本消息: deviceId={}, message={}",
                    context.getDeviceId(), context.getRawMessage());

            // 心跳
            if (StringUtils.isBlank(context.getRawMessage())) {
                return handleHeartbeat(context);
            }

            final V10WebsocketMessage message = JsonUtils.fromJson(context.getRawMessage(), V10WebsocketMessage.class);

            // 心跳
            if (HEARTBEAT_FIELD.equalsIgnoreCase(message.getContent())) {
                return handleHeartbeat(context);
            }

            // GPS数据
            if (StringUtils.isNotBlank(message.getGps())) {
                deviceReportUseCase.asyncPushSensorReport(context.getDeviceId(), message.getGps());
                return TextMessageProcessResult.ofSuccess(true);
            }

            else {
                log.warn("V10ProtocolMessageProcessor - 未知消息类型: deviceId={}, message={}",
                        context.getDeviceId(), context.getRawMessage());
                return TextMessageProcessResult.ofSuccess(false);
            }

        } catch (Exception e) {
            log.error("V10ProtocolMessageProcessor - V1.0文本消息处理失败", e);
            return TextMessageProcessResult.ofFailure("V1.0消息处理异常: " + e.getMessage());
        }
    }

    /**
     * 处理心跳
     * @return 返回成功
     */
    private TextMessageProcessResult handleHeartbeat(WsMessageProcessingContext context) {
        if (context.sendMessage(HEARTBEAT_FIELD)) {
            // 心跳没有业务逻辑
            return TextMessageProcessResult.ofSuccess(true);
        }
        return TextMessageProcessResult.ofFailure("PONG消息发送失败");
    }
}
