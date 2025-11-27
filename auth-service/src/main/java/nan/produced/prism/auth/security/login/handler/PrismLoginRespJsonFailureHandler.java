package nan.produced.prism.auth.security.login.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.utils.HttpUtils;
import nan.produced.prism.auth.utils.JsonUtils;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

@Slf4j
public class PrismLoginRespJsonFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {

        log.debug("Login failure: {}", exception.getMessage());

        String respJson = JsonUtils.toJson(PrismLoginRespJson.builder()
                .code("500")
                .msg(exception.getMessage())
                .build());

        HttpUtils.responseJson(respJson, response);
    }
}
