package nan.produced.prism.core.message.api;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MessageCenterConstants {

    public static final String TASK_STATUS_PENDING = "PENDING";

    public static final String TASK_STATUS_RUNNING = "RUNNING";

    public static final String TASK_STATUS_SUCCESS = "SUCCESS";

    public static final String TASK_STATUS_FAILED = "FAILED";
}

