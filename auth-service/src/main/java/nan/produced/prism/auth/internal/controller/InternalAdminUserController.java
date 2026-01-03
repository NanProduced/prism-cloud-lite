package nan.produced.prism.auth.internal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.response.ApiResponse;
import nan.produced.prism.auth.domain.user.UserType;
import nan.produced.prism.auth.internal.dto.InternalAdminUserCreateRequest;
import nan.produced.prism.auth.internal.dto.InternalAdminUserCreatedView;
import nan.produced.prism.auth.internal.dto.InternalAdminUserItem;
import nan.produced.prism.auth.internal.dto.InternalAdminUserPageView;
import nan.produced.prism.auth.internal.dto.InternalAdminUserPasswordResetView;
import nan.produced.prism.auth.internal.service.InternalAdminUserService;
import nan.produced.prism.auth.utils.TraceUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "内部接口-管理账号", description = "仅供服务间调用（core-service），前端勿用")
@RestController
@RequestMapping("/internal/admin-users")
@RequiredArgsConstructor
public class InternalAdminUserController {

    private final InternalAdminUserService internalAdminUserService;

    @GetMapping
    @Operation(
        summary = "分页查询管理账号（Admin/Manager）",
        description = """
            供 core-service 管理端查询管理账号列表。

            - 访问路径：对外为 `/auth/internal/admin-users`（因为 auth-service context-path 为 `/auth`）；
            - 鉴权：service-signature + IP 白名单；
            - 响应体：`ApiResponse<InternalAdminUserPageView>`（内部 RPC 格式）。
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "成功返回分页",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = InternalAdminUserPageView.class)))
    public ResponseEntity<ApiResponse<InternalAdminUserPageView>> list(@RequestParam(value = "type", required = false) UserType userType,
                                                                       @RequestParam(value = "page", defaultValue = "0") int page,
                                                                       @RequestParam(value = "size", defaultValue = "20") int size) {
        try {
            InternalAdminUserPageView view = internalAdminUserService.list(userType, page, size);
            return ResponseEntity.ok(ApiResponse.success(view).withMeta(TraceUtils.getTraceId(), null));
        } catch (BizException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.<InternalAdminUserPageView>error(ex.getErrorCode(), ex.getMessage())
                    .withMeta(TraceUtils.getTraceId(), null));
        }
    }

    @GetMapping("/{publicId}")
    @Operation(summary = "按 publicId 查询管理账号")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "成功返回详情",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = InternalAdminUserItem.class)))
    public ResponseEntity<ApiResponse<InternalAdminUserItem>> get(@PathVariable("publicId") String publicId) {
        try {
            InternalAdminUserItem view = internalAdminUserService.getByPublicId(publicId);
            return ResponseEntity.ok(ApiResponse.success(view).withMeta(TraceUtils.getTraceId(), null));
        } catch (BizException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.<InternalAdminUserItem>error(ex.getErrorCode(), ex.getMessage())
                    .withMeta(TraceUtils.getTraceId(), null));
        }
    }

    @PostMapping
    @Operation(summary = "创建 Manager（返回一次性初始密码）")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "创建成功",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = InternalAdminUserCreatedView.class)))
    public ResponseEntity<ApiResponse<InternalAdminUserCreatedView>> create(@Valid @RequestBody InternalAdminUserCreateRequest request) {
        try {
            InternalAdminUserCreatedView view = internalAdminUserService.createManager(request.username(), request.password());
            return ResponseEntity.ok(ApiResponse.success(view).withMeta(TraceUtils.getTraceId(), null));
        } catch (BizException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.<InternalAdminUserCreatedView>error(ex.getErrorCode(), ex.getMessage())
                    .withMeta(TraceUtils.getTraceId(), null));
        }
    }

    @PostMapping("/{publicId}/lock")
    @Operation(summary = "锁定管理账号")
    public ResponseEntity<ApiResponse<Object>> lock(@PathVariable("publicId") String publicId) {
        try {
            internalAdminUserService.lock(publicId);
            return ResponseEntity.ok(ApiResponse.success().withMeta(TraceUtils.getTraceId(), null));
        } catch (BizException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.<Object>error(ex.getErrorCode(), ex.getMessage())
                    .withMeta(TraceUtils.getTraceId(), null));
        }
    }

    @PostMapping("/{publicId}/unlock")
    @Operation(summary = "解锁管理账号")
    public ResponseEntity<ApiResponse<Object>> unlock(@PathVariable("publicId") String publicId) {
        try {
            internalAdminUserService.unlock(publicId);
            return ResponseEntity.ok(ApiResponse.success().withMeta(TraceUtils.getTraceId(), null));
        } catch (BizException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.<Object>error(ex.getErrorCode(), ex.getMessage())
                    .withMeta(TraceUtils.getTraceId(), null));
        }
    }

    @PostMapping("/{publicId}/password/reset")
    @Operation(summary = "重置管理账号密码（返回一次性新密码）")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "重置成功",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = InternalAdminUserPasswordResetView.class)))
    public ResponseEntity<ApiResponse<InternalAdminUserPasswordResetView>> resetPassword(@PathVariable("publicId") String publicId) {
        try {
            InternalAdminUserPasswordResetView view = internalAdminUserService.resetPassword(publicId);
            return ResponseEntity.ok(ApiResponse.success(view).withMeta(TraceUtils.getTraceId(), null));
        } catch (BizException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.<InternalAdminUserPasswordResetView>error(ex.getErrorCode(), ex.getMessage())
                    .withMeta(TraceUtils.getTraceId(), null));
        }
    }
}

