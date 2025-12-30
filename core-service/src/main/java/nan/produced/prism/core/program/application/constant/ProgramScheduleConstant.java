package nan.produced.prism.core.program.application.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProgramScheduleConstant {

    // ==================== Device Command ====================

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class DeviceCommandDefaults {

        public static final String AUTHOR_URL_EMPTY = "";

        public static final int KARMA_DEFAULT = 1;
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class DeviceCommandRaw {

        public static final String PROGRAM_DIRTY = "{\"program\":\"dirty\"}";

        public static final String SCHEDULE = "{\"program\":\"schedule\"}";
    }

    // ==================== Program Publish/Unpublish ====================

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ProgramPublishAction {

        public static final String SKIP = "skip";

        public static final String DEPLOY = "deploy";

        public static final String NO_CHANGE = "no-change";

        public static final String UPDATE = "update";

        public static final String ROLLBACK = "rollback";

        public static final String UNDEPLOY = "undeploy";
    }

    // ==================== Schedule Binding ====================

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ScheduleBindingStatus {

        public static final String SKIP = "skip";

        public static final String BOUND = "bound";

        public static final String NO_CHANGE = "no-change";

        public static final String CONFLICT = "conflict";
    }

    // ==================== Schedule Contents Rules ====================

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ScheduleContentsType {

        public static final String ROTATION = "rotation";

        public static final String SPOT = "spot";
    }

    // ==================== Device Program Allowlist ====================

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class DeviceProgramSource {

        public static final String BOTH = "both";

        public static final String DIRECT_PUBLISH = "direct-publish";

        public static final String SCHEDULE = "schedule";
    }

    // ==================== Terminal Schedule Protocol ====================

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ScheduleProtocolKeys {

        public static final String NAME = "name";

        public static final String TYPE = "type";

        public static final String OPERATION = "operation";

        public static final String AUTHOR_URL = "author_url";

        public static final String KARMA = "karma";

        public static final String CONTENT = "content";

        public static final String OP_TIME = "op_time";

        public static final String IF_LIMIT_DATE = "if_limit_date";

        public static final String LIMIT_DATE = "limit_date";

        public static final String IF_LIMIT_WEEKDAY = "if_limit_weekday";

        public static final String LIMIT_WEEKDAY = "limit_weekday";

        public static final String VALUE = "value";
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ScheduleProtocolValues {

        public static final String TYPE_COMMAND = "command";

        public static final String CONTENT_NAME_SWITCH = "Switch";

        public static final String CONTENT_NAME_VALUE = "Value";

        public static final String SWITCH_VALUE_SYNC = "sync";

        public static final String SWITCH_VALUE_ASYNC = "async";
    }

    // ==================== Device Action Body ====================

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class DeviceActionBodyKeys {

        public static final String COMMAND = "command";

        public static final String BRIGHTNESS = "brightness";

        public static final String MUSIC_VOLUME = "musicvolume";

        public static final String VOLUME = "volume";

        public static final String COLOR_TEMP = "colortemp";

        public static final String INPUT_MODE = "inputmode";
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class DeviceActionBodyValues {

        public static final String POWER_SLEEP = "sleep";

        public static final String POWER_WAKEUP = "wakeup";

        public static final String POWER_REBOOT = "reboot";

        public static final String INPUT_MODE_HDMI = "hdmi";

        public static final String INPUT_MODE_DVI = "dvi";
    }
}

