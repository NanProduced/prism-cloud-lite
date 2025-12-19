package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "用户 Settings 覆盖值（仅保存用户修改过的字段；默认值由前端自行合并渲染）")
public record UserSettingsOverridesView(

    @Schema(description = "后端定义的结构化 settings 覆盖值（Merge合并语义）")
    Map<String, Object> settings,

    @Schema(description = "前端自由扩展的 ui 覆盖值（透传 JSON）")
    Map<String, Object> ui
) {
}

