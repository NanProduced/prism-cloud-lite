package nan.produced.prism.core.feedback.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.feedback.api.dto.AdminBugReportDetailView;
import nan.produced.prism.core.feedback.api.dto.AdminBugReportPageView;
import nan.produced.prism.core.feedback.api.dto.AdminBugReportReplyRequest;
import nan.produced.prism.core.feedback.api.dto.AdminBugReportUpdateStatusRequest;
import nan.produced.prism.core.feedback.application.BugReportService;
import nan.produced.prism.core.feedback.domain.BugReportStatus;
import nan.produced.prism.core.security.api.AdminAuthz;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "反馈（管理端）", description = "管理平台处理用户反馈")
@RestController
@RequestMapping("/api/v1/admin/feedback/bugs")
@RequiredArgsConstructor
public class AdminBugReportController {

    private final BugReportService bugReportService;

    @Operation(summary = "分页查询 bug 列表")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回分页",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminBugReportPageView.class)))
    @GetMapping
    public ResponseEntity<BffResponse<AdminBugReportPageView>> list(@RequestParam(value = "status", required = false) BugReportStatus status,
                                                                    @RequestParam(value = "page", defaultValue = "0") int page,
                                                                    @RequestParam(value = "size", defaultValue = "20") int size) {
        AdminAuthz.requireAdmin();
        AdminBugReportPageView view = bugReportService.listForAdmin(status, page, size);
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "查询 bug 详情")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回详情",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminBugReportDetailView.class)))
    @GetMapping("/{id}")
    public ResponseEntity<BffResponse<AdminBugReportDetailView>> detail(@PathVariable("id") UUID id) {
        AdminAuthz.requireAdmin();
        AdminBugReportDetailView view = bugReportService.getDetail(id);
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "更新 bug 状态/备注")
    @ApiResponse(responseCode = "200", description = "更新成功")
    @PostMapping("/{id}/status")
    public ResponseEntity<BffResponse<Object>> updateStatus(@PathVariable("id") UUID id,
                                                            @RequestBody AdminBugReportUpdateStatusRequest request) {
        AdminAuthz.requireAdmin();
        bugReportService.updateStatus(id, request);
        return ResponseEntity.ok(BffResponse.success().withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "回复用户（发送邮件）")
    @ApiResponse(responseCode = "200", description = "发送成功")
    @PostMapping("/{id}/reply")
    public ResponseEntity<BffResponse<Object>> reply(@PathVariable("id") UUID id,
                                                     @RequestBody AdminBugReportReplyRequest request) {
        AdminAuthz.requireAdmin();
        bugReportService.saveReply(id, request);
        return ResponseEntity.ok(BffResponse.success().withTraceId(TraceUtils.getTraceId()));
    }
}
