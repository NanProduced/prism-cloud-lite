package nan.produced.prism.device.application.port.outbound.status;

import nan.produced.prism.device.application.domain.status.DeviceOnlineStatus;

import java.util.List;

/**
 * 设备状态更新接口(异步缓冲池)
 *
 * @author Nan
 */
public interface DeviceOnlineStatusUpdatePort {

    /**
     * 异步提交设备状态更新
     * 将状态更新请求加入缓冲池，由后台线程批量处理
     *
     * @param status 设备状态
     */
    void submitStatusUpdate(DeviceOnlineStatus status);

    /**
     * 异步批量提交设备状态更新
     *
     * @param statusList 设备状态列表
     */
    void submitBatchStatusUpdate(List<DeviceOnlineStatus> statusList);

    /**
     * 立即刷新缓冲池
     * 强制将缓冲池中的所有待处理状态立即提交
     */
    void flushBuffer();

    /**
     * 定时刷新设备状态缓冲池。
     * <p>
     * 该方法由 Spring {@code @Scheduled} 注解自动触发执行。
     * <b>请勿在业务代码中手动调用此方法，因为它专为后台定时任务设计。</b>
     *
     */
    void scheduledFlush();


}
