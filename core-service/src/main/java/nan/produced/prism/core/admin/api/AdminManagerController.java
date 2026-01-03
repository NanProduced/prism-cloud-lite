package nan.produced.prism.core.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.admin.api.dto.AdminManagerCreateRequest;
import nan.produced.prism.core.admin.api.dto.AdminManagerCreatedView;
import nan.produced.prism.core.admin.api.dto.AdminManagerPageView;
import nan.produced.prism.core.admin.api.dto.AdminManagerPasswordResetView;
import nan.produced.prism.core.admin.application.AdminManagerService;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.security.api.AdminAuthz;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-管理员账号", description = "管理平台账号管理（仅 Admin 可用）")
@RestController
@RequestMapping("/api/v1/admin/managers")
@RequiredArgsConstructor
public class AdminManagerController {

    private final AdminManagerService adminManagerService;

    @Operation(summary = "分页查询 managers")
    @GetMapping
    public ResponseEntity<BffResponse<AdminManagerPageView>> list(@RequestParam(value = "page", defaultValue = "0") int page,
                                                                  @RequestParam(value = "size", defaultValue = "20") int size) {
        AdminAuthz.requireAdmin();
        AdminManagerPageView view = adminManagerService.listManagers(page, size);
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "创建 manager（返回一次性初始密码）")
    @PostMapping
    public ResponseEntity<BffResponse<AdminManagerCreatedView>> create(@RequestBody AdminManagerCreateRequest request) {
        AdminAuthz.requireAdmin();
        AdminManagerCreatedView view = adminManagerService.createManager(request);
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "锁定 manager")
    @PostMapping("/{publicId}/lock")
    public ResponseEntity<BffResponse<Object>> lock(@PathVariable("publicId") String publicId) {
        AdminAuthz.requireAdmin();
        adminManagerService.lockManager(publicId);
        return ResponseEntity.ok(BffResponse.success().withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "解锁 manager")
    @PostMapping("/{publicId}/unlock")
    public ResponseEntity<BffResponse<Object>> unlock(@PathVariable("publicId") String publicId) {
        AdminAuthz.requireAdmin();
        adminManagerService.unlockManager(publicId);
        return ResponseEntity.ok(BffResponse.success().withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "重置 manager 密码（返回一次性新密码）")
    @PostMapping("/{publicId}/password/reset")
    public ResponseEntity<BffResponse<AdminManagerPasswordResetView>> resetPassword(@PathVariable("publicId") String publicId) {
        AdminAuthz.requireAdmin();
        AdminManagerPasswordResetView view = adminManagerService.resetPassword(publicId);
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }
}

