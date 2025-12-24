package nan.produced.prism.core.media.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import lombok.Data;

/**
 * 创建素材转码任务请求
 */
@Data
@Schema(name = "TranscodeCreateRequest", description = "创建素材转码任务请求")
public class TranscodeCreateRequest {

    /**
     * 预设ID（例如：mp4_720p_h264）
     */
    @NotBlank(message = "presetId is required")
    @Schema(description = "转码预设ID", example = "mp4_720p_h264", requiredMode = Schema.RequiredMode.REQUIRED)
    private String presetId;

    /**
     * 目标文件夹ID（null = 跟随源素材；空字符串视为 null）
     */
    @Schema(description = "目标文件夹ID（不传/null 表示跟随源素材；空字符串视为 null）")
    private String targetFolderId;

    /**
     * 参数覆盖（在 preset 基础上覆盖，安全字段白名单）
     */
    @Valid
    @Schema(description = "参数覆盖（在 preset 基础上做安全覆盖；白名单字段）")
    private TranscodeOptions options;
}
