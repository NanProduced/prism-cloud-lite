package nan.produced.prism.core.media.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 重命名媒体库节点请求
 */
@Data
public class RenameNodeRequest {

    @NotBlank(message = "name is required")
    private String name;
}

