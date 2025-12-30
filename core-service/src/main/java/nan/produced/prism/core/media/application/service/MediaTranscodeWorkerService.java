package nan.produced.prism.core.media.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.config.StoragePathProperties;
import nan.produced.prism.core.common.util.FileNameUtils;
import nan.produced.prism.core.common.util.JsonUtils;
import nan.produced.prism.core.media.application.constant.MediaAssetSourceTypeConstant;
import nan.produced.prism.core.media.application.constant.MediaTranscodeConstant;
import nan.produced.prism.core.media.application.domain.FileEntity;
import nan.produced.prism.core.media.application.domain.MediaAssetEntity;
import nan.produced.prism.core.media.application.dto.TranscodeOptions;
import nan.produced.prism.core.media.application.port.outbound.MediaObjectUrlPort;
import nan.produced.prism.core.media.application.port.outbound.ObjectStoragePort;
import nan.produced.prism.core.media.application.repository.FileEntityRepository;
import nan.produced.prism.core.media.application.repository.MediaAssetRepository;
import nan.produced.prism.core.media.application.repository.MediaFolderRepository;
import nan.produced.prism.core.media.application.util.MediaFolderIdUtils;
import nan.produced.prism.core.media.application.util.MediaLibraryObjectKeyUtils;
import nan.produced.prism.core.media.infrastructure.config.S3Properties;
import nan.produced.prism.core.media.infrastructure.config.TranscodeProperties;
import nan.produced.prism.core.media.infrastructure.config.UploadRouteProperties;
import nan.produced.prism.core.media.infrastructure.messaging.TranscodeTaskPendingMessage;
import nan.produced.prism.core.message.api.MessageCenterConstants;
import nan.produced.prism.core.message.api.MessageCenterFacade;
import nan.produced.prism.core.system.api.SubscriptionQuotaFacade;
import nan.produced.prism.core.user.api.StorageFileType;
import nan.produced.prism.core.user.api.StorageFileTypeResolver;
import nan.produced.prism.core.user.api.StorageSourceType;
import nan.produced.prism.core.user.api.UserStorageUsageFacade;
import nan.produced.prism.core.user.api.UserStorageUsageQueryFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaTranscodeWorkerService {

    private static final long MIN_PROGRESS_PUBLISH_INTERVAL_MS = 200;

    private static final double TRANSCODE_PROGRESS_CAP = 0.999;

    private static final String FFMPEG_PROGRESS_OUT_TIME_MS = "out_time_ms";
    private static final String FFMPEG_PROGRESS_SPEED = "speed";
    private static final String FFMPEG_PROGRESS_PROGRESS = "progress";
    private static final String FFMPEG_PROGRESS_END = "end";

    private final TranscodeProperties transcodeProperties;
    private final UploadRouteProperties uploadRouteProperties;
    private final StoragePathProperties storagePathProperties;
    private final MediaObjectUrlPort mediaObjectUrlPort;
    private final ObjectStoragePort objectStoragePort;
    private final S3Client s3Client;
    private final S3Properties s3Properties;

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaFolderRepository mediaFolderRepository;
    private final FileEntityRepository fileEntityRepository;
    private final UserStorageUsageFacade userStorageUsageFacade;
    private final UserStorageUsageQueryFacade userStorageUsageQueryFacade;
    private final SubscriptionQuotaFacade subscriptionQuotaFacade;
    private final MessageCenterFacade messageCenterFacade;

    public void handle(TranscodeTaskPendingMessage message) {
        if (message == null || message.userId() == null || message.messageId() == null) {
            return;
        }
        if (!StringUtils.hasText(message.taskId()) || !StringUtils.hasText(message.assetId()) || !StringUtils.hasText(message.presetId())) {
            fail(message, null, null, "任务参数不完整", null);
            return;
        }

        try {
            doHandle(message);
        } catch (Exception ex) {
            log.error("Transcode task failed: taskId={}, messageId={}, userId={}", message.taskId(), message.messageId(), message.userId(), ex);
        }
    }

    private void doHandle(TranscodeTaskPendingMessage message) {
        UUID userId = message.userId();
        UUID messageId = message.messageId();

        Path taskDir = null;
        String stage = MediaTranscodeConstant.Stage.PENDING;
        Map<String, Object> payload = null;
        boolean success = false;

        try {
            var asset = mediaAssetRepository.findWithFilesByIdAndUserId(message.assetId().trim(), userId)
                .orElseThrow(() -> new IllegalArgumentException("source asset not found"));

            var original = asset.getOriginalFile();
            if (original == null || !StringUtils.hasText(original.getS3Key())) {
                throw new IllegalArgumentException("source file missing");
            }

            String mimeType = original.getMimeType();
            if (StorageFileTypeResolver.fromMimeType(mimeType) != StorageFileType.VIDEO) {
                throw new IllegalArgumentException("Only video assets are supported for transcode");
            }

            TranscodePreset preset = resolvePreset(message.presetId(), message.options());

            String targetFolderId = MediaFolderIdUtils.normalizeFolderId(message.targetFolderId());
            if (targetFolderId == null) {
                targetFolderId = MediaFolderIdUtils.normalizeFolderId(asset.getFolderId());
            }
            if (StringUtils.hasText(targetFolderId) && !mediaFolderRepository.existsByIdAndUserId(targetFolderId, userId)) {
                throw new IllegalArgumentException("target folder not found");
            }

            taskDir = prepareTaskDir(message.taskId());
            String sourceExt = FileNameUtils.resolveExtensionOrDefault(original.getS3Key(), mimeType, MediaTranscodeConstant.DEFAULT_SOURCE_EXT);
            Path sourcePath = taskDir.resolve(MediaTranscodeConstant.TEMP_SOURCE_FILENAME_PREFIX + sourceExt);
            Path outputPath = taskDir.resolve(MediaTranscodeConstant.TEMP_OUTPUT_FILENAME_PREFIX + preset.outputExt());

            payload = basePayload(message, asset, original, preset, targetFolderId);

            stage = MediaTranscodeConstant.Stage.DOWNLOADING;
            update(userId, messageId, MessageCenterConstants.TASK_STATUS_RUNNING, patch(payload, stage, null, null));
            download(mediaObjectUrlPort.toPublicUrl(original.getS3Key()), sourcePath);

            Long durationMs = original.getDurationMs();
            if (durationMs == null || durationMs <= 0) {
                durationMs = probe(sourcePath).durationMs();
            }

            stage = MediaTranscodeConstant.Stage.TRANSCODING;
            update(userId, messageId, MessageCenterConstants.TASK_STATUS_RUNNING, patch(payload, stage, 0.0, null));
            TranscodeProgress progress = runFfmpegWithProgress(message, payload, sourcePath, outputPath, durationMs, preset);

            long outputSize = Files.size(outputPath);
            ensureQuota(message, outputSize);

            stage = MediaTranscodeConstant.Stage.UPLOADING;
            update(userId, messageId, MessageCenterConstants.TASK_STATUS_RUNNING, patch(payload, stage, progress.percent(), progress));
            String outputMd5Lower = md5HexLower(outputPath);
            var routeConfig = uploadRouteProperties.getRouteConfig(MediaTranscodeConstant.UPLOAD_ROUTE_MEDIA_LIBRARY);
            String outputKey = MediaLibraryObjectKeyUtils.buildMediaLibraryFilesObjectKey(
                routeConfig,
                storagePathProperties.getMediaLibrary().getFilesDir(),
                outputMd5Lower,
                outputSize,
                preset.outputExt()
            );
            putObject(outputKey, preset.outputContentType(), outputPath);

            stage = MediaTranscodeConstant.Stage.FINALIZING;
            update(userId, messageId, MessageCenterConstants.TASK_STATUS_RUNNING, patch(payload, stage, 0.99, progress));
            PersistedResult result = persistOutput(userId, asset, targetFolderId, message.taskId(), preset, outputKey, outputMd5Lower, outputSize, outputPath);

            Map<String, Object> done = new HashMap<>(payload);
            done.put(MediaTranscodeConstant.PayloadKey.STAGE, MediaTranscodeConstant.Stage.FINALIZING);
            done.put(MediaTranscodeConstant.PayloadKey.PROGRESS, Map.of(MediaTranscodeConstant.ProgressKey.PERCENT, 1.0));
            done.put(MediaTranscodeConstant.PayloadKey.OUTPUT, Map.of(
                MediaTranscodeConstant.OutputKey.ASSET_ID, result.assetId(),
                MediaTranscodeConstant.OutputKey.URL, mediaObjectUrlPort.toPublicUrl(outputKey)
            ));

            update(userId, messageId, MessageCenterConstants.TASK_STATUS_SUCCESS, done);
            success = true;
        } catch (Exception ex) {
            log.error("Transcode task execution failed: taskId={}, messageId={}, userId={}", message.taskId(), message.messageId(), message.userId(), ex);
            fail(message, payload, stage, safeMessage(ex), ex);
        } finally {
            cleanup(taskDir, !success);
        }
    }

    private record TranscodePreset(String presetId,
                                  String label,
                                  Integer width,
                                  Integer height,
                                  Integer crf,
                                  String videoCodec,
                                  String encoderPreset,
                                  Integer videoBitrateKbps,
                                  String audioCodec,
                                  Integer audioBitrateKbps,
                                  boolean faststart,
                                  String outputExt,
                                  String outputContentType) {
    }

    private TranscodePreset resolvePreset(String presetId, TranscodeOptions options) {
        if (!StringUtils.hasText(presetId)) {
            throw new IllegalArgumentException("presetId is blank");
        }

        String requested = presetId.trim();
        TranscodeProperties.Preset preset = transcodeProperties.resolvePreset(requested);
        if (preset == null) {
            throw new IllegalArgumentException("Unsupported presetId: " + presetId);
        }

        Integer width = preset.getWidth();
        Integer height = preset.getHeight();
        Integer crf = preset.getCrf();
        Integer videoBitrateKbps = preset.getVideoBitrateKbps();
        Integer audioBitrateKbps = preset.getAudioBitrateKbps();
        boolean faststart = preset.isFaststart();

        if (options != null) {
            if (options.getWidth() != null || options.getHeight() != null) {
                if (options.getWidth() == null || options.getHeight() == null) {
                    throw new IllegalArgumentException("width and height must be provided together");
                }
                width = options.getWidth();
                height = options.getHeight();
            }
            if (options.getCrf() != null) {
                crf = options.getCrf();
            }
            if (options.getVideoBitrateKbps() != null) {
                videoBitrateKbps = options.getVideoBitrateKbps();
            }
            if (options.getAudioBitrateKbps() != null) {
                audioBitrateKbps = options.getAudioBitrateKbps();
            }
            if (options.getFaststart() != null) {
                faststart = options.getFaststart();
            }
        }

        String outputExt = StringUtils.hasText(preset.getOutputExt()) ? preset.getOutputExt().trim() : "mp4";
        String outputContentType = StringUtils.hasText(preset.getOutputContentType()) ? preset.getOutputContentType().trim() : "video/mp4";

        String label = StringUtils.hasText(preset.getLabel()) ? preset.getLabel().trim() : null;
        String videoCodec = StringUtils.hasText(preset.getVideoCodec()) ? preset.getVideoCodec().trim() : "libx264";
        String encoderPreset = StringUtils.hasText(preset.getEncoderPreset()) ? preset.getEncoderPreset().trim() : "medium";
        String audioCodec = StringUtils.hasText(preset.getAudioCodec()) ? preset.getAudioCodec().trim() : "aac";

        if (audioBitrateKbps == null || audioBitrateKbps <= 0) {
            audioBitrateKbps = 128;
        }

        return new TranscodePreset(
            requested,
            label,
            width,
            height,
            crf,
            videoCodec,
            encoderPreset,
            videoBitrateKbps,
            audioCodec,
            audioBitrateKbps,
            faststart,
            outputExt,
            outputContentType
        );
    }

    private void download(String url, Path dest) throws IOException, InterruptedException {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("source url is blank");
        }

        HttpClient.Builder builder = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(Math.max(1, transcodeProperties.getDownloadConnectTimeoutSeconds())));

        HttpClient client = builder.build();

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET();

        int timeoutSeconds = transcodeProperties.getDownloadTimeoutSeconds();
        if (timeoutSeconds > 0) {
            reqBuilder.timeout(Duration.ofSeconds(timeoutSeconds));
        }

        HttpResponse<java.io.InputStream> resp = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("download failed, status=" + resp.statusCode());
        }

        Files.createDirectories(dest.getParent());
        try (var in = resp.body()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private TranscodeProgress runFfmpegWithProgress(TranscodeTaskPendingMessage message,
                                                   Map<String, Object> basePayload,
                                                   Path input,
                                                   Path output,
                                                   Long durationMs,
                                                   TranscodePreset preset) throws Exception {
        long duration = durationMs != null && durationMs > 0 ? durationMs : 0L;

        var args = new java.util.ArrayList<String>();
        args.add(transcodeProperties.getFfmpeg());
        args.add("-y");
        args.add("-i");
        args.add(input.toAbsolutePath().toString());
        args.add("-map_metadata");
        args.add("-1");
        args.add("-c:v");
        args.add(preset.videoCodec());
        args.add("-preset");
        args.add(preset.encoderPreset());
        if (preset.videoBitrateKbps() != null && preset.videoBitrateKbps() > 0) {
            args.add("-b:v");
            args.add(preset.videoBitrateKbps() + "k");
        } else if (preset.crf() != null) {
            args.add("-crf");
            args.add(String.valueOf(preset.crf()));
        }
        if (preset.width() != null && preset.height() != null) {
            args.add("-vf");
            args.add(String.format("scale=%d:%d:force_original_aspect_ratio=decrease", preset.width(), preset.height()));
        }
        args.add("-c:a");
        args.add(preset.audioCodec());
        if (preset.audioBitrateKbps() != null && preset.audioBitrateKbps() > 0) {
            args.add("-b:a");
            args.add(preset.audioBitrateKbps() + "k");
        }
        if (preset.faststart() && "mp4".equalsIgnoreCase(preset.outputExt())) {
            args.add("-movflags");
            args.add("+faststart");
        }
        args.add("-progress");
        args.add("pipe:1");
        args.add("-nostats");
        args.add(output.toAbsolutePath().toString());

        Process process = new ProcessBuilder(args).redirectErrorStream(false).start();

        Deque<String> stderrTail = new ArrayDeque<>();
        Thread stderrThread = Thread.startVirtualThread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    synchronized (stderrTail) {
                        stderrTail.addLast(line);
                        while (stderrTail.size() > Math.max(1, transcodeProperties.getStderrTailLines())) {
                            stderrTail.removeFirst();
                        }
                    }
                }
            } catch (IOException ignore) {
            }
        });

        long lastPublishAt = 0L;
        int lastPercentInt = -1;
        long outTimeMs = 0L;
        String speed = null;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                int idx = line.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String key = line.substring(0, idx);
                String value = idx < line.length() - 1 ? line.substring(idx + 1) : "";

                if (FFMPEG_PROGRESS_OUT_TIME_MS.equals(key)) {
                    try {
                        outTimeMs = Long.parseLong(value.trim());
                    } catch (Exception ignore) {
                    }
                } else if (FFMPEG_PROGRESS_SPEED.equals(key)) {
                    speed = StringUtils.hasText(value) ? value.trim() : null;
                } else if (FFMPEG_PROGRESS_PROGRESS.equals(key) && FFMPEG_PROGRESS_END.equalsIgnoreCase(value.trim())) {
                    break;
                }

                if (duration > 0 && outTimeMs > 0) {
                    double percent = Math.min(TRANSCODE_PROGRESS_CAP, (double) outTimeMs / (double) duration);
                    int percentInt = (int) Math.floor(percent * 100);

                    long now = System.currentTimeMillis();
                    boolean shouldPublish = (now - lastPublishAt) >= Math.max(MIN_PROGRESS_PUBLISH_INTERVAL_MS, transcodeProperties.getProgressPublishIntervalMs())
                        && percentInt != lastPercentInt;

                    if (shouldPublish) {
                        lastPublishAt = now;
                        lastPercentInt = percentInt;

                        TranscodeProgress p = new TranscodeProgress(percent, outTimeMs, duration, speed);
                        Map<String, Object> payload = patch(basePayload, MediaTranscodeConstant.Stage.TRANSCODING, percent, p);
                        messageCenterFacade.updateTaskMessage(
                            message.userId(),
                            message.messageId(),
                            MessageCenterConstants.TASK_STATUS_RUNNING,
                            payload
                        );
                    }
                }
            }
        }

        boolean finished = process.waitFor(transcodeProperties.getMaxTaskSeconds(), java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("ffmpeg timeout");
        }

        int exit = process.exitValue();
        stderrThread.join(Duration.ofSeconds(5));

        if (exit != 0) {
            String logTail = joinTail(stderrTail);
            throw new FfmpegFailedException(exit, logTail);
        }

        double finalPercent = duration > 0 ? TRANSCODE_PROGRESS_CAP : 0.0;
        return new TranscodeProgress(finalPercent, outTimeMs, duration, speed);
    }

    private record TranscodeProgress(double percent, long outTimeMs, long durationMs, String speed) {
    }

    private static class FfmpegFailedException extends RuntimeException {
        private final int exitCode;
        private final String logTail;

        private FfmpegFailedException(int exitCode, String logTail) {
            super("ffmpeg failed, exitCode=" + exitCode);
            this.exitCode = exitCode;
            this.logTail = logTail;
        }
    }

    private record ProbeResult(Long durationMs, Integer width, Integer height) {
    }

    private ProbeResult probe(Path file) {
        if (file == null || !Files.exists(file)) {
            return new ProbeResult(null, null, null);
        }

        var args = java.util.List.of(
            transcodeProperties.getFfprobe(),
            "-v", "error",
            "-print_format", "json",
            "-show_entries", "format=duration:stream=width,height",
            "-select_streams", "v:0",
            file.toAbsolutePath().toString()
        );

        try {
            Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
            String json;
            try (var in = process.getInputStream()) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

            if (!StringUtils.hasText(json)) {
                return new ProbeResult(null, null, null);
            }
            JsonNode node = JsonUtils.fromJson(json);
            Long durationMs = null;
            Integer width = null;
            Integer height = null;

            JsonNode format = node.get("format");
            if (format != null) {
                String durationSec = format.path("duration").asText(null);
                durationMs = parseDurationMs(durationSec);
            }

            JsonNode streams = node.get("streams");
            if (streams != null && streams.isArray() && streams.size() > 0) {
                JsonNode s0 = streams.get(0);
                width = s0.path("width").isNumber() ? s0.path("width").asInt() : null;
                height = s0.path("height").isNumber() ? s0.path("height").asInt() : null;
            }

            return new ProbeResult(durationMs, width, height);
        } catch (Exception ex) {
            log.debug("ffprobe failed: {}", ex.getMessage());
            return new ProbeResult(null, null, null);
        }
    }

    private Long parseDurationMs(String seconds) {
        if (!StringUtils.hasText(seconds)) {
            return null;
        }
        try {
            double sec = Double.parseDouble(seconds.trim());
            if (sec <= 0) {
                return null;
            }
            return (long) Math.floor(sec * 1000.0);
        } catch (Exception ignore) {
            return null;
        }
    }

    private void ensureQuota(TranscodeTaskPendingMessage message, long outputBytes) {
        long quotaBytes = Optional.ofNullable(subscriptionQuotaFacade.getQuota(message.tier()).storageLimitBytes()).orElse(0L);
        if (quotaBytes <= 0) {
            return;
        }
        var usage = userStorageUsageQueryFacade.getUsage(message.userId(), StorageSourceType.MEDIA_LIBRARY);
        long usedBytes = usage != null ? usage.totalBytes() : 0L;
        if (usedBytes + outputBytes > quotaBytes) {
            throw new StorageQuotaExceededException();
        }
    }

    private static class StorageQuotaExceededException extends RuntimeException {
        private StorageQuotaExceededException() {
            super("storage quota exceeded");
        }
    }

    private void putObject(String objectKey, String contentType, Path file) {
        if (!StringUtils.hasText(s3Properties.getBucket())) {
            throw new IllegalStateException("S3 bucket not configured");
        }
        if (!StringUtils.hasText(objectKey) || file == null || !Files.exists(file)) {
            throw new IllegalArgumentException("invalid upload input");
        }

        boolean exists = objectStoragePort.objectExists(objectKey);
        if (exists) {
            return;
        }

        var request = PutObjectRequest.builder()
            .bucket(s3Properties.getBucket())
            .key(objectKey)
            .contentType(contentType)
            .build();
        s3Client.putObject(request, RequestBody.fromFile(file));
    }

    @Transactional
    protected PersistedResult persistOutput(UUID userId,
                                           MediaAssetEntity sourceAsset,
                                           String targetFolderId,
                                           String taskId,
                                           TranscodePreset preset,
                                           String outputKey,
                                           String outputMd5Lower,
                                           long outputSize,
                                           Path outputPath) {
        if (userId == null || sourceAsset == null) {
            throw new IllegalArgumentException("invalid persist input");
        }

        FileEntity outputFile = fileEntityRepository.findByMd5(outputMd5Lower).orElse(null);
        boolean createdFileEntity = false;
        if (outputFile == null) {
            var now = Instant.now();
            ProbeResult probe = probe(outputPath);

            outputFile = new FileEntity();
            outputFile.setFileId(UUID.randomUUID().toString());
            outputFile.setMd5(outputMd5Lower);
            outputFile.setS3Key(outputKey);
            outputFile.setSize(outputSize);
            outputFile.setMimeType(preset.outputContentType());
            outputFile.setRefCount(1);
            outputFile.setWidth(probe.width());
            outputFile.setHeight(probe.height());
            outputFile.setDurationMs(probe.durationMs());
            outputFile.setCreatedAt(now);

            fileEntityRepository.save(outputFile);
            createdFileEntity = true;
        } else {
            fileEntityRepository.incrementRefCount(outputFile.getFileId(), 1);
        }

        FileEntity coverFile = sourceAsset.getCoverFile();
        if (coverFile != null) {
            fileEntityRepository.incrementRefCount(coverFile.getFileId(), 1);
        }

        var now = Instant.now();
        String groupId = UUID.randomUUID().toString();
        String title = buildOutputTitle(sourceAsset.getTitle(), preset);

        var asset = new MediaAssetEntity();
        asset.setId(UUID.randomUUID().toString());
        asset.setUserId(userId);
        asset.setTitle(title);
        asset.setDescription(null);
        asset.setFolderId(StringUtils.hasText(targetFolderId) ? targetFolderId : null);
        asset.setGroupId(groupId);
        asset.setOriginalFile(outputFile);
        asset.setCoverFile(coverFile);
        asset.setSourceType(MediaAssetSourceTypeConstant.TRANSCODE);
        asset.setSourceTaskId(taskId);
        asset.setMetadata(null);
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);

        mediaAssetRepository.save(asset);

        if (createdFileEntity) {
            var fileType = StorageFileTypeResolver.fromMimeType(preset.outputContentType());
            userStorageUsageFacade.incrementUsage(userId, StorageSourceType.MEDIA_LIBRARY, fileType, 1, outputSize);
        }

        return new PersistedResult(asset.getId(), outputFile.getFileId());
    }

    private record PersistedResult(String assetId, String fileId) {
    }

    private Map<String, Object> basePayload(TranscodeTaskPendingMessage message,
                                           MediaAssetEntity asset,
                                           FileEntity original,
                                           TranscodePreset preset,
                                           String targetFolderId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(MediaTranscodeConstant.PayloadKey.TASK_TYPE, MediaTranscodeConstant.TASK_TYPE);
        payload.put(MediaTranscodeConstant.PayloadKey.TASK_ID, message.taskId());
        payload.put(MediaTranscodeConstant.PayloadKey.ATTEMPT, 1);

        Map<String, Object> presetObj = new HashMap<>();
        presetObj.put(MediaTranscodeConstant.PresetKey.PRESET_ID, preset.presetId());
        if (StringUtils.hasText(preset.label())) {
            presetObj.put(MediaTranscodeConstant.PresetKey.LABEL, preset.label());
        }
        if (preset.width() != null) {
            presetObj.put(MediaTranscodeConstant.PresetKey.WIDTH, preset.width());
        }
        if (preset.height() != null) {
            presetObj.put(MediaTranscodeConstant.PresetKey.HEIGHT, preset.height());
        }
        if (preset.crf() != null) {
            presetObj.put(MediaTranscodeConstant.PresetKey.CRF, preset.crf());
        }
        presetObj.put(MediaTranscodeConstant.PresetKey.OUTPUT_EXT, preset.outputExt());
        presetObj.put(MediaTranscodeConstant.PresetKey.OUTPUT_CONTENT_TYPE, preset.outputContentType());
        if (StringUtils.hasText(preset.videoCodec())) {
            presetObj.put(MediaTranscodeConstant.PresetKey.VIDEO_CODEC, preset.videoCodec());
        }
        if (StringUtils.hasText(preset.encoderPreset())) {
            presetObj.put(MediaTranscodeConstant.PresetKey.ENCODER_PRESET, preset.encoderPreset());
        }
        if (preset.videoBitrateKbps() != null) {
            presetObj.put(MediaTranscodeConstant.PresetKey.VIDEO_BITRATE_KBPS, preset.videoBitrateKbps());
        }
        if (StringUtils.hasText(preset.audioCodec())) {
            presetObj.put(MediaTranscodeConstant.PresetKey.AUDIO_CODEC, preset.audioCodec());
        }
        if (preset.audioBitrateKbps() != null) {
            presetObj.put(MediaTranscodeConstant.PresetKey.AUDIO_BITRATE_KBPS, preset.audioBitrateKbps());
        }
        presetObj.put(MediaTranscodeConstant.PresetKey.FASTSTART, preset.faststart());
        payload.put(MediaTranscodeConstant.PayloadKey.PRESET, presetObj);

        Map<String, Object> sourceObj = new HashMap<>();
        sourceObj.put(MediaTranscodeConstant.SourceKey.ASSET_ID, asset.getId());
        if (StringUtils.hasText(asset.getTitle())) {
            sourceObj.put(MediaTranscodeConstant.SourceKey.TITLE, asset.getTitle());
        }
        sourceObj.put(MediaTranscodeConstant.SourceKey.MIME_TYPE, original.getMimeType());
        sourceObj.put(MediaTranscodeConstant.SourceKey.SIZE_BYTES, original.getSize());
        if (original.getDurationMs() != null) {
            sourceObj.put(MediaTranscodeConstant.SourceKey.DURATION_MS, original.getDurationMs());
        }
        payload.put(MediaTranscodeConstant.PayloadKey.SOURCE, sourceObj);

        if (StringUtils.hasText(targetFolderId)) {
            payload.put(MediaTranscodeConstant.PayloadKey.TARGET_FOLDER_ID, targetFolderId);
        }
        return payload;
    }

    private Map<String, Object> patch(Map<String, Object> base, String stage, Double percent, TranscodeProgress progress) {
        Map<String, Object> patched = new HashMap<>(base);
        patched.put(MediaTranscodeConstant.PayloadKey.STAGE, stage);
        if (percent != null) {
            Map<String, Object> progressObj = new HashMap<>();
            progressObj.put(MediaTranscodeConstant.ProgressKey.PERCENT, percent);
            if (progress != null) {
                progressObj.put(MediaTranscodeConstant.ProgressKey.OUT_TIME_MS, progress.outTimeMs());
                progressObj.put(MediaTranscodeConstant.ProgressKey.DURATION_MS, progress.durationMs());
                if (StringUtils.hasText(progress.speed())) {
                    progressObj.put(MediaTranscodeConstant.ProgressKey.SPEED, progress.speed());
                }
            }
            patched.put(MediaTranscodeConstant.PayloadKey.PROGRESS, progressObj);
        }
        return patched;
    }

    private void update(UUID userId, UUID messageId, String status, Map<String, Object> payload) {
        messageCenterFacade.updateTaskMessage(userId, messageId, status, payload);
    }

    private void fail(TranscodeTaskPendingMessage message, String error) {
        fail(message, null, null, error, null);
    }

    private void fail(TranscodeTaskPendingMessage message,
                      Map<String, Object> basePayload,
                      String stage,
                      String error,
                      Exception ex) {
        Map<String, Object> payload = basePayload != null ? new HashMap<>(basePayload) : new HashMap<>();
        payload.put(MediaTranscodeConstant.PayloadKey.TASK_TYPE, MediaTranscodeConstant.TASK_TYPE);
        payload.put(MediaTranscodeConstant.PayloadKey.TASK_ID, message.taskId());
        payload.put(MediaTranscodeConstant.PayloadKey.STAGE, MediaTranscodeConstant.Stage.FAILED);

        Map<String, Object> errorObj = new HashMap<>();
        errorObj.put(MediaTranscodeConstant.ErrorKey.CODE, errorCode(ex));
        errorObj.put(MediaTranscodeConstant.ErrorKey.MESSAGE, error);
        if (StringUtils.hasText(stage)) {
            errorObj.put(MediaTranscodeConstant.ErrorKey.STAGE, stage);
        }

        if (ex instanceof FfmpegFailedException ff) {
            errorObj.put(MediaTranscodeConstant.ErrorKey.FFMPEG_EXIT_CODE, ff.exitCode);
            if (StringUtils.hasText(ff.logTail)) {
                errorObj.put(MediaTranscodeConstant.ErrorKey.LOG_TAIL, ff.logTail);
            }
        }

        payload.put(MediaTranscodeConstant.PayloadKey.ERROR, errorObj);
        messageCenterFacade.updateTaskMessage(message.userId(), message.messageId(), MessageCenterConstants.TASK_STATUS_FAILED, payload);
    }

    private String errorCode(Exception ex) {
        if (ex instanceof FfmpegFailedException) {
            return "FFMPEG_FAILED";
        }
        if (ex instanceof StorageQuotaExceededException) {
            return "STORAGE_QUOTA_EXCEEDED";
        }
        if (ex instanceof IllegalArgumentException) {
            return "INVALID_ARGUMENT";
        }
        if (ex instanceof IOException) {
            return "IO_ERROR";
        }
        return "INTERNAL_ERROR";
    }

    private String safeMessage(Exception ex) {
        if (ex instanceof FfmpegFailedException ff) {
            return "ffmpeg failed (exitCode=" + ff.exitCode + ")";
        }
        if (ex instanceof StorageQuotaExceededException) {
            return "storage quota exceeded";
        }
        return ex.getMessage() != null ? ex.getMessage() : "transcode failed";
    }

    private Path prepareTaskDir(String taskId) throws IOException {
        String base = transcodeProperties.getTempDir();
        if (!StringUtils.hasText(base)) {
            throw new IllegalStateException("transcode tempDir is blank");
        }
        Path root = Path.of(base).toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path dir = root.resolve(taskId);
        Files.createDirectories(dir);
        return dir;
    }

    private void cleanup(Path dir, boolean failed) {
        if (dir == null) {
            return;
        }
        boolean keep = failed && transcodeProperties.isKeepTempOnFailure();
        if (keep) {
            return;
        }
        try {
            if (!Files.exists(dir)) {
                return;
            }
            try (var stream = Files.walk(dir)) {
                stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignore) {
                        }
                    });
            }
        } catch (Exception ignore) {
        }
    }

    private String md5HexLower(Path file) throws Exception {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        try (var in = Files.newInputStream(file)) {
            byte[] buf = new byte[1024 * 1024];
            int read;
            while ((read = in.read(buf)) > 0) {
                md5.update(buf, 0, read);
            }
        }
        byte[] digest = md5.digest();
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String buildOutputTitle(String sourceTitle, TranscodePreset preset) {
        String base = StringUtils.hasText(sourceTitle) ? sourceTitle.trim() : "转码素材";
        String suffix = preset != null && StringUtils.hasText(preset.label()) ? preset.label().trim() : "transcoded";
        return base + " (" + suffix + ")";
    }

    private String joinTail(Deque<String> tail) {
        if (tail == null || tail.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        synchronized (tail) {
            for (String line : tail) {
                if (line == null) {
                    continue;
                }
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
