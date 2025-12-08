package nan.produced.prism.device.application.domain.event;

import lombok.*;

/**
 * 异步缓冲区刷新事件
 * <p>用于触发异步服务的缓冲池刷新操作，解决@Async自调用代理失效问题</p>
 *
 * @author Nan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncBufferFlushEvent {

    /**
     * 缓冲区类型
     */
    private BufferType bufferType;

    /**
     * 服务实例引用
     */
    private Object serviceInstance;

    /**
     * 事件创建时间戳
     */
    private Long eventTime;

    /**
     * 缓冲区当前大小（可选）
     */
    private Integer bufferSize;

    /**
     * 设备ID（设备数据清理专用）
     */
    private Long deviceId;


    @Getter
    public enum BufferType {

        /**
         * 设备状态更新缓冲池
         */
        DEVICE_ONLINE_STATUS,

        /**
         * 终端登录更新缓冲池
         */
        DEVICE_LOGIN_UPDATE,

        /**
         * 设备数据清理缓冲池
         */
        DEVICE_DATA_CLEANUP;

    }

    /**
     * 创建设备状态刷新事件
     * @param serviceInstance 服务实例引用
     * @param bufferSize 缓冲区当前大小
     */
    public static AsyncBufferFlushEvent createDeviceOnlineStatusFlushEvent(Object serviceInstance, Integer bufferSize) {
        return AsyncBufferFlushEvent.builder()
                .bufferType(BufferType.DEVICE_ONLINE_STATUS)
                .serviceInstance(serviceInstance)
                .eventTime(System.currentTimeMillis())
                .bufferSize(bufferSize)
                .build();
    }

    /**
     * 创建登录更新刷新事件
     */
    public static AsyncBufferFlushEvent createDeviceLoginUpdateFlushEvent(Object serviceInstance, Integer bufferSize) {
        return AsyncBufferFlushEvent.builder()
                .bufferType(BufferType.DEVICE_LOGIN_UPDATE)
                .serviceInstance(serviceInstance)
                .eventTime(System.currentTimeMillis())
                .bufferSize(bufferSize)
                .build();
    }
}
