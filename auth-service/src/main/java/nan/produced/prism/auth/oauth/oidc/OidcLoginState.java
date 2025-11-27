package nan.produced.prism.auth.oauth.oidc;

import lombok.Getter;

@Getter
public enum OidcLoginState {

    LOGIN(1, "已登录"),
    LOGOUT(2, "已登出"),
    EXPIRED(3, "已过期");

    /**
     * 登录状态
     */
    private final Integer code;
    /**
     * 登录状态描述
     */
    private final String desc;

    OidcLoginState(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

