package nan.produced.prism.core.media.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建媒体文件夹请求
 */
@Data
public class CreateFolderRequest {

    /**
     * 父文件夹ID（null = 根目录）
     */
    private String parentId;

    /**
     * 文件夹名称
     */
    @NotBlank(message = "Folder name is required")
    private String name;
}

