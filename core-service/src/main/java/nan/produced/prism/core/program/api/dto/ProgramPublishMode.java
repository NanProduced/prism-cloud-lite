package nan.produced.prism.core.program.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "发布模式：追加或覆盖")
public enum ProgramPublishMode {
    APPEND,
    OVERWRITE
}

