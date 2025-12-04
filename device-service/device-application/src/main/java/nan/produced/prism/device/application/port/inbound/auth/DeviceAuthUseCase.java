package nan.produced.prism.device.application.port.inbound.auth;

import nan.produced.prism.device.application.dto.request.AuthRequest;
import nan.produced.prism.device.application.dto.result.AuthResult;

public interface DeviceAuthUseCase {

    AuthResult authenticate(AuthRequest authRequest);
}
