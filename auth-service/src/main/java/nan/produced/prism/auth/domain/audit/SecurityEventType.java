package nan.produced.prism.auth.domain.audit;

/**
 * Security-related audit event types shown in Dashboard Settings/Security.
 */
public enum SecurityEventType {
    /**
     * 登录成功
     */
    LOGIN_SUCCESS,

    /**
     * 登录失败
     */
    LOGIN_FAILURE,

    /**
     * 密码修改成功
     */
    PASSWORD_CHANGED,

    /**
     * 登录会话撤销
     */
    SESSION_REVOKED,

    /**
     * 清空登录会话
     */
    SESSIONS_REVOKED
}

