package nan.produced.prism.device.infrastructure.async;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.domain.event.AsyncBufferFlushEvent;
import nan.produced.prism.device.application.dto.record.DeviceLoginRecord;
import nan.produced.prism.device.application.port.outbound.config.DeviceConfigPort;
import nan.produced.prism.device.application.port.outbound.repository.DeviceAccountRepository;
import nan.produced.prism.device.application.port.outbound.status.DeviceLoginUpdatePort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 异步终端登录时间更新服务
 * <p>
 * 基于缓冲池机制自动去重：每设备仅保留最新的登录记录
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncDeviceLoginUpdateService implements DeviceLoginUpdatePort {

    private final DeviceAccountRepository deviceAccountRepository;

    private final DeviceConfigPort deviceConfigPort;

    private final ApplicationEventPublisher eventPublisher;

    // 去重缓冲池 - 使用ConcurrentHashMap自动去重，每设备仅保留最新记录
    private final ConcurrentHashMap<Long, DeviceLoginRecord> bufferPool = new ConcurrentHashMap<>();

    // 刷新锁 - 防止并发刷新
    private final ReentrantLock flushLock = new ReentrantLock();

    // 统计指标
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalFlushed = new AtomicLong(0);
    private volatile long lastFlushTime = 0;

    @PreDestroy
    public void destroy() {

        flushBuffer();

        log.info("AsyncDeviceLoginUpdate - 异步登录时间更新服务关闭: totalProcessed={}, totalFlushed={}",
                totalProcessed.get(), totalFlushed.get());
    }

    @Override
    public void submitLoginUpdate(Long deviceId, String clientIp) {
        submitLoginUpdate(deviceId, clientIp, LocalDateTime.now());
    }

    @Override
    public void submitLoginUpdate(Long deviceId, String clientIp, LocalDateTime updateTime) {
        try {
            DeviceLoginRecord loginRecord = DeviceLoginRecord.create(deviceId, clientIp, updateTime);

            // 添加到缓冲池（自动去重，相同deviceId会被覆盖）
            bufferPool.put(deviceId, loginRecord);
            totalProcessed.incrementAndGet();

            checkEmergencyFlush();

        } catch (Exception e) {
            log.error("AsyncDeviceLoginUpdate - 提交登录更新失败: deviceId={}", deviceId, e);
        }
    }

    @Override
    public void submitBatchLoginUpdate(List<DeviceLoginRecord> records) {
        if (records.isEmpty()) return;

        try {
            // 批量添加到缓冲池（自动去重）
            for (DeviceLoginRecord loginRecord : records) {
                if (loginRecord != null && loginRecord.getDeviceId() != null) {
                    bufferPool.put(loginRecord.getDeviceId(), loginRecord);
                    totalProcessed.incrementAndGet();
                }
            }

            // 检查是否需要紧急刷新
             checkEmergencyFlush();
        } catch (Exception e) {
            log.error("AsyncDeviceLoginUpdate - 批量提交登录更新失败: count={}", records.size(), e);
        }
    }

    @Override
    public void flushBuffer() {
        if (!flushLock.tryLock()) {
            return;
        }

        try {
            int bufferSize = bufferPool.size();
            if (bufferSize == 0) {
                return;
            }

            long startTime = System.currentTimeMillis();
            int batchSize = deviceConfigPort.getDeviceConfig().getOnlineStatus().getBufferPool().getBatchSize();
            int processedCount = 0;

            // 分批处理缓冲池中的登录更新
            List<DeviceLoginRecord> batch = new ArrayList<>(batchSize);

            // 从ConcurrentHashMap中批量取出记录
            var iterator = bufferPool.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                batch.add(entry.getValue());
                iterator.remove(); // 从缓冲池中移除

                // 达到批处理大小或已处理完所有记录，执行批量更新
                if (batch.size() >= batchSize || !iterator.hasNext()) {
                    processBatch(new ArrayList<>(batch));
                    processedCount += batch.size();
                    batch.clear();
                }
            }

            lastFlushTime = System.currentTimeMillis();
            totalFlushed.addAndGet(processedCount);

            log.info("AsyncDeviceLoginUpdate - 缓冲池刷新完成: processed={}, duration={}ms, remainingBuffer={}",
                    processedCount, lastFlushTime - startTime, bufferPool.size());

        } catch (Exception e) {
            log.error("AsyncDeviceLoginUpdate - 刷新缓冲池失败", e);
        } finally {
            flushLock.unlock();
        }
    }

    /**
     * 定时刷新缓冲池
     * 根据配置的窗口时间定期刷新
     * 配置延迟启动，避免系统启动时资源竞争
     */
    @Override
    @Scheduled(fixedDelayString = "#{@deviceConfigPort.getBufferPoolWindowMs()}",
                initialDelayString = "#{@deviceConfigPort.getBufferPoolFlushTaskDelays()}")
    public void scheduledFlush() {

        try {
            if (!bufferPool.isEmpty()) {
                eventPublisher.publishEvent(AsyncBufferFlushEvent.createDeviceLoginUpdateFlushEvent(this, bufferPool.size()));
            }
        } catch (Exception e) {
            log.error("AsyncDeviceLoginUpdate - 定时刷新失败", e);
        }

    }

    /**
     * 处理一批登录时间更新
     * @param batch 登录时间批次列表
     */
    private void processBatch(List<DeviceLoginRecord> batch) {
        if (batch.isEmpty()) {
            return;
        }

        // 逐个处理记录，单个失败不影响其他记录
        for (DeviceLoginRecord loginRecord : batch) {
            try {
                deviceAccountRepository.updateLoginTime(
                        loginRecord.getDeviceId(),
                        loginRecord.getClientIp(),
                        loginRecord.getUpdateTime()
                );
            } catch (Exception e) {
                log.error("AsyncDeviceLoginUpdate - 单个记录处理失败: deviceId={}, clientIp={}",
                        loginRecord.getDeviceId(), loginRecord.getClientIp(), e);
            }
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
                log.warn("AsyncTerminalLoginUpdate - 缓冲池使用率达到紧急阈值，触发紧急刷新: " +
                                "currentSize={}, maxSize={}, utilizationRate={}%, threshold={}%",
                        currentSize, maxSize, (int)(utilizationRate * 100), (int)(threshold * 100));

                // 发布紧急刷新事件
                eventPublisher.publishEvent(AsyncBufferFlushEvent.createDeviceLoginUpdateFlushEvent(this, currentSize));
            }
        }
    }
}
