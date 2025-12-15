package nan.produced.prism.core.task.application.domain;

public enum TaskStatus {

    PENDING,    // 待处理（队列中）
    RUNNING,    // 处理中
    SUCCESS,    // 成功
    FAILED      // 失败
}
