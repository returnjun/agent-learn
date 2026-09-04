package top.daoha.trigger.auth;

/**
 * 固定账号登录的会话标识。
 */
public final class AuthenticationSession {

    public static final String AUTHENTICATED_ATTRIBUTE = "AUTHENTICATED";
    public static final int MAX_INACTIVE_INTERVAL_SECONDS = 8 * 60 * 60;

    private AuthenticationSession() {
    }
}
