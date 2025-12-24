package nan.produced.prism.auth.internal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.response.ApiResponse;
import nan.produced.prism.auth.domain.audit.SecurityEventEntity;
import nan.produced.prism.auth.internal.dto.InternalChangePasswordRequest;
import nan.produced.prism.auth.internal.dto.InternalSecurityEventView;
import nan.produced.prism.auth.internal.dto.InternalSecurityHistoryPageView;
import nan.produced.prism.auth.internal.service.InternalAccountSecurityService;
import nan.produced.prism.auth.security.audit.SecurityAuditService;
import nan.produced.prism.auth.security.rememberme.RememberMeTokenService;
import nan.produced.prism.auth.security.rememberme.RememberMeTokenService.RememberedDeviceView;
import nan.produced.prism.auth.utils.TraceUtils;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Internal APIs for account security operations invoked by core-service.
 * <p>
 * All endpoints are under {@code /internal/**} and are protected by the service-signature filter.
 * </p>
 */
@Tag(name = "内部接口-账号安全", description = "仅供服务间调用（core-service），前端勿用")
@RestController
@RequestMapping("/internal/account/security")
@RequiredArgsConstructor
public class InternalAccountSecurityController {

    private final RememberMeTokenService rememberMeTokenService;
    private final InternalAccountSecurityService internalAccountSecurityService;
    private final SecurityAuditService securityAuditService;

    /**
     * 获取登录设备列表
     * @param userId 用户 ID
     * @param activeSeries 激活的 Series
     * @return
     */
    @GetMapping("/remember-me/tokens")
    @Operation(
        summary = "列出用户登录设备（remember-me）",
        description = """
            供 core-service 的“账号安全/登录设备”功能使用，返回用户当前有效的 remember-me token 列表。

            - `activeSeries`：由 core-service 传入，用于标记当前设备；
            - 鉴权：service-signature + IP 白名单；
            - 响应体：`ApiResponse<List<RememberedDeviceView>>`。
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "成功返回设备列表",
        content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = RememberedDeviceView.class))))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "签名无效或无权限（service-signature）")
    public ResponseEntity<ApiResponse<List<RememberedDeviceView>>> listRememberMeTokens(
        @Parameter(description = "用户ID（auth-service 内部 UUID）") @RequestParam("userId") UUID userId,
        @Parameter(description = "当前设备的 series（可选，用于标记 current=true）") @RequestParam(value = "activeSeries", required = false) String activeSeries) {
        List<RememberedDeviceView> devices = rememberMeTokenService.listActiveTokens(userId, activeSeries);
        return ResponseEntity.ok(ApiResponse.success(devices).withMeta(TraceUtils.getTraceId(), null));
    }

    /**
     * 获取安全事件列表
     * @param userId 用户 ID
     * @param page 页码
     * @param size 每页数量
     * @return 安全事件VO
     */
    @GetMapping("/history")
    @Operation(
        summary = "查询用户安全事件历史",
        description = """
            供 core-service 的“账号安全/登录历史”功能使用，分页返回用户的安全审计事件。

            - `page`：从 0 开始；
            - `size`：每页数量（默认 20）；
            - 鉴权：service-signature + IP 白名单；
            - 响应体：`ApiResponse<InternalSecurityHistoryPageView>`。
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "成功返回安全事件分页数据",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = InternalSecurityHistoryPageView.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "签名无效或无权限（service-signature）")
    public ResponseEntity<ApiResponse<InternalSecurityHistoryPageView>> listSecurityHistory(
        @Parameter(description = "用户ID（auth-service 内部 UUID）") @RequestParam("userId") UUID userId,
        @Parameter(description = "页码（从 0 开始）") @RequestParam(value = "page", defaultValue = "0") int page,
        @Parameter(description = "每页数量（默认 20）") @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<SecurityEventEntity> events = securityAuditService.listUserEvents(userId, page, size);
        List<InternalSecurityEventView> items = events.getContent().stream()
            .map(it -> new InternalSecurityEventView(
                it.getId(),
                it.getEventType() == null ? null : it.getEventType().name(),
                it.isSuccess(),
                it.getIpAddress(),
                it.getDeviceName(),
                it.getUserAgent(),
                it.getMetadata(),
                it.getCreatedAt()
            ))
            .toList();
        InternalSecurityHistoryPageView view = new InternalSecurityHistoryPageView(items, events.getNumber(), events.getSize(), events.getTotalElements());
        return ResponseEntity.ok(ApiResponse.success(view).withMeta(TraceUtils.getTraceId(), null));
    }

    @PostMapping("/remember-me/tokens/{series}/revoke")
    @Operation(
        summary = "撤销单个设备登录态",
        description = """
            撤销指定 series 的 remember-me token；撤销后该设备需要重新登录。
            鉴权：service-signature + IP 白名单；响应体：`ApiResponse<Object>`（成功时 data 为 null）。
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功撤销")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "签名无效或无权限（service-signature）")
    public ResponseEntity<ApiResponse<Object>> revokeRememberMeToken(@RequestParam("userId") UUID userId,
                                                                     @PathVariable("series") String series,
                                                                     HttpServletRequest request) {
        rememberMeTokenService.revokeToken(userId, series);
        securityAuditService.recordSessionRevoked(userId, series, request);
        return ResponseEntity.ok(ApiResponse.success().withMeta(TraceUtils.getTraceId(), null));
    }

    @PostMapping("/remember-me/tokens/revoke-all")
    @Operation(
        summary = "撤销全部设备登录态",
        description = """
            撤销该用户所有有效的 remember-me token；撤销后所有设备都需要重新登录。
            鉴权：service-signature + IP 白名单；响应体：`ApiResponse<Object>`（成功时 data 为 null）。
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功撤销全部设备登录态")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "签名无效或无权限（service-signature）")
    public ResponseEntity<ApiResponse<Object>> revokeAllRememberMeTokens(@RequestParam("userId") UUID userId,
                                                                         HttpServletRequest request) {
        rememberMeTokenService.revokeAll(userId);
        securityAuditService.recordSessionsRevoked(userId, request);
        return ResponseEntity.ok(ApiResponse.success().withMeta(TraceUtils.getTraceId(), null));
    }

    @PostMapping("/password/change")
    @Operation(
        summary = "修改用户密码",
        description = """
            供 core-service 的“账号安全/修改密码”功能使用。

            - 需要提供 `currentPassword` 和 `newPassword`；
            - 修改成功后会撤销该用户所有 remember-me 设备（强制重新登录）；
            - 鉴权：service-signature + IP 白名单；
            - 响应体：`ApiResponse<Object>`（成功时 data 为 null）。
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功修改密码")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数不合法或当前密码错误")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "签名无效或无权限（service-signature）")
    public ResponseEntity<ApiResponse<Object>> changePassword(@RequestParam("userId") UUID userId,
                                                              @RequestBody InternalChangePasswordRequest body,
                                                              HttpServletRequest request) {
        try {
            internalAccountSecurityService.changePassword(
                userId,
                body == null ? null : body.currentPassword(),
                body == null ? null : body.newPassword()
            );
            securityAuditService.recordPasswordChanged(userId, request);
            return ResponseEntity.ok(ApiResponse.success().withMeta(TraceUtils.getTraceId(), null));
        } catch (BizException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.<Object>error(ex.getErrorCode(), ex.getMessage())
                    .withMeta(TraceUtils.getTraceId(), null));
        }
    }
}
