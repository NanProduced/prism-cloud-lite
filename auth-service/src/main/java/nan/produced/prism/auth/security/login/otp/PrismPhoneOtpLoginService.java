package nan.produced.prism.auth.security.login.otp;

import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.security.login.PrismLoginInterface;
import nan.produced.prism.auth.security.principal.PrismUserPrincipal;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Map;

public class PrismPhoneOtpLoginService implements PrismLoginInterface {

    @Override
    public PrismUserPrincipal loadUserByAuthParams(Map<String, String> authParams) throws UsernameNotFoundException {
        throw new BizException(ErrorCode.INVALID_PARAMETER, "手机号验证码登录暂未开放");
    }

    @Override
    public void authenticateUser(Map<String, String> authParams, PrismUserPrincipal userPrincipal) throws AuthenticationException {
        throw new BizException(ErrorCode.INVALID_PARAMETER, "手机号验证码登录暂未开放");
    }
}
