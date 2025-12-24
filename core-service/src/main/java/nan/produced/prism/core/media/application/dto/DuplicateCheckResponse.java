package nan.produced.prism.core.media.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "DuplicateCheckResponse", description = "秒传检查响应")
public class DuplicateCheckResponse {

    /**
     * 检查结果列表
     */
    @Schema(description = "检查结果列表（顺序与请求一致，通过 clientId 对应）")
    private List<FileCheckResult> results;

    /**
     * 单个文件的检查结果
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "DuplicateCheckFileCheckResult", description = "单个文件的秒传检查结果")
    public static class FileCheckResult {

        /**
         * 前端传入的 clientId，用于映射
         */
        @Schema(description = "请求中的 clientId")
        private String clientId;

        /**
         * 是否已存在（可秒传）
         */
        @Schema(description = "是否已存在（true 表示可秒传）")
        private boolean duplicate;

        /**
         * 已存在的文件实体ID（仅当 duplicate=true 时有值）
         * 用于 batch-finalize 落库时引用
         */
        @Schema(description = "已存在的文件实体ID（duplicate=true 时返回，供 batch-finalize 使用）")
        private String fileEntityId;
    }
}
