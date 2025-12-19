package nan.produced.prism.core.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.user.dto.UserActiveSessionView;
import nan.produced.prism.core.user.dto.UserChangePasswordRequest;
import nan.produced.prism.core.user.service.UserSecurityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "账号安全（用户）", description = "Dashboard Settings - Security")
@RestController
@RequestMapping("/api/v1/user/security")
@RequiredArgsConstructor
public class UserSecurityController {

    private final UserSecurityService userSecurityService;

    @Operation(summary = "获取活跃会话列表", description = "当前实现基于 auth-service remember-me token（设备维度）。")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回活跃会话列表",
        content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = UserActiveSessionView.class))))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping("/sessions")
    public ResponseEntity<BffResponse<List<UserActiveSessionView>>> listActiveSessions(HttpServletRequest request) {
        List<UserActiveSessionView> sessions = userSecurityService.listCurrentUserActiveSessions(request);
        return ResponseEntity.ok(BffResponse.success(sessions).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "注销单个会话")
    @ApiResponse(responseCode = "200", description = "注销成功")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @PostMapping("/sessions/{series}/revoke")
    public ResponseEntity<BffResponse<Object>> revokeSession(@PathVariable String series,
                                                             HttpServletRequest request,
                                                             HttpServletResponse response) {
        userSecurityService.revokeCurrentUserSession(series, request, response);
        return ResponseEntity.ok(BffResponse.success().withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "注销全部会话")
    @ApiResponse(responseCode = "200", description = "注销成功")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @PostMapping("/sessions/revoke-all")
    public ResponseEntity<BffResponse<Object>> revokeAllSessions(HttpServletRequest request, HttpServletResponse response) {
        userSecurityService.revokeAllCurrentUserSessions(request, response);
        return ResponseEntity.ok(BffResponse.success().withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "修改密码", description = "修改成功后会注销所有 remember-me 设备（要求重新登录）。")
    @ApiResponse(responseCode = "200", description = "修改成功")
    @ApiResponse(responseCode = "400", description = "请求参数不合法")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @PostMapping("/password/change")
    public ResponseEntity<BffResponse<Object>> changePassword(@RequestBody UserChangePasswordRequest request,
                                                              HttpServletRequest httpRequest,
                                                              HttpServletResponse httpResponse) {
        userSecurityService.changeCurrentUserPassword(request, httpRequest, httpResponse);
        return ResponseEntity.ok(BffResponse.success().withTraceId(TraceUtils.getTraceId()));
    }
}
