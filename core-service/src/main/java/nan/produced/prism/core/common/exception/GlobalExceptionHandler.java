package nan.produced.prism.core.common.exception;

import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 符合微服务统一开发规范第 2.2 节：统一异常处理
 * <p>
 * 重要：返回 BFF 格式而非 RPC/ApiResponse 格式
 * - BffResponse：用于REST接口返回给前端（success + error）
 * - ApiResponse：用于RPC/Feign调用（code + message + meta）
 * <p>
 * 职责：
 * 1. 捕获所有 BaseServiceException 及其子类（BizException、AuthException、InfraException）
 * 2. 将异常转换为 BFF 格式（符合service-standards.md第3.2节）
 * 3. 自动添加 traceId 便于追踪
 * 4. 记录错误日志
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理所有业务异常（BaseServiceException 及其子类）
     * <p>
     * 包括：
     * - BizException：业务校验失败
     * - AuthException：认证/授权失败
     * - InfraException：基础设施错误
     */
    @ExceptionHandler(BaseServiceException.class)
    public ResponseEntity<BffResponse<Void>> handleBaseServiceException(BaseServiceException e) {
        log.error("Service exception: code={}, message={}, httpStatus={}",
            e.getCode(), e.getMessage(), e.getHttpStatus(), e);

        // 通过错误码查询对应的ErrorCode枚举以获取displayMessage和retryable信息
        ErrorCode errorCode = findErrorCode(e.getCode());

        BffResponse<Void> response = BffResponse.<Void>error(
                errorCode != null ? errorCode : ErrorCode.INTERNAL_SERVER_ERROR,
                errorCode != null ? errorCode.getDisplayMessage() : "系统错误，请稍后重试"
            )
            .withTraceId(TraceUtils.getTraceId());

        return ResponseEntity
            .status(e.getHttpStatus())
            .body(response);
    }

    /**
     * 处理未预期的系统异常
     * <p>
     * 所有未被 BaseServiceException 捕获的异常都会走到这里
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BffResponse<Void>> handleException(Exception e) {
        log.error("Unexpected exception", e);

        BffResponse<Void> response = BffResponse.<Void>error(ErrorCode.INTERNAL_SERVER_ERROR)
            .withTraceId(TraceUtils.getTraceId());

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response);
    }

    /**
     * 根据错误码字符串查找对应的ErrorCode枚举
     * <p>
     * 例如："CORE-2401" → ErrorCode.UNAUTHORIZED
     */
    private ErrorCode findErrorCode(String code) {
        try {
            for (ErrorCode ec : ErrorCode.values()) {
                if (ec.getCode().equals(code)) {
                    return ec;
                }
            }
        } catch (Exception ignore) {
            // 忽略查询异常，返回null使用默认值
        }
        return null;
    }
}
