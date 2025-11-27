package nan.produced.prism.auth.security.authentication;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 登录认证过滤器
 *
 * @author Nan
 */
public class PrismLoginAuthenticationProgressingFilter extends AbstractAuthenticationProcessingFilter {

    private boolean postOnly = true;

    public PrismLoginAuthenticationProgressingFilter(String defaultFilterProcessesUrl, AuthenticationManager authenticationManager) {
        super(defaultFilterProcessesUrl, authenticationManager);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException, ServletException {
        if (this.postOnly && !request.getMethod().equals("POST")) {
            throw new AuthenticationServiceException("Authentication method not supported: " + request.getMethod());
        }
        PrismAuthenticationToken authRequest = new PrismAuthenticationToken(getAuthParams(request));
        setDetails(request, authRequest);
        return getAuthenticationManager().authenticate(authRequest);
    }

    private Map<String, String> getAuthParams(HttpServletRequest request) {
        Map<String, String[]> originRequestMap = request.getParameterMap();
        Assert.notEmpty(originRequestMap, "Login params should not be empty");
        Map<String, String> authParams = new HashMap<>(originRequestMap.size());
        for (Map.Entry<String, String[]> entry : originRequestMap.entrySet()) {
            String paramName = entry.getKey();
            String[] paramValues = entry.getValue();
            if (null == paramValues || paramValues.length == 0 || !StringUtils.hasText(paramValues[0])) {
                continue;
            }
            authParams.put(paramName, paramValues[0]);
        }
        return authParams;
    }

    /**
     * 设置认证详情
     * @param request 请求
     * @param authRequest 认证请求
     */
    protected void setDetails(HttpServletRequest request, PrismAuthenticationToken authRequest) {
        authRequest.setDetails(authenticationDetailsSource.buildDetails(request));
    }
}
