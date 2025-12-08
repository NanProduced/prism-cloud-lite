package nan.produced.prism.device.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.domain.event.DeviceOnlineStatusEvent;
import nan.produced.prism.device.application.domain.status.DeviceOnlineStatus;
import nan.produced.prism.device.application.domain.status.OnlineStatus;
import nan.produced.prism.device.application.domain.status.ReportSource;
import nan.produced.prism.device.application.domain.websocket.ProtocolVersion;
import nan.produced.prism.device.application.dto.cache.DeviceOnlineStatusUpdateContext;
import nan.produced.prism.device.application.port.inbound.status.DeviceOnlineStatusUseCase;
import nan.produced.prism.device.application.port.outbound.config.DeviceConfigPort;
import nan.produced.prism.device.application.port.outbound.status.DeviceOnlineStatusCachePort;
import nan.produced.prism.device.application.port.outbound.status.DeviceOnlineStatusFlushCallback;
import nan.produced.prism.device.application.port.outbound.status.DeviceOnlineStatusPort;
import nan.produced.prism.device.application.port.outbound.status.DeviceOnlineStatusUpdatePort;
import nan.produced.prism.device.application.port.outbound.websocket.WsConnectionManagerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备在线状态管理应用服务
 * 支持同步/异步配置切换
 * <p>
 * 使用 DeviceStatusCachePort 实现本地缓存锁，替代 Redis 分布式锁
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceOnlineStatusApplicationService implements DeviceOnlineStatusUseCase, DeviceOnlineStatusFlushCallback {

    private final DeviceOnlineStatusPort deviceOnlineStatusPort;

    private final DeviceOnlineStatusCachePort deviceOnlineStatusCachePort;

    private final DeviceOnlineStatusUpdatePort deviceOnlineStatusUpdatePort;

    private final DeviceConfigPort deviceConfigPort;

    private final WsConnectionManagerPort wsConnectionManagerPort;

    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Async
    public void updateLastReportTime(Long deviceId, ReportSource source, String clientIp) {
        DeviceOnlineStatusUpdateContext context = deviceOnlineStatusCachePort.getOrCreateContext(deviceId);
        boolean shouldFlushPending = false;
        try {
            context.lock();
            Optional<DeviceOnlineStatus> pendingUpdate = processDeviceStatusUpdate(deviceId, source, clientIp);
            if (pendingUpdate.isPresent()) {
                context.savePendingStatus(pendingUpdate.get());
                shouldFlushPending = context.tryScheduleFlush();
            }
        } catch (Exception e) {
            log.error("刷新设备上报时间失败: deviceId={}, source={}", deviceId, source, e);
        } finally {
            context.unlock();
        }

        if (shouldFlushPending) {
            flushPendingStatus(deviceId, context);
        }

    }

    @Override
    public boolean isDeviceOnline(Long deviceId) {
        try {
            Optional<DeviceOnlineStatus> statusOpt = deviceOnlineStatusPort.getDeviceStatus(deviceId);

            if (statusOpt.isPresent()) {
                // 使用配置化的超时阈值
                long timeoutThreshold = deviceConfigPort.getDeviceOfflineThreshold();
                return statusOpt.get().isOnline(timeoutThreshold);
            }

            return false;

        } catch (Exception e) {
            log.error("ApplicationService - 查询设备在线状态失败: deviceId={}", deviceId, e);
            // 故障时返回未知状态（保守策略）
            return false;
        }
    }

    @Override
    public Optional<DeviceOnlineStatus> getDeviceStatus(Long deviceId) {
        try {
            return deviceOnlineStatusPort.getDeviceStatus(deviceId);
        } catch (Exception e) {
            log.error("ApplicationService - 获取设备状态详情失败: deviceId={}", deviceId, e);
            return Optional.empty();
        }
    }

    @Override
    public Map<Long, Boolean> batchCheckOnline(List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            log.debug("ApplicationService - 批量检查设备在线状态: count={}", deviceIds.size());

            Map<Long, DeviceOnlineStatus> statusMap = deviceOnlineStatusPort.batchGetDeviceStatus(deviceIds);
            long timeoutThreshold = deviceConfigPort.getDeviceOfflineThreshold();

            return deviceIds.stream()
                    .collect(Collectors.toMap(
                            deviceId -> deviceId,
                            deviceId -> {
                                DeviceOnlineStatus status = statusMap.get(deviceId);
                                return status != null && status.isOnline(timeoutThreshold);
                            }
                    ));

        } catch (Exception e) {
            log.error("ApplicationService - 批量检查设备在线状态失败: deviceIds.size={}", deviceIds.size(), e);
            // 故障降级：返回全部离线
            return deviceIds.stream()
                    .collect(Collectors.toMap(deviceId -> deviceId, deviceId -> false));
        }
    }

    @Override
    public Map<Long, DeviceOnlineStatus> batchGetDeviceStatus(List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            return deviceOnlineStatusPort.batchGetDeviceStatus(deviceIds);
        } catch (Exception e) {
            log.error("ApplicationService - 批量获取设备状态详情失败: deviceIds.size={}", deviceIds.size(), e);
            return Collections.emptyMap();
        }
    }

    @Override
    public Set<Long> getOnlineDeviceIds() {
        try {
            Set<Long> allDeviceIds = deviceOnlineStatusPort.getAllDeviceIds();
            Map<Long, DeviceOnlineStatus> statusMap = deviceOnlineStatusPort.batchGetDeviceStatus(
                    new ArrayList<>(allDeviceIds));

            long timeoutThreshold = deviceConfigPort.getDeviceOfflineThreshold();

            return statusMap.values().stream()
                    .filter(status -> status.isOnline(timeoutThreshold))
                    .map(DeviceOnlineStatus::getDeviceId)
                    .collect(Collectors.toSet());

        } catch (Exception e) {
            log.error("ApplicationService - 获取在线设备ID列表失败", e);
            return Collections.emptySet();
        }
    }

    @Override
    public int getOnlineDeviceCount() {
        try {
            return deviceOnlineStatusPort.getOnlineDeviceCount();
        } catch (Exception e) {
            log.error("ApplicationService - 获取在线设备数量失败", e);
            return 0;
        }
    }

    /**
     * 批量处理离线设备
     * @return 处理的离线设备数量
     */
    @Override
    public int processOfflineDevices() {
        try {
            long startTime = System.currentTimeMillis();
            long expireThreshold = startTime - deviceConfigPort.getDeviceOfflineThreshold();

            log.debug("ApplicationService - 开始检查离线设备: expireThreshold={}", expireThreshold);

            // 获取可能过期的设备ID列表
            List<Long> candidateDeviceIds = deviceOnlineStatusPort.findExpiredDevices(expireThreshold);

            if (candidateDeviceIds.isEmpty()) {
                log.debug("ApplicationService - 无离线设备");
                return 0;
            }

            log.info("ApplicationService - 发现可能离线设备: count={}", candidateDeviceIds.size());

            // 批量处理离线设备
            int processedCount = 0;

            // 使用批量处理优化网络往返
            List<DeviceOnlineStatus> offlineStatuses = deviceOnlineStatusPort.batchMarkOfflineAndResetTtl(candidateDeviceIds);

            // 创建离线事件
            List<DeviceOnlineStatusEvent> offlineEvents = offlineStatuses.stream()
                    .map(offlineStatus -> {
                        try {
                            DeviceOnlineStatusEvent event = DeviceOnlineStatusEvent.createDetectedOfflineEvent(
                                    offlineStatus.getDeviceId(),
                                    offlineStatus.getOnlineStartTime(),
                                    offlineStatus.getLastReportTime()
                            );
                            log.debug("ApplicationService - 设备标记离线成功: deviceId={}", offlineStatus.getDeviceId());
                            return event;
                        } catch (Exception ex) {
                            log.warn("ApplicationService - 处理单个设备离线失败: deviceId={}", offlineStatus.getDeviceId(), ex);
                            return null; // 出错的跳过
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();

            processedCount += offlineEvents.size();

            // 批量发布离线事件
            if (!offlineEvents.isEmpty()) {
                offlineEvents.forEach(eventPublisher::publishEvent);
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("ApplicationService - 离线设备检查完成: 候选设备={}, 实际处理={}, 耗时={}ms",
                    candidateDeviceIds.size(), processedCount, elapsed);

            return processedCount;

        } catch (Exception e) {
            log.error("ApplicationService - 处理离线设备失败", e);
            return 0;
        }
    }

    /**
     * 实现 DeviceStatusFlushCallback 接口
     * 当设备缓存被驱逐时，自动 flush 待处理的设备状态
     *
     * @param deviceId 被驱逐的设备ID
     * @param context 设备的更新上下文
     */
    @Override
    public void onContextEvicted(Long deviceId, DeviceOnlineStatusUpdateContext context) {
        flushPendingStatus(deviceId, context);
    }

    /**
     * 处理设备状态更新逻辑
     *
     * @param deviceId 设备ID
     * @param source   上报源
     * @param clientIp 客户端IP
     */
    private Optional<DeviceOnlineStatus> processDeviceStatusUpdate(Long deviceId, ReportSource source, String clientIp) {
        Optional<DeviceOnlineStatus> currentStatusOpt = deviceOnlineStatusPort.getDeviceStatus(deviceId);

        if (currentStatusOpt.isPresent()) {
            return handleExistingDevice(deviceId, currentStatusOpt.get(), source, clientIp);
        }

        handleNewDevice(deviceId, source, clientIp);
        return Optional.empty();
    }

    /**
     * 处理已存在的设备状态（更新/重连）
     * @param deviceId 设备ID
     * @param currentStatus 当前设备状态
     * @param source   上报源
     * @param clientIp 客户端IP
     */
    private Optional<DeviceOnlineStatus> handleExistingDevice(Long deviceId, DeviceOnlineStatus currentStatus,
                                                              ReportSource source, String clientIp) {
        DeviceOnlineStatus updatedStatus = determinedUpdateStatus(currentStatus, source, clientIp);

        // ONLINE 心跳可以延迟刷盘，只保留最后一次时间戳即可
        if ((updatedStatus.getStatus() == OnlineStatus.ONLINE)) {
            return Optional.of(updatedStatus);
        }

        updateDeviceStatusWithMode(updatedStatus);
        publishStatusEvent(deviceId, updatedStatus);
        return Optional.empty();
    }

    /**
     * 处理新上线的设备
     * @param deviceId 设备ID
     * @param source   上报源
     * @param clientIp 客户端IP
     */
    private void handleNewDevice(Long deviceId, ReportSource source, String clientIp) {
        String version = source == ReportSource.WEBSOCKET ? getProtocolVersionFromConnection(deviceId) : null;
        DeviceOnlineStatus newStatus = DeviceOnlineStatus.createGoLive(deviceId, source, clientIp, version);

        updateDeviceStatusWithMode(newStatus);
        publishStatusEvent(deviceId, newStatus);
        log.info("设备上线: deviceId={}, source={}", deviceId, source);
    }

    /**
     * 决策设备状态转换
     * <p>
     * 状态转换流程：
     *  <li> OFFLINE → RECONNECT（设备重新连接）</li>
     *  <li> GO_LIVE/RECONNECT → ONLINE（状态稳定）</li>
     *  <li> ONLINE → ONLINE（心跳维持，仅刷新时间）</li>
     * </p>
     *
     * @param currentStatus 当前设备状态
     * @param source 上报数据源
     * @param clientIp 上报客户端IP
     */
    private DeviceOnlineStatus determinedUpdateStatus(DeviceOnlineStatus currentStatus,
                                                      ReportSource source, String clientIp) {
        OnlineStatus currentState = currentStatus.getStatus();

        // 离线状态 → 重连
        if (currentState == OnlineStatus.OFFLINE) {
            return handleOfflineReconnect(currentStatus, source, clientIp);
        }

        // 初始状态 → 在线稳定
        if (currentState == OnlineStatus.GO_LIVE || currentState == OnlineStatus.RECONNECT) {
            return transitionToOnline(currentStatus, source, clientIp);
        }

        // 在线状态 → 心跳维持
        return refreshOnlineStatus(currentStatus, source, clientIp);
    }

    /**
     * 根据配置选择同步或异步方式更新设备状态
     * 对于GO_LIVE状态强制同步处理，确保状态立即写入
     *
     * @param status 设备状态
     */
    private void updateDeviceStatusWithMode(DeviceOnlineStatus status) {
        // 对于首次上线，强制同步处理确保状态立即写入，避免竞态条件
        if (status.getStatus() == OnlineStatus.GO_LIVE || status.getStatus() == OnlineStatus.RECONNECT) {
            deviceOnlineStatusPort.determinedOps(status);
            return;
        }

        try {
            // 异步模式：提交到缓冲池
            deviceOnlineStatusUpdatePort.submitStatusUpdate(status);
        } catch (Exception e) {
            log.warn("ApplicationService - 异步提交失败，降级到同步模式: deviceId={}", status.getDeviceId(), e);
            // 降级到同步模式
            deviceOnlineStatusPort.determinedOps(status);
        }

    }

    /**
     * 根据状态转换发布相应的事件
     * @param deviceId 设备ID
     * @param updatedStatus 更新后的设备状态
     */
    private void publishStatusEvent(Long deviceId, DeviceOnlineStatus updatedStatus) {
        DeviceOnlineStatusEvent event;

        if (updatedStatus.getStatus() == OnlineStatus.RECONNECT) {
            event = DeviceOnlineStatusEvent.createReconnectEvent(deviceId, updatedStatus.getLastReportSource(),
                    updatedStatus.getClientIp(), updatedStatus.getOnlineStartTime(),
                    updatedStatus.getLastReportTime());
        }
        else if (updatedStatus.getStatus() == OnlineStatus.GO_LIVE) {
            event = DeviceOnlineStatusEvent.createGoLiveEvent(deviceId, updatedStatus.getLastReportSource(), updatedStatus.getClientIp());
        }
        else {
            event = DeviceOnlineStatusEvent.createHeartbeatEvent(deviceId, updatedStatus.getLastReportSource(),
                    updatedStatus.getClientIp());
        }

        eventPublisher.publishEvent(event);
    }

    /**
     * 将最新的心跳批量刷入底层端口，只保留时间戳最大的记录。
     * 采用循环而非递归，避免在高频心跳场景下的栈溢出风险。
     * <p>
     * 注意：updateLastReportTime 已在加锁阶段调用过 tryScheduleFlush()，
     * 所以此处直接执行第一次 flush，之后才检查是否需要重新调度（处理并发新心跳）。
     * @param deviceId 设备ID
     * @param context 上下文
     */
    private void flushPendingStatus(Long deviceId, DeviceOnlineStatusUpdateContext context) {
        // 直接执行第一次 flush（updateLastReportTime 已成功调度）
        do {
            flushOnce(deviceId, context);
        }
        // 若有新的 pending 产生（并发心跳），继续迭代处理
        while (context.hasPending() && context.tryScheduleFlush());
    }

    /**
     * 单次刷新操作：从上下文 drain 最新心跳，持久化到底层。
     * @param deviceId 设备ID
     * @param context 上下文
     */
    private void flushOnce(Long deviceId, DeviceOnlineStatusUpdateContext context) {
        try {
            DeviceOnlineStatus latest = drainLatestHeartbeat(context);
            if (latest == null) {
                return;
            }

            try {
                updateDeviceStatusWithMode(latest);
                publishStatusEvent(deviceId, latest);
            } catch (Exception flushError) {
                log.error("ApplicationService - 刷新聚合心跳失败: deviceId={}", deviceId, flushError);
            }
        } finally {
            context.finishFlush();
        }
    }

    /**
     * 尝试从上下文中获取最新心跳
     * @param context 上下文
     * @return 最新心跳
     */
    private DeviceOnlineStatus drainLatestHeartbeat(DeviceOnlineStatusUpdateContext context) {
        DeviceOnlineStatus latest = context.drainLatest();
        DeviceOnlineStatus candidate;
        while ((candidate = context.drainLatest()) != null) {
            latest = candidate;
        }
        return latest;
    }

    /**
     * 从连接获取设备的协议版本
     * 连接不存在时返回默认版本 V1.0
     * @param deviceId 设备ID
     */
    private String getProtocolVersionFromConnection(Long deviceId) {
        return wsConnectionManagerPort.getConnection(deviceId)
                .map(conn -> conn.getProtocolVersion().getVersion())
                .orElse(ProtocolVersion.V1_0.getVersion());
    }

    /**
     * 处理离线状态下的设备重连
     * @param currentStatus 当前设备状态
     * @param source 上报数据源
     * @param clientIp 上报客户端IP
     */
    private DeviceOnlineStatus handleOfflineReconnect(DeviceOnlineStatus currentStatus,
                                                      ReportSource source, String clientIp) {
        String version = source == ReportSource.WEBSOCKET
                ? getProtocolVersionFromConnection(currentStatus.getDeviceId())
                : currentStatus.getVersion();
        return DeviceOnlineStatus.createReconnect(currentStatus, source, clientIp, version);
    }

    /**
     * 从初始状态转换为在线稳定状态
     * @param currentStatus 当前设备状态
     * @param source 上报数据源
     * @param clientIp 上报客户端IP
     */
    private DeviceOnlineStatus transitionToOnline(DeviceOnlineStatus currentStatus,
                                                  ReportSource source, String clientIp) {
        long currentTime = System.currentTimeMillis();
        return DeviceOnlineStatus.builder()
                .deviceId(currentStatus.getDeviceId())
                .lastReportTime(currentTime)
                .lastReportSource(source)
                .status(OnlineStatus.ONLINE)
                .statusChangeTime(currentTime)
                .onlineStartTime(currentStatus.getOnlineStartTime())
                .clientIp(clientIp)
                .version(currentStatus.getVersion())
                .build();
    }

    /**
     * 刷新在线状态（仅更新心跳时间）
     * @param currentStatus 当前设备状态
     * @param source 上报数据源
     * @param clientIp 上报客户端IP
     */
    private DeviceOnlineStatus refreshOnlineStatus(DeviceOnlineStatus currentStatus,
                                                   ReportSource source, String clientIp) {
        String version = source == ReportSource.WEBSOCKET
                ? getProtocolVersionFromConnection(currentStatus.getDeviceId())
                : null;

        DeviceOnlineStatus refreshStatus = DeviceOnlineStatus.refreshOnline(
                currentStatus.getDeviceId(), source, clientIp, version);

        // 如果WebSocket获取版本失败，保持原有版本
        if (refreshStatus.getVersion() == null) {
            refreshStatus.setVersion(currentStatus.getVersion());
        }
        return refreshStatus;
    }

}
