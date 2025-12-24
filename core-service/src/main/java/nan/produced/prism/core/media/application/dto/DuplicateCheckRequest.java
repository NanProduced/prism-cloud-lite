package nan.produced.prism.core.media.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

/**
 * MD5 秒传检查请求
 *
 * 注意：请求中必须带 clientId，用于一一映射响应结果，避免批量上传时的歧义。
 */
@Data
@Schema(name = "DuplicateCheckRequest", description = "秒传检查请求（批量检查文件是否已存在）")
public class DuplicateCheckRequest {

    /**
     * 待检查的文件列表
     */
    @NotEmpty(message = "files is required")
    @Valid
    @Schema(description = "待检查的文件列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<FileCheckInfo> files;

    /**
     * 文件检查信息
     */
    @Data
    @Schema(name = "DuplicateCheckFileCheckInfo", description = "单个文件的检查信息")
    public static class FileCheckInfo {

        /**
         * 前端生成的临时ID，用于映射响应
         */
        @NotBlank(message = "clientId is required")
        @Schema(description = "前端生成的 clientId（用于映射响应）", example = "tmp_1", requiredMode = Schema.RequiredMode.REQUIRED)
        private String clientId;

        /**
         * 文件 MD5 哈希（可选，大文件可跳过校验）
         */
        @Schema(description = "文件 MD5（可选；不传则必定 duplicate=false）", example = "d41d8cd98f00b204e9800998ecf8427e")
        private String md5;

        /**
         * 文件大小（字节）
         */
        @NotNull(message = "size is required")
        @Positive(message = "size must be positive")
        @Schema(description = "文件大小（字节）", example = "1048576", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long size;

        /**
         * MIME 类型
         */
        @NotBlank(message = "type is required")
        @Schema(description = "MIME 类型", example = "video/mp4", requiredMode = Schema.RequiredMode.REQUIRED)
        private String type;
    }
}
