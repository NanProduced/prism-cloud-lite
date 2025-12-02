package nan.produced.prism.auth.common.handler;

import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.common.exception.InfraException;
import nan.produced.prism.auth.common.response.BffResponse;
import nan.produced.prism.auth.utils.TraceUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * BFF/前端接口统一异常处理器
 * <p>
 * 仅处理 nan.produced.prism.auth.controller 包下的 Controller 异常
 * 返回 BffResponse 格式响应（遵循 service-standards.md 第3.2节）
 *
 * @author Nan
 */
@Slf4j
@RestControllerAdvice(basePackages = "nan.produced.prism.auth.controller")
public class BffExceptionHandler {

    /**
     * 处理参数验证异常（@Valid）
     * 根据字段名智能映射到精确的错误码
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BffResponse<Object>> handleMethodArgumentNotValidException(
        MethodArgumentNotValidException ex
    ) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        if (fieldError == null) {
            log.warn("MethodArgumentNotValidException with no field error: {}", ex.getMessage());
            return buildErrorResponse(ErrorCode.INVALID_PARAMETER);
        }

        String field = fieldError.getField();
        String message = fieldError.getDefaultMessage();

        log.warn("Validation failed for field: {}, message: {}", field, message);

        // 根据字段名映射到精确的错误码
        ErrorCode errorCode = mapFieldToErrorCode(field, message);
        return buildErrorResponse(errorCode);
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<BffResponse<Object>> handleBizException(BizException ex) {
        log.warn("Business exception: code={}, message={}", ex.getErrorCode().getCode(), ex.getMessage());
        return buildErrorResponse(ex.getErrorCode());
    }

    /**
     * 处理基础设施异常
     */
    @ExceptionHandler(InfraException.class)
    public ResponseEntity<BffResponse<Object>> handleInfraException(InfraException ex) {
        log.error("Infrastructure exception: code={}, message={}",
            ex.getErrorCode().getCode(), ex.getMessage(), ex);
        return buildErrorResponse(ex.getErrorCode());
    }

    /**
     * 处理未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BffResponse<Object>> handleException(Exception ex) {
        log.error("Unexpected exception: {}", ex.getMessage(), ex);
        return buildErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    /**
     * 构建错误响应
     */
    private ResponseEntity<BffResponse<Object>> buildErrorResponse(ErrorCode errorCode) {
        BffResponse<Object> response = BffResponse.error(errorCode)
            .withTraceId(TraceUtils.getTraceId());

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(response);
    }

    /**
     * 根据字段名和消息映射到精确的错误码
     */
    private ErrorCode mapFieldToErrorCode(String field, String message) {
        return switch (field) {
            case "email" -> {
                // 根据消息内容判断是格式错误还是空值错误
                if (message != null && message.contains("格式")) {
                    yield ErrorCode.INVALID_EMAIL_FORMAT;
                }
                yield ErrorCode.INVALID_PARAMETER;
            }
            case "otp" -> ErrorCode.INVALID_OTP;
            case "password" -> ErrorCode.INVALID_PASSWORD;
            case "verificationToken" -> ErrorCode.VERIFICATION_TOKEN_NOT_FOUND;
            default -> ErrorCode.INVALID_PARAMETER;
        };
    }
}
