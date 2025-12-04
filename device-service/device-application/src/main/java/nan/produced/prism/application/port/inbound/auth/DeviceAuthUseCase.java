package nan.produced.prism.application.port.inbound.auth;

import nan.produced.prism.application.dto.request.AuthRequest;
import nan.produced.prism.application.dto.result.AuthResult;

public interface DeviceAuthUseCase {

    AuthResult authenticate(AuthRequest authRequest);
}
