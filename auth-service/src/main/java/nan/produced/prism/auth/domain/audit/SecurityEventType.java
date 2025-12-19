package nan.produced.prism.auth.domain.audit;

/**
 * Security-related audit event types shown in Dashboard Settings/Security.
 */
public enum SecurityEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    PASSWORD_CHANGED,
    SESSION_REVOKED,
    SESSIONS_REVOKED
}

