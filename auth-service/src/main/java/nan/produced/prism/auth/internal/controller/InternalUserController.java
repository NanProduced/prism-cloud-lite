package nan.produced.prism.auth.internal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.response.ApiResponse;
import nan.produced.prism.auth.internal.dto.InternalUserResponse;
import nan.produced.prism.auth.internal.service.InternalUserService;
import nan.produced.prism.auth.utils.TraceUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "内部接口-用户", description = "仅供服务间调用（core-service），前端勿用")
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final InternalUserService internalUserService;

    @GetMapping("/{publicId}")
    @Operation(
        summary = "按 publicId 查询用户",
        description = """
            供 core-service 通过 publicId 获取用户基础资料（用于聚合展示/权限校验等）。

            - 访问路径：对外为 `/auth/internal/users/{publicId}`（因为 auth-service context-path 为 `/auth`）；
            - 鉴权：service-signature + IP 白名单；
            - 响应体：`ApiResponse<InternalUserResponse>`（内部 RPC 格式）。
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "成功返回用户信息",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = InternalUserResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "publicId 不合法")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "签名无效或无权限（service-signature）")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "用户不存在")
    public ResponseEntity<ApiResponse<InternalUserResponse>> getUserByPublicId(
        @Parameter(description = "用户 publicId（对外展示的稳定 ID）") @PathVariable("publicId") String publicId) {
        try {
            InternalUserResponse response = internalUserService.findByPublicId(publicId);
            return ResponseEntity.ok(ApiResponse.success(response).withMeta(TraceUtils.getTraceId(), null));
        } catch (BizException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.<InternalUserResponse>error(ex.getErrorCode(), ex.getMessage())
                    .withMeta(TraceUtils.getTraceId(), null));
        }
    }
}
