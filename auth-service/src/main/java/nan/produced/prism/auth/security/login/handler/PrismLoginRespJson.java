package nan.produced.prism.auth.security.login.handler;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PrismLoginRespJson {

    private String code;

    private String msg;

    private String redirectUrl;
}
