package nan.produced.prism.core.media.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建媒体文件夹请求
 */
@Data
@Schema(name = "CreateFolderRequest", description = "创建文件夹请求")
public class CreateFolderRequest {

    /**
     * 父文件夹ID（null = 根目录）
     */
    @Schema(description = "父文件夹ID（不传/null 表示根目录）")
    private String parentId;

    /**
     * 文件夹名称
     */
    @NotBlank(message = "Folder name is required")
    @Schema(description = "文件夹名称（长度限制 64）", example = "项目素材", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
}
