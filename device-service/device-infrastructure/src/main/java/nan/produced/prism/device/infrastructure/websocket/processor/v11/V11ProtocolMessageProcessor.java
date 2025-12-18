package nan.produced.prism.device.infrastructure.websocket.processor.v11;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.domain.websocket.ProtocolVersion;
import nan.produced.prism.device.application.domain.websocket.WsMessageProcessingContext;
import nan.produced.prism.device.application.dto.websocket.v11.V11WebsocketErrorType;
import nan.produced.prism.device.application.dto.websocket.v11.V11WebsocketMessage;
import nan.produced.prism.device.application.port.outbound.websocket.WsProtocolMessageProcessor;
import nan.produced.prism.device.common.exception.business.BusinessErrorCode;
import nan.produced.prism.device.common.exception.business.BusinessException;
import nan.produced.prism.device.common.exception.tech.TechException;
import nan.produced.prism.device.common.utils.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * V1.1协议消息处理器
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class V11ProtocolMessageProcessor implements WsProtocolMessageProcessor {

    private final V11OperationHandlerRouter operationHandlerRouter;

    /**
     * V1.1协议使用空心跳消息
     */
    private static final String HEARTBEAT_RESPONSE = "";

    /**
     * V1.1协议连接建立回调 - 主动推送待执行指令
     *
     * <p>V1.1协议特性：</p>
     * <ul>
     *   <li>连接建立后，服务器主动推送待执行指令列表</li>
     *   <li>推送消息的receiptId为null，表示服务器主动推送</li>
     *   <li>设备接收指令后，通过COMMAND消息再次获取指令或通过CONFIRM_COMMAND确认执行</li>
     * </ul>
     *
     * @param context 消息处理上下文，包含设备连接信息
     */
    @Override
    public void onConnectionEstablished(WsMessageProcessingContext context) {
        log.info("V11ProtocolMessageProcessor - 连接建立，开始主动推送指令: deviceId={}", context.getDeviceId());
        operationHandlerRouter.pushCommandsOnConnection(context);
    }

    @Override
    public ProtocolVersion getSupportedVersion() {
        return ProtocolVersion.V1_1;
    }

    @Override
    public TextMessageProcessResult processTextMessage(WsMessageProcessingContext context) {

        log.debug("V11ProtocolMessageProcessor - 处理V1.1文本消息: deviceId={}, message={}",
                context.getDeviceId(), context.getRawMessage());

        // V1.1心跳消息为空报文
        if (StringUtils.isBlank(context.getRawMessage())) {
            return handleHeartbeat(context);
        }

        try {
            final V11WebsocketMessage v11WebsocketMessage = JsonUtils.fromJson(context.getRawMessage(), V11WebsocketMessage.class);

            if (Objects.isNull(v11WebsocketMessage.getMessageId())) {
                log.error("V11ProtocolMessageProcessor - #GET_MESSAGE#【错误消息ID】 === deviceId={}", context.getDeviceId());
                // 发送错误消息（消息Id不存在）
                context.sendMessage(V11WebsocketMessage.generateErrorContent(V11WebsocketErrorType.INVALID_MESSAGE_ID));
                return TextMessageProcessResult.ofFailure("V1.1-消息Id错误");
            }
            // 根据消息类型进行路由处理
            operationHandlerRouter.handleMessageByType(context, v11WebsocketMessage);

            return TextMessageProcessResult.ofSuccess(false);

        } catch (TechException e) {
            log.error("V11ProtocolMessageProcessor - #GET_MESSAGE#【消息反序列化错误】==={deviceId={}, message={}}",
                    context.getDeviceId(), context.getRawMessage(), e);
            // 发送错误信息（序列化问题）
            context.sendMessage(V11WebsocketMessage.generateErrorContent(V11WebsocketErrorType.INVALID_MESSAGE_DATA, e.getMessage()));
            return TextMessageProcessResult.ofFailure("V1.1-消息序列化异常");
        } catch (BusinessException e) {
            if (e.getErrorCode().equals(BusinessErrorCode.WS_INVALID_MESSAGE_DATA.getCode())) {
                context.sendMessage(V11WebsocketMessage.generateErrorContent(V11WebsocketErrorType.INVALID_MESSAGE_DATA, e.getMessage()));
            }
            else if (e.getErrorCode().equals(BusinessErrorCode.WS_INVALID_MESSAGE_TYPE.getCode())) {
                context.sendMessage(V11WebsocketMessage.generateErrorContent(V11WebsocketErrorType.INVALID_MESSAGE_TYPE, e.getMessage()));
            }
            else {
                context.sendMessage(V11WebsocketMessage.generateErrorContent(V11WebsocketErrorType.INVALID_MESSAGE_DATA, e.getMessage()));
            }
            return TextMessageProcessResult.ofFailure("V1.1-消息处理异常");
        } catch (Exception e) {
            log.error("V11ProtocolMessageProcessor - #GET_MESSAGE#【消息处理错误】==={deviceId={}, message={}}",
                    context.getDeviceId(), context.getRawMessage(), e);
            context.sendMessage(V11WebsocketMessage.generateErrorContent(V11WebsocketErrorType.SERVER_ERROR, e.getMessage()));
            return TextMessageProcessResult.ofFailure("V1.1-消息处理异常");
        }
    }

    /**
     * 处理心跳消息
     *
     * @param context 消息处理上下文，包含发送和接收消息所需的信息
     * @return 返回一个TextMessageProcessResult对象，表示消息处理的结果。如果PONG消息发送成功，则返回成功结果；否则返回失败结果。
     */
    private TextMessageProcessResult handleHeartbeat(WsMessageProcessingContext context) {
        if (context.sendMessage(HEARTBEAT_RESPONSE)) {
            // 心跳没有业务逻辑
            return TextMessageProcessResult.ofSuccess(true);
        }
        return TextMessageProcessResult.ofFailure("PONG消息发送失败");
    }
}
