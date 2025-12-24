package nan.produced.prism.core.common.messaging;

/**
 * RabbitMQ常量
 *
 * @author Nan
 */
public final class MessagingConstants {

    private MessagingConstants() {
    }

    public static final String MESSAGE_VERSION = "1.0";

    /**
     * 交换机
     */
    public static final class Exchanges {
        // 设备事件交换机（设备在线状态、设备指令响应、设备数据上报）
        public static final String DEVICE_EVENTS = "device.events";
        // 业务服务事件通知交换机（core-service内部使用） 任务推送/任务结果通知
        public static final String CORE_NOTIFICATIONS = "core.notifications";

        private Exchanges() {
        }
    }

    /**
     * 消息队列
     */
    public static final class Queues {
        // 设备在线状态队列
        public static final String DEVICE_STATUS = "core-device-status-q";
        // 设备指令响应队列
        public static final String DEVICE_COMMAND = "core-device-command-q";
        // 设备数据上报队列
        public static final String DEVICE_REPORT = "core-device-report-q";
        // 业务异步任务推送/执行结果队列
        public static final String TASK_WORKER = "core-task-worker-q";
        // 前端spa通知队列
        public static final String COMMON_NOTIFY = "core-notify-q";
        // 面向前端的高频实时数据队列（传感器/GPS 等，仅用于 SSE，不落库到消息中心）
        public static final String REALTIME_NOTIFY = "core-realtime-q";

        private Queues() {
        }
    }

    /**
     * 路由键
     */
    public static final class RoutingKeys {
        // 设备状态路由键
        public static final String STATUS_ALL = "status.*";
        // 设备指令路由键
        public static final String COMMAND_ALL = "command.*";
        // 设备数据上报路由键
        public static final String REPORT_ALL = "report.*";
        // 业务异步任务推送路由键
        public static final String TASK_PENDING = "task.pending";
        // 业务异步任务执行结果路由键
        public static final String TASK_RESULT = "task.result";
        // 前端spa通知路由键
        public static final String NOTIFY_ALL = "notify.#";

        /**
         * 设备在线状态变化通知（面向前端）
         */
        public static final String NOTIFY_DEVICE_STATUS_CHANGED = "notify.device.status.changed";

        /**
         * 操作进度更新通知（面向前端）
         * <p>示例：指令确认/完成等：type=operation.updated</p>
         */
        public static final String NOTIFY_OPERATION_UPDATED = "notify.operation.updated";

        /**
         * 消息中心：新消息创建通知（面向前端）
         * <p>示例：type=message.created</p>
         */
        public static final String NOTIFY_MESSAGE_CREATED = "notify.message.created";

        /**
         * 消息中心：消息状态更新通知（面向前端）
         * <p>示例：type=message.updated</p>
         */
        public static final String NOTIFY_MESSAGE_UPDATED = "notify.message.updated";

        /**
         * 面向前端的高频实时数据（传感器/GPS 等）
         */
        public static final String REALTIME_ALL = "realtime.#";

        public static final String REALTIME_SENSOR_REPORTED = "realtime.sensor.reported";

        public static final String REALTIME_GPS_REPORTED = "realtime.gps.reported";

        private RoutingKeys() {
        }
    }

    public static final class CommandTypes {

        public static final String CONFIRM = "confirm";

        public static final String EXPIRED = "expired";
    }

    public static final class DeviceEventTypes {

        /**
         * 设备数据上报 - 设备属性
         */
        public static final String REPORT_PROPERTIES = "report.properties";

        /**
         *  设备数据上报 - 素材播放记录
         */
        public static final String REPORT_MEDIA_PLAY_RECORD = "report.mediaPlayRecord";

        /**
         * 设备数据上报 - 节目播放记录
         */
        public static final String REPORT_PROGRAM_PLAY_RECORD = "report.programPlayRecord";

        /**
         * 设备数据上报 - 设备日志
         */
        public static final String REPORT_DEVICE_LOG = "report.deviceLog";

        /**
         * 设备数据上报 - 传感器数据
         */
        public static final String REPORT_SENSOR_DATA = "report.sensorData";

        /**
         * 设备数据上报 - 素材下载进度
         */
        public static final String REPORT_DOWNLOADING_PROGRESS = "report.downloadingProgress";

        /**
         * 设备数据上报 - 设备截图
         */
        public static final String REPORT_SCREENSHOT = "report.screenshot";

        public static final String REPORT_ONLINE_TIME = "report.onlineTime";

    }
}
