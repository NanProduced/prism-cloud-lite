package nan.produced.prism.core.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端-订阅兑换请求")
public record AdminUserSubscriptionRedeemRequest(
    @Schema(description = "兑换码")
    String code
) {
}

