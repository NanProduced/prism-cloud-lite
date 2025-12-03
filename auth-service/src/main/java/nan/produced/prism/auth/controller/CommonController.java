package nan.produced.prism.auth.controller;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "公共接口")
@RestController
@RequiredArgsConstructor
public class CommonController {

    private final JWKSource<SecurityContext> jwkSource;

    @Operation(summary = "获取公钥")
    @GetMapping("/oauth2/jwks")
    public Map<String, Object> publicKey() throws Exception {
        JWKSelector selector = new JWKSelector(new JWKMatcher.Builder().build());
        List<JWK> jwks = jwkSource.get(selector,  null);
        JWKSet jwkSet = new JWKSet(jwks);
        return jwkSet.toJSONObject();
    }
}
