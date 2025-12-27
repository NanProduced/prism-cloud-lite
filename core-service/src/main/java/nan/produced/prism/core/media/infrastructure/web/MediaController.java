package nan.produced.prism.core.media.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.media.application.dto.BatchFinalizeRequest;
import nan.produced.prism.core.media.application.dto.BatchFinalizeResponse;
import nan.produced.prism.core.media.application.dto.DuplicateCheckRequest;
import nan.produced.prism.core.media.application.dto.DuplicateCheckResponse;
import nan.produced.prism.core.media.application.service.MediaService;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 媒体库控制器
 * <p>
 * 处理媒体素材的秒传检查和批量落库请求
 *
 * @author Nan
 */
@Slf4j
@Tag(name = "素材库-上传", description = "秒传检查与批量落库（面向 SPA，经由 Gateway 访问）")
@RestController
@RequestMapping("/api/v1/media-library")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    /**
     * 秒传检查
     * <p>
     * 批量检查文件 MD5 是否已存在，支持秒传（即时上传）
     * 如果文件已存在，返回对应的 fileEntityId，客户端可直接使用该 ID 进行落库
     *
     * @param request 秒传检查请求（包含文件 MD5 列表）
     * @return 秒传检查结果
     */
    @PostMapping("/duplicate-check")
    @Operation(
        summary = "秒传检查（Duplicate Check）",
        description = """
            批量检查文件是否已存在，支持“秒传/去重”。

            - 每个文件必须带 `clientId`，用于与响应一一对应；
            - `md5` 可选（大文件可跳过），不提供时一定返回 duplicate=false；
            - 当 `duplicate=true` 时返回 `fileEntityId`，后续可在 `batch-finalize` 中直接引用实现秒传。
            """)
    @ApiResponse(
        responseCode = "200",
        description = "成功返回秒传检查结果",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = DuplicateCheckResponse.class)))
    @ApiResponse(responseCode = "400", description = "请求参数不合法")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<DuplicateCheckResponse>> duplicateCheck(
            @RequestBody @Validated DuplicateCheckRequest request) {

        log.debug("DuplicateCheck request: files={}", request.getFiles().size());

        var response = mediaService.duplicateCheck(request);
        return ResponseEntity.ok(BffResponse.success(response));
    }

    /**
     * 批量完成上传（落库）
     * <p>
     * 文件上传到 S3 成功后，调用此接口创建媒体素材记录
     * 支持混合场景：部分秒传 + 部分新上传
     * 使用 groupId 作为幂等键，重复请求会返回已存在的素材
     *
     * @param request 批量落库请求
     * @return 批量落库结果
     */
    @PostMapping("/batch-finalize")
    @Operation(
        summary = "批量落库（Batch Finalize）",
        description = """
            文件成功上传到对象存储（S3）后，调用本接口创建素材记录（Media Asset）。

            支持混合场景：
            - 秒传：传 `fileEntityId`；
            - 新上传：传 `s3Key`（Better Upload 返回的 object key）。

            幂等性：`groupId` 作为幂等键，重复请求会返回已存在的素材（`existed=true`），前端可安全重试。
            """)
    @ApiResponse(
        responseCode = "200",
        description = "成功创建/返回素材记录",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = BatchFinalizeResponse.class)))
    @ApiResponse(responseCode = "400", description = "请求参数不合法或文件引用无效（s3Key/fileEntityId）")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "文件夹/文件实体不存在或无权访问")
    public ResponseEntity<BffResponse<BatchFinalizeResponse>> batchFinalize(
            @RequestBody @Validated BatchFinalizeRequest request) {

        var userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        log.debug("BatchFinalize request: userId={}, items={}", userId, request.getItems().size());

        var response = mediaService.batchFinalize(request, userId);
        return ResponseEntity.ok(BffResponse.success(response));
    }
}
