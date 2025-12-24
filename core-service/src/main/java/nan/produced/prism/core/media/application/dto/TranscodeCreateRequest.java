package nan.produced.prism.core.media.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import lombok.Data;

/**
 * 创建素材转码任务请求
 */
@Data
public class TranscodeCreateRequest {

    /**
     * 预设ID（例如：mp4_720p_h264）
     */
    @NotBlank(message = "presetId is required")
    private String presetId;

    /**
     * 目标文件夹ID（null = 跟随源素材；空字符串视为 null）
     */
    private String targetFolderId;

    /**
     * 参数覆盖（在 preset 基础上覆盖，安全字段白名单）
     */
    @Valid
    private TranscodeOptions options;
}
