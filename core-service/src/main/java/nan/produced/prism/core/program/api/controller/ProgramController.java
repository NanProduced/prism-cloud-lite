package nan.produced.prism.core.program.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.program.api.dto.CreateProgramReq;
import nan.produced.prism.core.program.api.dto.ProgramDetailResp;
import nan.produced.prism.core.program.api.dto.ProgramDraftResp;
import nan.produced.prism.core.program.api.dto.ProgramListResp;
import nan.produced.prism.core.program.api.dto.ProgramAuditLogResp;
import nan.produced.prism.core.program.api.dto.ProgramPublishReq;
import nan.produced.prism.core.program.api.dto.ProgramPublishResp;
import nan.produced.prism.core.program.api.dto.ProgramRenameReq;
import nan.produced.prism.core.program.api.dto.ProgramTemplateResp;
import nan.produced.prism.core.program.api.dto.SaveProgramDraftReq;
import nan.produced.prism.core.program.api.dto.ProgramUnpublishReq;
import nan.produced.prism.core.program.api.dto.ProgramUnpublishResp;
import nan.produced.prism.core.program.application.service.ProgramApplicationService;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "节目", description = "节目模块（Programs）接口（面向 SPA，经由 Gateway 访问）")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/programs")
public class ProgramController {

    private final ProgramApplicationService programApplicationService;

    @Operation(summary = "创建节目", description = "创建一个节目容器（平台侧概念：Program），后续可保存草稿与发布多个版本。")
    @ApiResponse(
            responseCode = "200",
            description = "成功创建节目",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProgramDetailResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @PostMapping
    public ResponseEntity<BffResponse<ProgramDetailResp>> createProgram(@RequestBody @Valid CreateProgramReq req) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        String tier = CloudAuthContext.getCurrentUser().tier();
        ProgramDetailResp created = programApplicationService.createProgram(userId, tier, req);
        return ResponseEntity.ok(BffResponse.success(created).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "查询节目列表（不分页）", description = "Lite 个人用户场景，默认不分页返回用户全部节目。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回节目列表",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProgramListResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping
    public ResponseEntity<BffResponse<List<ProgramListResp>>> listPrograms() {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        List<ProgramListResp> list = programApplicationService.listPrograms(userId);
        return ResponseEntity.ok(BffResponse.success(list).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "获取节目详情", description = "返回节目基础信息 + drafts + versions + deployments（用于状态页/编辑器入口）。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回节目详情",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProgramDetailResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "节目不存在或无权访问")
    @GetMapping("/{programId}")
    public ResponseEntity<BffResponse<ProgramDetailResp>> getProgram(@PathVariable("programId") @NotNull UUID programId) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        ProgramDetailResp detail = programApplicationService.getProgramDetail(userId, programId);
        return ResponseEntity.ok(BffResponse.success(detail).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "重命名节目", description = "仅修改 Program.name（draft/release 不存 name）。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回更新后的节目详情",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProgramDetailResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "节目不存在或无权访问")
    @PostMapping("/{programId}/rename")
    public ResponseEntity<BffResponse<ProgramDetailResp>> renameProgram(
            @PathVariable("programId") @NotNull UUID programId,
            @RequestBody @Valid ProgramRenameReq req) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        ProgramDetailResp detail = programApplicationService.renameProgram(userId, programId, req);
        return ResponseEntity.ok(BffResponse.success(detail).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "删除节目", description = "删除 Program 及其草稿/版本/部署关系（DB 级联）。")
    @ApiResponse(responseCode = "200", description = "成功删除")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "节目不存在或无权访问")
    @PostMapping("/{programId}/delete")
    public ResponseEntity<BffResponse<Void>> deleteProgram(@PathVariable("programId") @NotNull UUID programId) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        programApplicationService.deleteProgram(userId, programId);
        return ResponseEntity.ok(BffResponse.<Void>success(null).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "获取或创建草稿", description = "按 baseVersion 获取/创建草稿（同一 program + baseVersion 仅一份草稿）。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回草稿",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProgramDraftResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "节目不存在或无权访问")
    @PostMapping("/{programId}/drafts/ensure")
    public ResponseEntity<BffResponse<ProgramDraftResp>> ensureDraft(
            @PathVariable("programId") @NotNull UUID programId,
            @RequestParam(value = "baseVersion", required = false) Integer baseVersion) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        ProgramDraftResp draft = programApplicationService.ensureDraft(userId, programId, baseVersion);
        return ResponseEntity.ok(BffResponse.success(draft).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "保存草稿（真保存）", description = "保存 VSN JSON + 封面截图（用于节目列表未发布场景预览）。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回最新草稿",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProgramDraftResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "节目/草稿不存在或无权访问")
    @PostMapping("/{programId}/drafts/{draftId}/save")
    public ResponseEntity<BffResponse<ProgramDraftResp>> saveDraft(
            @PathVariable("programId") @NotNull UUID programId,
            @PathVariable("draftId") @NotNull UUID draftId,
            @RequestBody @Valid SaveProgramDraftReq req) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        ProgramDraftResp draft = programApplicationService.saveDraft(userId, programId, draftId, req);
        return ResponseEntity.ok(BffResponse.success(draft).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "删除草稿", description = "删除指定草稿（用于 Discard draft changes）。")
    @ApiResponse(responseCode = "200", description = "成功删除草稿")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "节目/草稿不存在或无权访问")
    @PostMapping("/{programId}/drafts/{draftId}/delete")
    public ResponseEntity<BffResponse<Void>> deleteDraft(
            @PathVariable("programId") @NotNull UUID programId,
            @PathVariable("draftId") @NotNull UUID draftId) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        programApplicationService.deleteDraft(userId, programId, draftId);
        return ResponseEntity.ok(BffResponse.<Void>success(null).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "查询模板列表", description = "Templates Tab 使用（不分页）。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回模板列表",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProgramTemplateResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping("/templates")
    public ResponseEntity<BffResponse<List<ProgramTemplateResp>>> listTemplates() {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        List<ProgramTemplateResp> list = programApplicationService.listTemplates(userId);
        return ResponseEntity.ok(BffResponse.success(list).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "发布节目", description = "创建/选择版本，并建立设备-节目绑定关系，同时下发 {\"program\":\"dirty\"} 指令触发设备同步。")
    @ApiResponse(
            responseCode = "200",
            description = "成功发布",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProgramPublishResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "节目不存在或无权访问")
    @PostMapping("/{programId}/publish")
    public ResponseEntity<BffResponse<ProgramPublishResp>> publish(
            @PathVariable("programId") @NotNull UUID programId,
            @RequestBody @Valid ProgramPublishReq req) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        String tier = CloudAuthContext.getCurrentUser().tier();
        ProgramPublishResp resp = programApplicationService.publish(userId, tier, programId, req);
        return ResponseEntity.ok(BffResponse.success(resp).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "取消发布（解除设备绑定）", description = "删除设备-节目绑定关系，并下发 {\"program\":\"dirty\"} 指令触发设备同步。")
    @ApiResponse(
            responseCode = "200",
            description = "成功取消发布",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProgramUnpublishResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "节目不存在或无权访问")
    @PostMapping("/{programId}/unpublish")
    public ResponseEntity<BffResponse<ProgramUnpublishResp>> unpublish(
            @PathVariable("programId") @NotNull UUID programId,
            @RequestBody @Valid ProgramUnpublishReq req) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        ProgramUnpublishResp resp = programApplicationService.unpublish(userId, programId, req);
        return ResponseEntity.ok(BffResponse.success(resp).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "查询节目审计日志", description = "状态页 Full Audit Trail 使用（按时间倒序）。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回审计日志",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProgramAuditLogResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "节目不存在或无权访问")
    @GetMapping("/{programId}/audit-logs")
    public ResponseEntity<BffResponse<List<ProgramAuditLogResp>>> listAuditLogs(
            @PathVariable("programId") @NotNull UUID programId) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        List<ProgramAuditLogResp> logs = programApplicationService.listAuditLogs(userId, programId);
        return ResponseEntity.ok(BffResponse.success(logs).withTraceId(TraceUtils.getTraceId()));
    }
}
