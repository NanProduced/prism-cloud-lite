package nan.produced.prism.auth.security.authentication;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.SpringSecurityCoreVersion;
import org.springframework.util.Assert;

import java.io.Serial;
import java.util.Collection;
import java.util.Map;

/**
 * 认证信息
 *
 * @author Nan
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonIgnoreProperties({"name", "authParams"})
public class PrismAuthenticationToken extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = SpringSecurityCoreVersion.SERIAL_VERSION_UID;

    private Object principal;

    private Object credentials;

    @Getter
    @Setter
    private Map<String, String> authParams;

    public PrismAuthenticationToken() {
        super(null);
    }

    /**
     * 未认证时的认证信息
     *
     * @param authParams 登录参数
     */
    public PrismAuthenticationToken(Map<String, String> authParams) {
        super(null);
        this.authParams = authParams;
        super.setAuthenticated(false);
    }

    /**
     * 认证成功后的认证信息
     *
     * @param principal 用户身份或用户唯一标识
     * @param credentials 用户凭证
     * @param authorities 用户权限
     * @param authParams 登录参数
     */
    public PrismAuthenticationToken(Object principal, Object credentials, Collection<? extends GrantedAuthority> authorities, Map<String, String> authParams) {
        super(authorities);
        Assert.notNull(principal, "Principal required");
        this.principal = principal;
        this.credentials = credentials;
        this.authParams = authParams;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return this.credentials;
    }

    @Override
    public Object getPrincipal() {
        return this.principal;
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
    }


}
