package nan.produced.prism.application.service;

import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.application.dto.request.AuthRequest;
import nan.produced.prism.application.dto.result.AuthResult;
import nan.produced.prism.application.port.inbound.auth.DeviceAuthUseCase;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DeviceAuthApplicationService implements DeviceAuthUseCase {

    @Override
    public AuthResult authenticate(AuthRequest authRequest) {
        return null;
    }
}
