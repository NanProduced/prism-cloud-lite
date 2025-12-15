package nan.produced.prism.core.media.application.dto;

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
public class DuplicateCheckRequest {

    /**
     * 待检查的文件列表
     */
    @NotEmpty(message = "files is required")
    @Valid
    private List<FileCheckInfo> files;

    /**
     * 文件检查信息
     */
    @Data
    public static class FileCheckInfo {

        /**
         * 前端生成的临时ID，用于映射响应
         */
        @NotBlank(message = "clientId is required")
        private String clientId;

        /**
         * 文件 MD5 哈希（可选，大文件可跳过校验）
         */
        private String md5;

        /**
         * 文件大小（字节）
         */
        @NotNull(message = "size is required")
        @Positive(message = "size must be positive")
        private Long size;

        /**
         * MIME 类型
         */
        @NotBlank(message = "type is required")
        private String type;
    }
}
