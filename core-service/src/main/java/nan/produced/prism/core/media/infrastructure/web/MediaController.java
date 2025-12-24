package nan.produced.prism.core.media.infrastructure.web;

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
    public ResponseEntity<BffResponse<BatchFinalizeResponse>> batchFinalize(
            @RequestBody @Validated BatchFinalizeRequest request) {

        var userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        log.debug("BatchFinalize request: userId={}, items={}", userId, request.getItems().size());

        var response = mediaService.batchFinalize(request, userId);
        return ResponseEntity.ok(BffResponse.success(response));
    }
}
