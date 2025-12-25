package nan.produced.prism.auth.security.login.otp;

import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.security.login.LoginAuthTypeConstants;
import nan.produced.prism.auth.security.login.PrismLoginInterface;
import nan.produced.prism.auth.security.otp.PnvService;
import nan.produced.prism.auth.security.principal.PrismUserPrincipal;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 手机验证码登录
 *
 * @author Nan
 */
public class PrismPhoneOtpLoginService implements PrismLoginInterface {

    private final UserDetailsService userDetailsService;
    private final PnvService pnvService;

    public PrismPhoneOtpLoginService(UserDetailsService userDetailsService, PnvService pnvService) {
        this.userDetailsService = userDetailsService;
        this.pnvService = pnvService;
    }

    @Override
    public PrismUserPrincipal loadUserByAuthParams(Map<String, String> authParams) throws UsernameNotFoundException {
        String phone = authParams.get(LoginAuthTypeConstants.PHONE);
        if (!StringUtils.hasText(phone)) {
            throw new UsernameNotFoundException("phone is required");
        }
        return (PrismUserPrincipal) userDetailsService.loadUserByUsername(phone);
    }

    @Override
    public void authenticateUser(Map<String, String> authParams, PrismUserPrincipal userPrincipal) throws AuthenticationException {
        String pnvCode = authParams.get(LoginAuthTypeConstants.AUTH_CODE);
        if (!StringUtils.hasText(pnvCode)) {
            throw new BadCredentialsException("PNV Code is blank");
        }

        String phone = userPrincipal.getPhone();
        if (!StringUtils.hasText(phone)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "用户未绑定手机");
        }

        pnvService.verifyPnvCode(phone, pnvCode);
    }
}
