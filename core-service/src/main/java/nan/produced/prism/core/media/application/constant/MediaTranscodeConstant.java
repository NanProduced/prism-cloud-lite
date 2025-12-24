package nan.produced.prism.core.media.application.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MediaTranscodeConstant {

    public static final String MESSAGE_TYPE = "media.transcode";

    public static final String TASK_TYPE = "MEDIA_TRANSCODE";

    public static final String TASK_ID_PREFIX = "transcode_";

    public static final String UPLOAD_ROUTE_MEDIA_LIBRARY = "mediaLibrary";

    public static final String TEMP_SOURCE_FILENAME_PREFIX = "source.";

    public static final String TEMP_OUTPUT_FILENAME_PREFIX = "output.";

    public static final String DEFAULT_SOURCE_EXT = "bin";

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Stage {

        public static final String PENDING = "PENDING";

        public static final String DOWNLOADING = "DOWNLOADING";

        public static final String TRANSCODING = "TRANSCODING";

        public static final String UPLOADING = "UPLOADING";

        public static final String FINALIZING = "FINALIZING";

        public static final String FAILED = "FAILED";
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class PayloadKey {

        public static final String TASK_TYPE = "taskType";

        public static final String TASK_ID = "taskId";

        public static final String ATTEMPT = "attempt";

        public static final String ASSET_ID = "assetId";

        public static final String PRESET_ID = "presetId";

        public static final String STAGE = "stage";

        public static final String CREATED_AT = "createdAt";

        public static final String TARGET_FOLDER_ID = "targetFolderId";

        public static final String SOURCE = "source";

        public static final String PRESET = "preset";

        public static final String PROGRESS = "progress";

        public static final String OUTPUT = "output";

        public static final String ERROR = "error";
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ProgressKey {

        public static final String PERCENT = "percent";

        public static final String OUT_TIME_MS = "outTimeMs";

        public static final String DURATION_MS = "durationMs";

        public static final String SPEED = "speed";
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class OutputKey {

        public static final String ASSET_ID = "assetId";

        public static final String FILE_ID = "fileId";

        public static final String S3_KEY = "s3Key";

        public static final String URL = "url";
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class PresetKey {

        public static final String PRESET_ID = "presetId";

        public static final String LABEL = "label";

        public static final String WIDTH = "width";

        public static final String HEIGHT = "height";

        public static final String CRF = "crf";

        public static final String OUTPUT_EXT = "outputExt";

        public static final String OUTPUT_CONTENT_TYPE = "outputContentType";

        public static final String VIDEO_CODEC = "videoCodec";

        public static final String ENCODER_PRESET = "encoderPreset";

        public static final String VIDEO_BITRATE_KBPS = "videoBitrateKbps";

        public static final String AUDIO_CODEC = "audioCodec";

        public static final String AUDIO_BITRATE_KBPS = "audioBitrateKbps";

        public static final String FASTSTART = "faststart";
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class SourceKey {

        public static final String ASSET_ID = "assetId";

        public static final String TITLE = "title";

        public static final String FILE_ID = "fileId";

        public static final String S3_KEY = "s3Key";

        public static final String MIME_TYPE = "mimeType";

        public static final String SIZE_BYTES = "sizeBytes";

        public static final String DURATION_MS = "durationMs";
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ErrorKey {

        public static final String MESSAGE = "message";

        public static final String STAGE = "stage";

        public static final String FFMPEG_EXIT_CODE = "ffmpegExitCode";

        public static final String LOG_TAIL = "logTail";
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class SummaryText {

        public static final String QUEUED = "排队中";

        public static final String DOWNLOADING = "下载中…";

        public static final String TRANSCODING = "转码中…";

        public static final String UPLOADING = "上传中…";

        public static final String FINALIZING = "写入素材库…";

        public static final String SUCCESS = "转码完成";
    }
}
