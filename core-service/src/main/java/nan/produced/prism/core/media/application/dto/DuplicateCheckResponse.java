package nan.produced.prism.core.media.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * MD5 秒传检查响应
 *
 * 安全说明：返回 fileEntityId 而非 existingKey（S3路径），避免信息泄露。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DuplicateCheckResponse {

    /**
     * 检查结果列表
     */
    private List<FileCheckResult> results;

    /**
     * 单个文件的检查结果
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FileCheckResult {

        /**
         * 前端传入的 clientId，用于映射
         */
        private String clientId;

        /**
         * 是否已存在（可秒传）
         */
        private boolean duplicate;

        /**
         * 已存在的文件实体ID（仅当 duplicate=true 时有值）
         * 用于 batch-finalize 落库时引用
         */
        private String fileEntityId;
    }
}
