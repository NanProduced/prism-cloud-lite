package nan.produced.prism.core.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.admin.api.dto.AdminUserDetailView;
import nan.produced.prism.core.admin.api.dto.AdminUserSessionsRevokeResult;
import nan.produced.prism.core.admin.api.dto.AdminUserSearchPageView;
import nan.produced.prism.core.admin.api.dto.AdminUserSubscriptionRedeemRequest;
import nan.produced.prism.core.admin.application.AdminUserService;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.security.api.AdminAuthz;
import nan.produced.prism.core.user.dto.UserApiKeyView;
import nan.produced.prism.core.user.dto.UserSubscriptionView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-用户救火", description = "按 publicId 处理用户账号（订阅/会话/API Keys）")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "按 publicId 查询用户详情（聚合）")
    @GetMapping("/{publicId}")
    public ResponseEntity<BffResponse<AdminUserDetailView>> detail(@PathVariable("publicId") String publicId) {
        AdminAuthz.requireAdmin();
        AdminUserDetailView view = adminUserService.getUserDetail(publicId);
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "搜索用户（email/phone/publicId）")
    @GetMapping("/search")
    public ResponseEntity<BffResponse<AdminUserSearchPageView>> search(@RequestParam("q") String q,
                                                                       @RequestParam(value = "page", defaultValue = "0") int page,
                                                                       @RequestParam(value = "size", defaultValue = "20") int size) {
        AdminAuthz.requireAdmin();
        AdminUserSearchPageView view = adminUserService.searchUsers(q, page, size);
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "锁定用户")
    @PostMapping("/{publicId}/lock")
    public ResponseEntity<BffResponse<Object>> lock(@PathVariable("publicId") String publicId) {
        AdminAuthz.requireAdmin();
        adminUserService.lockUser(publicId);
        return ResponseEntity.ok(BffResponse.success().withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "解锁用户")
    @PostMapping("/{publicId}/unlock")
    public ResponseEntity<BffResponse<Object>> unlock(@PathVariable("publicId") String publicId) {
        AdminAuthz.requireAdmin();
        adminUserService.unlockUser(publicId);
        return ResponseEntity.ok(BffResponse.success().withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "查询用户 API Keys")
    @GetMapping("/{publicId}/api-keys")
    public ResponseEntity<BffResponse<List<UserApiKeyView>>> listApiKeys(@PathVariable("publicId") String publicId) {
        AdminAuthz.requireAdmin();
        List<UserApiKeyView> view = adminUserService.listUserApiKeys(publicId);
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "撤销用户 API Key")
    @PostMapping("/{publicId}/api-keys/{id}/revoke")
    public ResponseEntity<BffResponse<Object>> revokeApiKey(@PathVariable("publicId") String publicId,
                                                            @PathVariable("id") String apiKeyId) {
        AdminAuthz.requireAdmin();
        adminUserService.revokeUserApiKey(publicId, apiKeyId);
        return ResponseEntity.ok(BffResponse.success().withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "查询用户订阅")
    @GetMapping("/{publicId}/subscription")
    public ResponseEntity<BffResponse<UserSubscriptionView>> subscription(@PathVariable("publicId") String publicId) {
        AdminAuthz.requireAdmin();
        UserSubscriptionView view = adminUserService.getUserSubscription(publicId);
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "兑换订阅（使用兑换码）")
    @PostMapping("/{publicId}/subscription/redeem")
    public ResponseEntity<BffResponse<UserSubscriptionView>> redeem(@PathVariable("publicId") String publicId,
                                                                    @RequestBody AdminUserSubscriptionRedeemRequest request) {
        AdminAuthz.requireAdmin();
        UserSubscriptionView view = adminUserService.redeemUserSubscription(publicId, request == null ? null : request.code());
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "强制下线（撤销 remember-me + 删除 gateway session）")
    @PostMapping("/{publicId}/sessions/revoke-all")
    public ResponseEntity<BffResponse<AdminUserSessionsRevokeResult>> revokeAllSessions(@PathVariable("publicId") String publicId) {
        AdminAuthz.requireAdmin();
        AdminUserSessionsRevokeResult view = adminUserService.revokeAllUserSessions(publicId);
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }
}
