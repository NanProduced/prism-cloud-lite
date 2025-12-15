package nan.produced.prism.core.media.infrastructure.web;

import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.media.application.dto.BetterUploadErrorResponse;
import nan.produced.prism.core.media.application.exception.UploadValidationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Better Upload 专用异常处理器
 * <p>
 * 仅应用于 {@link BetterUploadController}，返回 Better Upload 协议格式而非 BffResponse。
 * 通过 {@code @Order(Ordered.HIGHEST_PRECEDENCE)} 确保优先级高于全局异常处理器。
 *
 * @author Nan
 */
@Slf4j
@RestControllerAdvice(assignableTypes = BetterUploadController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BetterUploadExceptionHandler {

    /**
     * 处理验证异常 - 返回 Better Upload 协议格式
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BetterUploadErrorResponse> handleValidationException(
            MethodArgumentNotValidException e) {

        var errors = e.getBindingResult().getFieldErrors();
        var message = errors.isEmpty()
                ? "Invalid request body"
                : errors.getFirst().getDefaultMessage();

        return ResponseEntity.badRequest()
                .body(BetterUploadErrorResponse.invalidRequest(message));
    }

    /**
     * 处理上传验证异常 - 返回 Better Upload 协议格式
     */
    @ExceptionHandler(UploadValidationException.class)
    public ResponseEntity<BetterUploadErrorResponse> handleUploadValidationException(
            UploadValidationException e) {

        return ResponseEntity.badRequest().body(e.getErrorResponse());
    }

    /**
     * 处理非法参数异常 - 返回 Better Upload 协议格式
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BetterUploadErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BetterUploadErrorResponse.rejected(e.getMessage()));
    }

    /**
     * 处理其他异常 - 返回 Better Upload 协议格式
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BetterUploadErrorResponse> handleException(Exception e) {
        log.error("Unexpected error during upload", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BetterUploadErrorResponse.rejected("Internal server error"));
    }
}
