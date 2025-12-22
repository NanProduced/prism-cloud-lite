package nan.produced.prism.core.program.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "发布范围：选择设备或当前运行设备集合")
public enum ProgramPublishScope {
    SELECTED,
    RUNNING
}

