package nan.produced.prism.core.media.application.service;

import java.time.Instant;
import java.util.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.messaging.MessagingConstants;
import nan.produced.prism.core.media.application.constant.MediaTranscodeConstant;
import nan.produced.prism.core.media.application.dto.TranscodeCreateRequest;
import nan.produced.prism.core.media.application.dto.TranscodeCreateResponse;
import nan.produced.prism.core.media.application.dto.TranscodeRetryRequest;
import nan.produced.prism.core.media.application.dto.TranscodeRetryResponse;
import nan.produced.prism.core.media.application.repository.MediaAssetRepository;
import nan.produced.prism.core.media.application.repository.MediaFolderRepository;
import nan.produced.prism.core.media.application.util.MediaFolderIdUtils;
import nan.produced.prism.core.media.infrastructure.config.TranscodeProperties;
import nan.produced.prism.core.media.infrastructure.messaging.TranscodeTaskPendingMessage;
import nan.produced.prism.core.message.api.MessageCenterFacade;
import nan.produced.prism.core.user.api.StorageFileType;
import nan.produced.prism.core.user.api.StorageFileTypeResolver;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaTranscodeService {

    private static final String TIER_PRO = "PRO";
    private static final int PRIORITY_FREE = 0;
    private static final int PRIORITY_PRO = 9;

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaFolderRepository mediaFolderRepository;
    private final TranscodeProperties transcodeProperties;
    private final MessageCenterFacade messageCenterFacade;
    private final RabbitTemplate rabbitTemplate;

    public TranscodeCreateResponse createTranscodeTask(UUID userId,
                                                      String tier,
                                                      String assetId,
                                                      TranscodeCreateRequest request) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (!StringUtils.hasText(assetId)) {
            throw new BizException(ErrorCode.MEDIA_ASSET_NOT_FOUND);
        }
        if (request == null || !StringUtils.hasText(request.getPresetId())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "presetId is required");
        }

        var asset = mediaAssetRepository.findWithFilesByIdAndUserId(assetId.trim(), userId)
            .orElseThrow(() -> new BizException(ErrorCode.MEDIA_ASSET_NOT_FOUND));

        var original = asset.getOriginalFile();
        String mimeType = original != null ? original.getMimeType() : null;
        if (StorageFileTypeResolver.fromMimeType(mimeType) != StorageFileType.VIDEO) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Only video assets are supported for transcode");
        }

        String presetId = validatePresetId(request.getPresetId());
        String normalizedTargetFolderId = MediaFolderIdUtils.normalizeFolderId(request.getTargetFolderId());
        if (StringUtils.hasText(normalizedTargetFolderId)
            && !mediaFolderRepository.existsByIdAndUserId(normalizedTargetFolderId, userId)) {
            throw new BizException(ErrorCode.MEDIA_FOLDER_NOT_FOUND);
        }

        String taskId = MediaTranscodeConstant.TASK_ID_PREFIX + UUID.randomUUID();

        String title = String.format("素材转码：%s", StringUtils.hasText(asset.getTitle()) ? asset.getTitle() : asset.getId());
        String summary = MediaTranscodeConstant.SummaryText.QUEUED;

        Map<String, Object> payload = new HashMap<>();
        payload.put(MediaTranscodeConstant.PayloadKey.TASK_TYPE, MediaTranscodeConstant.TASK_TYPE);
        payload.put(MediaTranscodeConstant.PayloadKey.TASK_ID, taskId);
        payload.put(MediaTranscodeConstant.PayloadKey.ATTEMPT, 1);
        payload.put(MediaTranscodeConstant.PayloadKey.ASSET_ID, asset.getId());
        payload.put(MediaTranscodeConstant.PayloadKey.PRESET_ID, presetId);
        payload.put(MediaTranscodeConstant.PayloadKey.STAGE, MediaTranscodeConstant.Stage.PENDING);
        payload.put(MediaTranscodeConstant.PayloadKey.CREATED_AT, Instant.now().toString());
        if (StringUtils.hasText(normalizedTargetFolderId)) {
            payload.put(MediaTranscodeConstant.PayloadKey.TARGET_FOLDER_ID, normalizedTargetFolderId);
        }

        UUID messageId = messageCenterFacade.createTaskMessage(
            userId,
            MediaTranscodeConstant.MESSAGE_TYPE,
            title,
            summary,
            payload,
            taskId
        );

        enqueue(new TranscodeTaskPendingMessage(
            taskId,
            messageId,
            userId,
            normalizeTierOrNull(tier),
            asset.getId(),
            presetId,
            normalizedTargetFolderId,
            request.getOptions()
        ));

        return TranscodeCreateResponse.builder()
            .taskId(taskId)
            .messageId(messageId)
            .build();
    }

    public TranscodeRetryResponse retryTranscodeTask(UUID userId, String tier, String taskId, TranscodeRetryRequest request) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (!StringUtils.hasText(taskId)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "taskId is required");
        }
        if (request == null || !StringUtils.hasText(request.getAssetId()) || !StringUtils.hasText(request.getPresetId())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "assetId and presetId are required");
        }

        var asset = mediaAssetRepository.findWithFilesByIdAndUserId(request.getAssetId().trim(), userId)
            .orElseThrow(() -> new BizException(ErrorCode.MEDIA_ASSET_NOT_FOUND));

        var original = asset.getOriginalFile();
        String mimeType = original != null ? original.getMimeType() : null;
        if (StorageFileTypeResolver.fromMimeType(mimeType) != StorageFileType.VIDEO) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Only video assets are supported for transcode");
        }

        String presetId = validatePresetId(request.getPresetId());
        String normalizedTargetFolderId = MediaFolderIdUtils.normalizeFolderId(request.getTargetFolderId());
        if (StringUtils.hasText(normalizedTargetFolderId)
            && !mediaFolderRepository.existsByIdAndUserId(normalizedTargetFolderId, userId)) {
            throw new BizException(ErrorCode.MEDIA_FOLDER_NOT_FOUND);
        }

        String title = String.format("素材转码：%s", StringUtils.hasText(asset.getTitle()) ? asset.getTitle() : asset.getId());
        String summary = MediaTranscodeConstant.SummaryText.QUEUED;

        Map<String, Object> payload = new HashMap<>();
        payload.put(MediaTranscodeConstant.PayloadKey.TASK_TYPE, MediaTranscodeConstant.TASK_TYPE);
        payload.put(MediaTranscodeConstant.PayloadKey.TASK_ID, taskId.trim());
        payload.put(MediaTranscodeConstant.PayloadKey.ATTEMPT, 0);
        payload.put(MediaTranscodeConstant.PayloadKey.ASSET_ID, asset.getId());
        payload.put(MediaTranscodeConstant.PayloadKey.PRESET_ID, presetId);
        payload.put(MediaTranscodeConstant.PayloadKey.STAGE, MediaTranscodeConstant.Stage.PENDING);
        payload.put(MediaTranscodeConstant.PayloadKey.CREATED_AT, Instant.now().toString());
        if (StringUtils.hasText(normalizedTargetFolderId)) {
            payload.put(MediaTranscodeConstant.PayloadKey.TARGET_FOLDER_ID, normalizedTargetFolderId);
        }

        UUID messageId = messageCenterFacade.createTaskMessage(
            userId,
            MediaTranscodeConstant.MESSAGE_TYPE,
            title,
            summary,
            payload,
            taskId.trim()
        );

        enqueue(new TranscodeTaskPendingMessage(
            taskId.trim(),
            messageId,
            userId,
            normalizeTierOrNull(tier),
            asset.getId(),
            presetId,
            normalizedTargetFolderId,
            request.getOptions()
        ));

        return TranscodeRetryResponse.builder()
            .taskId(taskId.trim())
            .messageId(messageId)
            .build();
    }

    private void enqueue(TranscodeTaskPendingMessage message) {
        int priority = resolveTaskPriority(message == null ? null : message.tier());
        rabbitTemplate.convertAndSend(
            MessagingConstants.Exchanges.CORE_NOTIFICATIONS,
            MessagingConstants.RoutingKeys.TASK_PENDING,
            message,
            msg -> {
                msg.getMessageProperties().setPriority(priority);
                return msg;
            }
        );
        log.debug("Enqueued transcode task: taskId={}, messageId={}, userId={}",
            message.taskId(), message.messageId(), message.userId());
    }

    private int resolveTaskPriority(String tier) {
        if (!StringUtils.hasText(tier)) {
            return PRIORITY_FREE;
        }
        return TIER_PRO.equalsIgnoreCase(tier.trim()) ? PRIORITY_PRO : PRIORITY_FREE;
    }

    private String normalizeTierOrNull(String tier) {
        if (!StringUtils.hasText(tier)) {
            return null;
        }
        String trimmed = tier.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String validatePresetId(String presetId) {
        if (!StringUtils.hasText(presetId)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "presetId is required");
        }
        String trimmed = presetId.trim();
        if (trimmed.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "presetId is required");
        }

        if (transcodeProperties.getPresets() != null) {
            if (transcodeProperties.getPresets().containsKey(trimmed)) {
                return trimmed;
            }
            for (String key : transcodeProperties.getPresets().keySet()) {
                if (key != null && key.equalsIgnoreCase(trimmed)) {
                    return key;
                }
            }
        }

        throw new BizException(ErrorCode.INVALID_REQUEST, "Unsupported presetId: " + presetId);
    }
}
