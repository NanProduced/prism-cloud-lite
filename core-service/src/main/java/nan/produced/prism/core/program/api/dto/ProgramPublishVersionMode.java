package nan.produced.prism.core.program.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "发布版本模式：创建新版本或使用既有版本")
public enum ProgramPublishVersionMode {
    CREATE,
    EXISTING
}

