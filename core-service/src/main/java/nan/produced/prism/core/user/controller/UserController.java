package nan.produced.prism.core.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.user.domain.UserProfileEntity;
import nan.produced.prism.core.user.dto.UserProfileView;
import nan.produced.prism.core.user.mapper.UserProfileMapper;
import nan.produced.prism.core.user.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户资料接口
 */
@Tag(name = "用户资料", description = "用户资料相关接口")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;
    private final UserProfileMapper userProfileMapper;

    @Operation(
        summary = "获取当前用户资料",
        description = "调用方必须通过 Gateway 鉴权，首次登录会触发 JIT Provisioning")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回用户资料",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserProfileView.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping("/me")
    public ResponseEntity<BffResponse<UserProfileView>> getCurrentUserProfile() {
        UserProfileEntity profile = userProfileService.getOrCreateCurrentUserProfile();
        UserProfileView view = userProfileMapper.toView(profile);
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }
}
