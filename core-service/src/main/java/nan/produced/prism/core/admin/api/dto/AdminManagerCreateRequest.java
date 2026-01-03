package nan.produced.prism.core.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端-创建 Manager 请求")
public record AdminManagerCreateRequest(
    @Schema(description = "username（v1 将存储在 auth email 字段中）")
    String username,
    @Schema(description = "可选：指定初始密码；为空则自动生成")
    String password
) {
}

