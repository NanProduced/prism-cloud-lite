package nan.produced.prism.core.integration.auth.dto;

import lombok.Data;

@Data
public class AuthInternalUserResponse {
    private String publicId;
    private String userId;
    private String email;
    private String displayName;
    private String phone;
}
