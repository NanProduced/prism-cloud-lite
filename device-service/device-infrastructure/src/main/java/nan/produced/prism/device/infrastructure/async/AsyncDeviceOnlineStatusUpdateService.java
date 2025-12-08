package nan.produced.prism.device.infrastructure.async;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.domain.event.AsyncBufferFlushEvent;
import nan.produced.prism.device.application.domain.status.DeviceOnlineStatus;
import nan.produced.prism.device.application.port.outbound.config.DeviceConfigPort;
import nan.produced.prism.device.application.port.outbound.status.DeviceOnlineStatusPort;
import nan.produced.prism.device.application.port.outbound.status.DeviceOnlineStatusUpdatePort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 异步设备状态更新服务
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncDeviceOnlineStatusUpdateService implements DeviceOnlineStatusUpdatePort {

    private final DeviceOnlineStatusPort deviceOnlineStatusPort;

    private final DeviceConfigPort deviceConfigPort;

    private final ApplicationEventPublisher eventPublisher;

    // 去重缓冲池 - 使用ConcurrentHashMap自动去重，每设备仅保留最新状态
    private final ConcurrentHashMap<Long, DeviceOnlineStatus> bufferPool = new ConcurrentHashMap<>();

    // 刷新锁 - 防止并发刷新
    private final ReentrantLock flushLock = new ReentrantLock();

    private volatile long lastFlushTime = 0;
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalFlushed = new AtomicLong(0);
    private volatile long peakSize = 0;  // 缓冲池峰值大小


    @PreDestroy
    public void destroy() {
        // 服务关闭时强制刷新缓冲池
        log.info("AsyncDeviceStatusUpdate - 服务关闭，强制刷新缓冲池: bufferSize={}", bufferPool.size());

        flushBuffer();

        log.info("AsyncDeviceStatusUpdate - 异步状态更新服务关闭: totalProcessed={}, totalFlushed={}",
                totalProcessed.get(), totalFlushed.get());
    }


    @Override
    public void submitStatusUpdate(DeviceOnlineStatus status) {
        if (status == null) return;

        try {
            ensureCapacityAndAdd(status);
            checkEmergencyFlush();
        } catch (Exception e) {
            log.error("AsyncDeviceStatusUpdate - 提交状态更新失败: deviceId={}",
                    status.getDeviceId(), e);
        }
    }

    @Override
    public void submitBatchStatusUpdate(List<DeviceOnlineStatus> statusList) {
        if (statusList.isEmpty()) return;

        try {
            // 批量添加到缓冲池（自动去重，并进行容量管理）
            for (DeviceOnlineStatus status : statusList) {
                if (status != null && status.getDeviceId() != null) {
                    ensureCapacityAndAdd(status);
                }
            }

            checkEmergencyFlush();

        } catch (Exception e) {
            log.error("AsyncDeviceStatusUpdate - 批量提交状态更新失败: count={}", statusList.size(), e);
        }
    }

    @Override
    public void flushBuffer() {
        if (!flushLock.tryLock()) {
            return;
        }

        try {
            if (bufferPool.isEmpty()) {
                return;
            }

            long startTime = System.currentTimeMillis();
            int batchSize = deviceConfigPort.getDeviceConfig().getOnlineStatus().getBufferPool().getBatchSize();
            int processedCount = 0;

            // 分批处理缓冲池中的状态更新
            List<DeviceOnlineStatus> batch = new ArrayList<>(batchSize);

            // 从ConcurrentHashMap中批量取出记录
            var iterator = bufferPool.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                batch.add(entry.getValue());
                iterator.remove();  // 从缓冲池中移除

                // 达到批处理大小或已处理完所有记录，执行批量更新
                if (batch.size() >= batchSize || !iterator.hasNext()) {
                    processBatch(new ArrayList<>(batch));
                    processedCount += batch.size();
                    batch.clear();
                }
            }

            lastFlushTime = System.currentTimeMillis();
            totalFlushed.addAndGet(processedCount);

            log.info("AsyncDeviceStatusUpdate - 缓冲池刷新完成: processed={}, duration={}ms, remainingBuffer={}",
                    processedCount, lastFlushTime - startTime, bufferPool.size());

        } catch (Exception e) {
            log.error("AsyncDeviceStatusUpdate - 刷新缓冲池失败", e);
        } finally {
            flushLock.unlock();
        }
    }

    @Override
    @Scheduled(fixedDelayString = "#{@deviceConfigPort.getBufferPoolWindowMs()}",
                initialDelayString = "#{@deviceConfigPort.getBufferPoolFlushTaskDelays()}")
    public void scheduledFlush() {
        try {
            if (!bufferPool.isEmpty()) {
                eventPublisher.publishEvent(AsyncBufferFlushEvent.createDeviceOnlineStatusFlushEvent(this, bufferPool.size()));
            }
        } catch (Exception e) {
            log.error("AsyncDeviceStatusUpdate - 定时刷新失败", e);
        }

    }

    /**
     * 容量管理：检查缓冲池是否接近上限，接近则同步触发 flush 以腾出空间
     * 使用软上限策略，不主动丢弃数据，而是通过同步 flush 来清空缓冲池
     *
     * @param status 设备状态
     */
    private void ensureCapacityAndAdd(DeviceOnlineStatus status) {
        int currentSize = bufferPool.size();
        int maxSize = deviceConfigPort.getDeviceConfig().getOnlineStatus().getBufferPool().getMaxSize();

        // 容量检查：若接近上限，同步触发 flush 以腾出空间（不丢弃数据）
        if (currentSize >= maxSize) {
            log.warn("AsyncDeviceStatusUpdate - 缓冲池接近上限，触发同步flush以腾出空间: " +
                    "currentSize={}, maxSize={}", currentSize, maxSize);
            flushBuffer();  // 同步刷新，清空缓冲池
        }

        // 添加新值到缓冲池
        bufferPool.put(status.getDeviceId(), status);
        totalProcessed.incrementAndGet();

        // 更新峰值大小
        long newSize = bufferPool.size();
        if (newSize > peakSize) {
            peakSize = newSize;
        }
    }

    /**
     * 检查是否需要紧急刷新
     */
    private void checkEmergencyFlush() {

        int currentSize = bufferPool.size();
        int maxSize = deviceConfigPort.getDeviceConfig().getOnlineStatus().getBufferPool().getMaxSize();
        double threshold = deviceConfigPort.getDeviceConfig().getOnlineStatus().getBufferPool().getEmergencyFlushThreshold();

        if (currentSize > 0 && maxSize > 0) {
            double utilizationRate = (double) currentSize / maxSize;

            if (utilizationRate >= threshold) {
                log.warn("AsyncDeviceStatusUpdate - 缓冲池使用率达到紧急阈值，触发紧急刷新: " +
                                "currentSize={}, maxSize={}, utilizationRate={}%, threshold={}%",
                        currentSize, maxSize, (int)(utilizationRate * 100), (int)(threshold * 100));

                // 发布紧急刷新事件
                eventPublisher.publishEvent(AsyncBufferFlushEvent.createDeviceOnlineStatusFlushEvent(this, currentSize));
            }
        }
    }

    /**
     * 处理一批状态更新
     *
     * @param batch 设备状态批次列表
     */
    private void processBatch(List<DeviceOnlineStatus> batch) {
        if (batch.isEmpty()) {
            return;
        }

        try {
            deviceOnlineStatusPort.batchDeterminedOps(batch);
        } catch (Exception e) {
            log.error("AsyncDeviceStatusUpdate - 批处理失败: batchSize={}", batch.size(), e);
        }
    }
}
