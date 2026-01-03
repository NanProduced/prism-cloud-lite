package nan.produced.prism.core.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.admin.api.dto.AdminMeView;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.security.api.AdminAuthz;
import nan.produced.prism.core.security.api.CloudAuthContext;
import nan.produced.prism.core.security.api.CloudAuthUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端-我的信息", description = "Console 管理员信息")
@RestController
@RequestMapping("/api/v1/admin/me")
@RequiredArgsConstructor
public class AdminMeController {

    @Operation(summary = "获取当前管理员信息")
    @GetMapping
    public ResponseEntity<BffResponse<AdminMeView>> me() {
        AdminAuthz.requireAdmin();
        CloudAuthUser user = CloudAuthContext.getCurrentUser();
        AdminMeView view = new AdminMeView(user.publicId(), user.userUuid(), user.roles(), user.tier());
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }
}

