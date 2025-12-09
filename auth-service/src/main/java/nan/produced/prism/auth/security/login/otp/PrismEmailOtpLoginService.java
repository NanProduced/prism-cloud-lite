package nan.produced.prism.auth.security.login.otp;

import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.security.login.LoginAuthTypeConstants;
import nan.produced.prism.auth.security.login.PrismLoginInterface;
import nan.produced.prism.auth.security.otp.EmailOtpService;
import nan.produced.prism.auth.security.principal.PrismUserPrincipal;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

public class PrismEmailOtpLoginService implements PrismLoginInterface {

    private final UserDetailsService userDetailsService;
    private final EmailOtpService emailOtpService;

    public PrismEmailOtpLoginService(UserDetailsService userDetailsService, EmailOtpService emailOtpService) {
        this.userDetailsService = userDetailsService;
        this.emailOtpService = emailOtpService;
    }

    @Override
    public PrismUserPrincipal loadUserByAuthParams(Map<String, String> authParams) throws UsernameNotFoundException {
        String email = normalizeEmail(authParams.get(LoginAuthTypeConstants.EMAIL));
        if (!StringUtils.hasText(email)) {
            throw new UsernameNotFoundException("email is required");
        }
        return (PrismUserPrincipal) userDetailsService.loadUserByUsername(email);
    }

    @Override
    public void authenticateUser(Map<String, String> authParams, PrismUserPrincipal userPrincipal) throws AuthenticationException {
        String otp = authParams.get(LoginAuthTypeConstants.AUTH_CODE);
        if (!StringUtils.hasText(otp)) {
            throw new BadCredentialsException("OTP is blank");
        }

        String email = userPrincipal.getEmail();
        if (!StringUtils.hasText(email)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "用户未绑定邮箱");
        }

        emailOtpService.verifyOtp(email, otp);
    }

    private String normalizeEmail(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
