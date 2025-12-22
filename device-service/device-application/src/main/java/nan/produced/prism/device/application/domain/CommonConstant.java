package nan.produced.prism.device.application.domain;

public class CommonConstant {

    private CommonConstant(){}

    public static final class Device {

        private Device() {
        }

        public static final String DEVICE_ID = "deviceId";

        public static final String LAST_REPORT_TIME = "lastReportTime";

        public static final String LAST_REPORT_SOURCE = "lastReportSource";

        public static final String STATUS = "status";

        public static final String STATUS_CHANGE_TIME = "statusChangeTime";

        public static final String ONLINE_START_TIME = "onlineStartTime";

        public static final String REPORT_SOURCE = "reportSource";

        public static final String CLIENT_IP = "clientIp";

        public static final String STATUS_EVENT_TYPE = "statusEventType";

        public static final String VERSION = "version";

        public static final String ONLINE_TIME = "onlineTime";

        public static final String OFFLINE_TIME = "offlineTime";

        public static final String ONLINE = "online";

        public static final String OFFLINE = "offline";

    }

    public static final class Command {

        public static final String CONFIRM = "confirm";

        public static final String EXPIRED = "expired";

        public static final String COMMAND_ID = "commandId";

        public static final String QUEUE_ID = "queueId";
    }


    public static final class Report {

        /**
         * 设备属性上报
         * <p>/wp-json/screen/v1/status</p>
         */
        public static final String PROPERTIES = "properties";

        /**
         * 素材播放记录上报
         * <p>/wp-json/led/flowfee</p>
         */
        public static final String MEDIA_PLAY_RECORD = "mediaPlayRecord";

        /**
         * 节目播放记录上报
         * <p>/wp-json/led/flowfee/v2/program</p>
         */
        public static final String PROGRAM_PLAY_RECORD = "programPlayRecord";

        /**
         * 终端日志上报
         * <p>/wp-json/led/monitor/log</p>
         */
        public static final String DEVICE_LOG = "deviceLog";

        /**
         * 传感器数据上报
         * <P>/wp-json/led/v2/monitor</P>
         */
        public static final String SENSOR_DATA = "sensorData";

        /**
         * 素材下载进度上报
         * <p>/wp-json/screen/v1/info</p>
         */
        public static final String DOWNLOADING_PROGRESS = "downloadingProgress";

        /**
         * 设备截图上报
         * <p>/wp-json/wp/v2/media</p>
         */
        public static final String SCREENSHOT = "screenshot";

        /**
         * 设备在线时长上报
         * <p>device-service生成</p>
         */
        public static final String ONLINE_TIME = "onlineTime";
    }

}
