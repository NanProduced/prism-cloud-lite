package nan.produced.prism.auth.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "内部接口-创建管理账号请求（Manager）")
public record InternalAdminUserCreateRequest(
    @Schema(description = "username（建议 3~36，v1 将存储在 email 字段中）")
    @Size(min = 3, max = 36)
    String username,

    @Schema(description = "可选：指定初始密码；为空则自动生成")
    @Size(min = 8, max = 64)
    String password
) {
}

