package nan.produced.prism.core.media.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "素材库-上传", description = "Better Upload 协议：获取对象存储直传预签名 URL（面向 SPA，经由 Gateway 访问）")
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
    @Operation(
        summary = "Better Upload：获取预签名 URL",
        description = """
            Better Upload 协议端点：根据 `route`（上传路由）生成对象存储（S3）直传预签名 URL。

            典型流程（素材库上传）：
            1) 调用本接口获取预签名 URL；
            2) 客户端使用预签名 URL 直传到对象存储（single/multipart）；
            3) 上传成功后调用 `POST /api/v1/media-library/batch-finalize` 进行落库。

            注意：本接口**不返回** `BffResponse<T>`，而是直接返回 Better Upload 协议 JSON（便于前端 @better-upload/client 解析）。
            """)
    @ApiResponse(
        responseCode = "200",
        description = "成功返回预签名 URL（普通上传或 multipart 上传）",
        content = @Content(mediaType = "application/json", schema = @Schema(oneOf = { BetterUploadResponse.class, BetterUploadMultipartResponse.class })))
    @ApiResponse(
        responseCode = "400",
        description = "请求不合法或业务校验失败（Better Upload 协议错误体）",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = BetterUploadErrorResponse.class)))
    @ApiResponse(
        responseCode = "404",
        description = "route 不存在（Better Upload 协议错误体）",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = BetterUploadErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
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
