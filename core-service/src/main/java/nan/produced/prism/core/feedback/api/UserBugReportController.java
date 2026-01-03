package nan.produced.prism.core.feedback.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.feedback.api.dto.UserBugReportCreateRequest;
import nan.produced.prism.core.feedback.api.dto.UserBugReportCreatedView;
import nan.produced.prism.core.feedback.application.BugReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "反馈（用户）", description = "提交 bug/反馈")
@RestController
@RequestMapping("/api/v1/feedback/bugs")
@RequiredArgsConstructor
public class UserBugReportController {

    private final BugReportService bugReportService;

    @Operation(summary = "提交 bug/反馈（登录用户）")
    @ApiResponse(
            responseCode = "200",
            description = "提交成功",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserBugReportCreatedView.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @PostMapping
    public ResponseEntity<BffResponse<UserBugReportCreatedView>> create(@RequestBody UserBugReportCreateRequest request,
                                                                        HttpServletRequest httpRequest) {
        UserBugReportCreatedView view = bugReportService.createForCurrentUser(request, httpRequest);
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }
}

