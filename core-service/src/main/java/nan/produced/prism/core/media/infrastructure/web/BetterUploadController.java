package nan.produced.prism.core.media.infrastructure.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.media.application.dto.*;
import nan.produced.prism.core.media.application.exception.UploadValidationException;
import nan.produced.prism.core.media.application.service.BetterUploadService;
import nan.produced.prism.core.media.infrastructure.config.UploadRouteProperties;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 上传控制器
 * <p>
 * Better Upload 协议端点
 * <p>
 * 注意：
 * - upload 方法使用 {@link BetterUploadExceptionHandler} 处理异常，返回 Better Upload 协议格式
 * - 其他方法使用全局 GlobalExceptionHandler，返回 BffResponse 格式
 */
@Slf4j
@RestController
@RequestMapping({ "/api/upload"})
@RequiredArgsConstructor
public class BetterUploadController {

    private final BetterUploadService betterUploadService;
    private final UploadRouteProperties uploadRouteProperties;

    /**
     * Better Upload 协议端点
     * <p>
     * 处理上传请求，返回预签名 URL
     *
     * @param request Better Upload 请求
     * @return Better Upload 响应（普通上传或 Multipart）
     */
    @PostMapping
    public ResponseEntity<?> upload(
            @Valid @RequestBody BetterUploadRequest request) {

        String userId = CloudAuthContext.getCurrentUser().userUuid();

        // 验证路由是否存在
        if (!uploadRouteProperties.hasRoute(request.getRoute())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BetterUploadErrorResponse.routeNotFound(request.getRoute()));
        }

        try {
            var response = betterUploadService.processUploadRequest(request, userId);
            return ResponseEntity.ok(response);
        } catch (UploadValidationException e) {
            log.warn("BetterUpload - Upload validation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getErrorResponse());
        }
    }

}
