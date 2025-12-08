package nan.produced.prism.device.application.handler;

import nan.produced.prism.device.common.exception.tech.TechErrorCode;
import nan.produced.prism.device.common.exception.tech.TechException;

import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket消息统计工具类
 * @author Nan
 */
public class WebsocketMsgMetricsHelper {

    private static final AtomicLong totalSentMessage = new AtomicLong(0);

    private static final AtomicLong totalReceivedMessage = new AtomicLong(0);

    private static final AtomicLong totalErrorMessage = new AtomicLong(0);

    private WebsocketMsgMetricsHelper() {
        throw new TechException(TechErrorCode.INSTANTIATION_IS_PROHIBITED);
    }

    public static void incrementSentMessage() {
        totalSentMessage.incrementAndGet();
    }

    public static void incrementReceivedMessage() {
        totalReceivedMessage.incrementAndGet();
    }

    public static void incrementErrorMessage() {
        totalErrorMessage.incrementAndGet();
    }

    public static long getTotalSentMessage() {
        return totalSentMessage.get();
    }

    public static long getTotalReceivedMessage() {
        return totalReceivedMessage.get();
    }

    public static long getTotalErrorMessage() {
        return totalErrorMessage.get();
    }
}
