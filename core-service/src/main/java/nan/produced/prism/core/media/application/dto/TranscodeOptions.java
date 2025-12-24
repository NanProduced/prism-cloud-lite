package nan.produced.prism.core.media.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 转码参数覆盖（在 preset 基础上进行安全覆盖）。
 * <p>
 * 注意：不允许直接传入任意 ffmpeg 参数字符串，避免命令注入与不可控资源消耗。
 */
@Data
@Schema(name = "TranscodeOptions", description = "转码参数覆盖（安全白名单字段）")
public class TranscodeOptions {

    /**
     * 目标最大宽度（像素，保持等比缩放；需与 height 同时提供）
     */
    @Min(value = 16, message = "width must be >= 16")
    @Max(value = 8192, message = "width must be <= 8192")
    @Schema(description = "目标最大宽度（像素，保持等比缩放；需与 height 同时提供）", example = "1280")
    private Integer width;

    /**
     * 目标最大高度（像素，保持等比缩放；需与 width 同时提供）
     */
    @Min(value = 16, message = "height must be >= 16")
    @Max(value = 8192, message = "height must be <= 8192")
    @Schema(description = "目标最大高度（像素，保持等比缩放；需与 width 同时提供）", example = "720")
    private Integer height;

    /**
     * CRF（0~51，越小越清晰；常用 18~28）
     */
    @Min(value = 0, message = "crf must be >= 0")
    @Max(value = 51, message = "crf must be <= 51")
    @Schema(description = "CRF（0~51，越小越清晰；常用 18~28）", example = "23")
    private Integer crf;

    /**
     * 视频码率（kbps）。若提供，则优先于 crf 生效
     */
    @Min(value = 1, message = "videoBitrateKbps must be positive")
    @Max(value = 50000, message = "videoBitrateKbps must be <= 50000")
    @Schema(description = "视频码率（kbps，若提供则优先于 crf 生效）", example = "2500")
    private Integer videoBitrateKbps;

    /**
     * 音频码率（kbps）
     */
    @Min(value = 8, message = "audioBitrateKbps must be >= 8")
    @Max(value = 1000, message = "audioBitrateKbps must be <= 1000")
    @Schema(description = "音频码率（kbps）", example = "128")
    private Integer audioBitrateKbps;

    /**
     * 是否启用 faststart（将 moov atom 前移，便于在线播放）。
     * <p>
     * null 表示沿用 preset 配置
     */
    @Schema(description = "是否启用 faststart（便于在线播放）；null 表示沿用 preset 配置")
    private Boolean faststart;

    @AssertTrue(message = "width and height must be provided together")
    public boolean isScaleValid() {
        return (width == null && height == null) || (width != null && height != null);
    }
}
