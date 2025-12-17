package nan.produced.prism.device.infrastructure.websocket.processor.v11;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.domain.websocket.ProtocolVersion;
import nan.produced.prism.device.application.domain.websocket.WsMessageProcessingContext;
import nan.produced.prism.device.application.port.outbound.websocket.WsProtocolMessageProcessor;
import org.springframework.stereotype.Component;

/**
 * V1.1协议消息处理器
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class V11ProtocolMessageProcessor implements WsProtocolMessageProcessor {

    /**
     * V1.1协议使用空心跳消息
     */
    private static final String HEARTBEAT_RESPONSE = "";

    @Override
    public ProtocolVersion getSupportedVersion() {
        return ProtocolVersion.V1_1;
    }

    @Override
    public TextMessageProcessResult processTextMessage(WsMessageProcessingContext context) {
        return null;
    }
}
