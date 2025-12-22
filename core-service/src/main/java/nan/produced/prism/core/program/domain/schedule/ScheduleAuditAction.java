package nan.produced.prism.core.program.domain.schedule;

/**
 * 排程变更动作类型（Lite：面向用户查看历史）。
 */
public enum ScheduleAuditAction {
    CREATE,
    UPDATE,
    DELETE,
    BIND_DEVICES,
    UNBIND_DEVICE,
    PUSH
}

