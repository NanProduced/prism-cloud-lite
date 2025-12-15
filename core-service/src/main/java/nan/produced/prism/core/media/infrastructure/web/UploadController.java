package nan.produced.prism.core.media.infrastructure.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.media.application.dto.*;
import nan.produced.prism.core.media.application.service.UploadService;
import nan.produced.prism.core.media.application.service.UploadService.UploadValidationException;
import nan.produced.prism.core.media.infrastructure.config.UploadRouteProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 上传控制器
 *
 * Better Upload 协议端点
 *
 * 注意：此控制器的响应不包裹在 BffResponse 中，直接返回 Better Upload 协议格式
 */
@Slf4j
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;
    private final UploadRouteProperties uploadRouteProperties;

    /**
     * Better Upload 协议端点
     *
     * 处理上传请求，返回预签名 URL
     *
     * @param request Better Upload 请求
     * @param jwt     JWT Token（从 OAuth2 Resource Server 获取）
     * @return Better Upload 响应（普通上传或 Multipart）
     */
    @PostMapping
    public ResponseEntity<?> upload(
            @Valid @RequestBody BetterUploadRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        // 验证路由是否存在
        if (!uploadRouteProperties.hasRoute(request.getRoute())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BetterUploadErrorResponse.routeNotFound(request.getRoute()));
        }

        // 获取用户 ID
        var userId = extractUserId(jwt);

        log.info("Processing upload request: route={}, files={}, user={}",
                request.getRoute(), request.getFiles().size(), userId);

        try {
            var response = uploadService.processUploadRequest(request, userId);
            return ResponseEntity.ok(response);
        } catch (UploadValidationException e) {
            log.warn("Upload validation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getErrorResponse());
        }
    }

    /**
     * MD5 秒传检查端点
     *
     * Lite 版本采用全局去重（所有用户共享同一 S3 桶），返回 fileEntityId 而非 S3 key（安全考虑）
     *
     * @param request 秒传检查请求
     * @param jwt     JWT Token
     * @return 检查结果
     */
    @PostMapping("/check")
    public ResponseEntity<DuplicateCheckResponse> checkDuplicate(
            @Valid @RequestBody DuplicateCheckRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        var userId = extractUserId(jwt);

        log.info("Processing duplicate check: files={}, user={}",
                request.getFiles().size(), userId);

        // TODO: 实现业务层的 MD5 查询逻辑
        // 当前返回所有文件都不存在，需要后续实现数据库查询
        var results = new ArrayList<DuplicateCheckResponse.FileCheckResult>();

        for (var file : request.getFiles()) {
            var result = DuplicateCheckResponse.FileCheckResult.builder()
                    .clientId(file.getClientId())
                    .duplicate(false)
                    .build();

            // TODO: 仅当有 MD5 时才查询（大文件可能跳过 MD5 校验）
            // if (StringUtils.hasText(file.getMd5())) {
            //     var existing = fileEntityRepository.findByMd5(file.getMd5());
            //     if (existing.isPresent()) {
            //         result = DuplicateCheckResponse.FileCheckResult.builder()
            //                 .clientId(file.getClientId())
            //                 .duplicate(true)
            //                 .fileEntityId(existing.get().getId())
            //                 .build();
            //     }
            // }

            results.add(result);
        }

        return ResponseEntity.ok(DuplicateCheckResponse.builder()
                .results(results)
                .build());
    }

    /**
     * 从 JWT 中提取用户 ID
     */
    private String extractUserId(Jwt jwt) {
        // 尝试从 sub claim 获取
        var sub = jwt.getSubject();
        if (sub != null) {
            return sub;
        }

        // 尝试从 user_id claim 获取
        var userId = jwt.getClaimAsString("user_id");
        if (userId != null) {
            return userId;
        }

        // 兜底使用 "anonymous"
        return "anonymous";
    }

    // ==================== 异常处理 ====================

    /**
     * 处理验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BetterUploadErrorResponse> handleValidationException(
            MethodArgumentNotValidException e) {

        var errors = e.getBindingResult().getFieldErrors();
        var message = errors.isEmpty()
                ? "Invalid request body"
                : errors.get(0).getDefaultMessage();

        return ResponseEntity.badRequest()
                .body(BetterUploadErrorResponse.invalidRequest(message));
    }

    /**
     * 处理上传验证异常
     */
    @ExceptionHandler(UploadValidationException.class)
    public ResponseEntity<BetterUploadErrorResponse> handleUploadValidationException(
            UploadValidationException e) {

        return ResponseEntity.badRequest().body(e.getErrorResponse());
    }

    /**
     * 处理其他异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BetterUploadErrorResponse> handleException(Exception e) {
        log.error("Unexpected error during upload", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BetterUploadErrorResponse.rejected("Internal server error"));
    }
}
