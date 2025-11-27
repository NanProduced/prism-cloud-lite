package nan.produced.prism.auth.security.login;

import nan.produced.prism.auth.security.principal.PrismUserPrincipal;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Map;

/**
 * Prism平台通用登录 - 用户查询以及认证服务
 *
 * @author Nan
 * @see org.springframework.security.core.userdetails.UserDetailsService
 * @see org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider
 */
public interface PrismLoginInterface {

    /**
     * 根据登录参数查询用户
     *
     * @param authParams 登录参数
     * @return 用户信息
     * @throws UsernameNotFoundException 用户不存在
     */
    PrismUserPrincipal loadUserByAuthParams(Map<String, String > authParams) throws UsernameNotFoundException;

    /**
     * 认证用户
     *
     * @param authParams 登录参数
     * @param userPrincipal 用户信息
     * @throws AuthenticationException 认证失败
     */
    void authenticateUser(Map<String, String> authParams, PrismUserPrincipal userPrincipal) throws AuthenticationException;
}
