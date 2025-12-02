package nan.produced.prism.auth.common.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.common.exception.BaseServiceException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.common.exception.InfraException;
import nan.produced.prism.auth.common.response.ApiResponse;
import org.springframework.stereotype.Component;

/**
 * Feign 错误解码器
 * 将远端服务的异常响应体解析为本地异常
 *
 * @author Nan
 */
@Slf4j
@Component
public class FeignErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Exception decode(String methodKey, Response response) {
        try {
            String body = new String(response.body().asInputStream().readAllBytes());
            log.warn("Feign 调用失败: method={}, status={}, body={}", methodKey, response.status(), body);

            // 尝试解析为 ApiResponse
            Exception exception = tryParseApiResponse(body);
            if (exception != null) {
                return exception;
            }

            // 返回通用异常
            return new InfraException(
                ErrorCode.EXTERNAL_SERVICE_ERROR,
                "远端服务调用失败: " + response.status()
            );
        } catch (Exception e) {
            log.error("Feign 错误解码异常: {}", e.getMessage(), e);
            return new InfraException(
                ErrorCode.EXTERNAL_SERVICE_ERROR,
                "服务调用异常: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * 尝试解析远程服务的响应体为 ApiResponse
     *
     * @param body 响应体内容
     * @return 解析成功返回对应的异常，否则返回 null
     */
    private Exception tryParseApiResponse(String body) {
        try {
            ApiResponse<?> apiResponse = objectMapper.readValue(body, ApiResponse.class);
            if (apiResponse.getCode() != null) {
                // 返回相应的服务异常
                return new InfraException(
                    ErrorCode.EXTERNAL_SERVICE_ERROR,
                    apiResponse.getMessage()
                );
            }
        } catch (IOException e) {
            log.debug("无法解析 ApiResponse: {}", e.getMessage());
        }
        return null;
    }
}
