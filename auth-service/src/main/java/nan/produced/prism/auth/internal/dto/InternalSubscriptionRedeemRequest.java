package nan.produced.prism.auth.internal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "InternalSubscriptionRedeemRequest", description = "兑换订阅请求（内部接口）")
public record InternalSubscriptionRedeemRequest(
    @Schema(description = "兑换码", example = "PR7F2A3B4C5D6E8F") String code
) {
}

