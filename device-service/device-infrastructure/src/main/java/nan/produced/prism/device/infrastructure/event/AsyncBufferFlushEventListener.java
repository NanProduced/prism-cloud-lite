package nan.produced.prism.device.infrastructure.event;

import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.domain.event.AsyncBufferFlushEvent;
import nan.produced.prism.device.infrastructure.async.AsyncDeviceOnlineStatusUpdateService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异步缓冲区刷新事件监听器
 * 处理各种异步服务的缓冲池刷新事件，解决@Async自调用代理失效问题
 *
 * @author Nan
 */
@Slf4j
@Component
public class AsyncBufferFlushEventListener {

    @EventListener
    @Async
    public void handleBufferFlushEvent(AsyncBufferFlushEvent event) {
        if (event.getBufferType() == AsyncBufferFlushEvent.BufferType.DEVICE_ONLINE_STATUS) {
            try {
                ((AsyncDeviceOnlineStatusUpdateService) event.getServiceInstance()).flushBuffer();
                log.debug("AsyncBufferFlush - 设备在线状态缓冲池异步刷新完成");
            } catch (Exception e) {
                log.error("AsyncBufferFlush - 设备状态缓冲池异步刷新失败: bufferSize={}",
                        event.getBufferSize(), e);
            }
        }
    }
}
