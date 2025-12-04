package nan.produced.prism.device.infrastracture.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.domain.device.DeviceAccountStatus;
import nan.produced.prism.device.application.dto.request.AuthRequest;
import nan.produced.prism.device.application.dto.result.AuthResult;
import nan.produced.prism.device.application.port.inbound.auth.DeviceAuthUseCase;
import nan.produced.prism.device.common.exception.DeviceResponseException;
import nan.produced.prism.device.common.exception.business.BusinessErrorCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * 终端设备Basic Auth认证
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceAuthenticationProvider implements AuthenticationProvider {

    private final DeviceAuthUseCase deviceAuthUseCase;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String accountName = authentication.getName();
        String rawPassword = authentication.getCredentials().toString();
        if (StringUtils.isBlank(accountName) || StringUtils.isBlank(rawPassword)) {
            throw new DeviceResponseException(BusinessErrorCode.PARAMETER_MISSING);
        }

        AuthResult authResult = deviceAuthUseCase.authenticate(new AuthRequest(accountName, rawPassword));
        if (authResult.isSuccess()) {
            DevicePrincipal principal = new DevicePrincipal(authResult.getDeviceId(), DeviceAccountStatus.ENABLE);
            return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        }

        throw new AuthenticationCredentialsNotFoundException("Authentication failed");
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
