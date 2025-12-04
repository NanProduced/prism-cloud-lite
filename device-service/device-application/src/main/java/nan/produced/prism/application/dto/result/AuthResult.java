package nan.produced.prism.application.dto.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResult {

    private boolean success;

    private Long deviceId;

    public static AuthResult success(Long deviceId) {
        return AuthResult.builder()
                .success(true)
                .deviceId(deviceId)
                .build();
    }

    public static AuthResult failed() {
        return AuthResult.builder()
                .success(false)
                .build();
    }
}
