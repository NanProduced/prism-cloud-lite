package nan.produced.prism.device.application.properties;

import lombok.Data;

/**
 * 设备相关属性(应用层的配置模型)
 *
 * @author Nan
 */
@Data
public class DomainDeviceProps {

    private OnlineStatus onlineStatus = new OnlineStatus();

    @Data
    public static class OnlineStatus {

        /**
         * Redis设备在线状态机初始TTL(秒) - 设备上线和心跳时使用
         */
        private long defaultCacheTtl = 3600; // 1小时

        /**
         * Redis设备重连窗口TTL(秒) - 设备离线后等待重连的时间
         */
        private long reconnectCacheTtl = 120;

        /**
         * 设备离线检查间隔(毫秒)
         */
        private long offlineCheckInterval = 30_000;

        /**
         * 设备离线检测任务初始延迟(毫秒)
         */
        private long offlineCheckInitialDelay = 60_000;

        /**
         * 设备在线校准间隔(毫秒)
         */
        private long calibrationInterval = 300_000;

        /**
         * 设备离线超时阈值(毫秒)
         * 默认70秒 = 60秒业务超时 + 10秒容错
         */
        private long offlineThreshold = 70_000;

        /**
         * 缓冲池配置
         */
        private BufferPool bufferPool = new BufferPool();

        /**
         * 流式查询配置
         */
        private StreamQuery streamQuery = new StreamQuery();


    }

    /**
     * 缓冲池配置
     */
    @Data
    public static class BufferPool {

        /**
         * 缓冲窗口时间(毫秒)
         */
        private long windowMs = 2000;

        /**
         * 最大缓冲数量
         */
        private int maxSize = 10000;

        /**
         * 批处理大小
         */
        private int batchSize = 100;

        /**
         * 紧急刷新阈值(百分比)
         */
        private double emergencyFlushThreshold = 0.8;

        /**
         * 缓冲池任务延迟(毫秒)
         * 状态更新和登录更新缓冲池任务的延迟
         */
        private long flushTaskDelayMs = 15_000;
    }

    /**
     * 流式查询配置
     */
    @Data
    public static class StreamQuery {

        /**
         * 是否启用Redis Stream查询
         */
        private boolean enabled = true;

        /**
         * 分页大小
         */
        private int pageSize = 1000;

        /**
         * 最大迭代次数
         */
        private int maxIterations = 1000;

        /**
         * 查询超时时间(毫秒)
         */
        private long timeoutMs = 5000;
    }
}
