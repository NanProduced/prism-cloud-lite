package nan.produced.prism.core.media.infrastructure.web;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.media.application.dto.TranscodeCreateRequest;
import nan.produced.prism.core.media.application.dto.TranscodeCreateResponse;
import nan.produced.prism.core.media.application.dto.TranscodeRetryRequest;
import nan.produced.prism.core.media.application.dto.TranscodeRetryResponse;
import nan.produced.prism.core.media.application.service.MediaTranscodeService;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/media-library")
public class MediaTranscodeController {

    private final MediaTranscodeService mediaTranscodeService;

    @PostMapping("/assets/{assetId}/transcode")
    public ResponseEntity<BffResponse<TranscodeCreateResponse>> transcode(
        @PathVariable("assetId") String assetId,
        @RequestBody @Validated TranscodeCreateRequest request) {

        var user = CloudAuthContext.getCurrentUser();
        UUID userId = UUID.fromString(user.userUuid());

        var response = mediaTranscodeService.createTranscodeTask(userId, user.tier(), assetId, request);
        return ResponseEntity.ok(BffResponse.success(response).withTraceId(TraceUtils.getTraceId()));
    }

    @PostMapping("/transcode/{taskId}/retry")
    public ResponseEntity<BffResponse<TranscodeRetryResponse>> retry(
        @PathVariable("taskId") String taskId,
        @RequestBody @Validated TranscodeRetryRequest request) {

        var user = CloudAuthContext.getCurrentUser();
        UUID userId = UUID.fromString(user.userUuid());

        var response = mediaTranscodeService.retryTranscodeTask(userId, user.tier(), taskId, request);
        return ResponseEntity.ok(BffResponse.success(response).withTraceId(TraceUtils.getTraceId()));
    }
}
