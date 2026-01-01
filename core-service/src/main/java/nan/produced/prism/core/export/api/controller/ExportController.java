package nan.produced.prism.core.export.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.export.api.dto.CreateExportRequest;
import nan.produced.prism.core.export.api.dto.CreateExportResponse;
import nan.produced.prism.core.export.api.dto.ExportDownloadResponse;
import nan.produced.prism.core.export.api.dto.ExportSchemaResponse;
import nan.produced.prism.core.export.application.service.ExportApplicationService;
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

@Tag(name = "Export")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/exports")
public class ExportController {

    private final ExportApplicationService exportApplicationService;

    @Operation(summary = "导出字段 schema（用于前端字段选择/格式选择）")
    @GetMapping("/schema")
    public ResponseEntity<BffResponse<ExportSchemaResponse>> schema(@RequestParam("type") String exportType) {
        var user = CloudAuthContext.getCurrentUser();
        ExportSchemaResponse data = exportApplicationService.getSchema(user.tier(), exportType);
        return ResponseEntity.ok(BffResponse.success(data).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "创建导出任务（异步）")
    @PostMapping
    public ResponseEntity<BffResponse<CreateExportResponse>> create(@RequestBody CreateExportRequest request) {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        String tier = CloudAuthContext.getCurrentUser().tier();
        CreateExportResponse data = exportApplicationService.createExportTask(userId, tier, request);
        return ResponseEntity.ok(BffResponse.success(data).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "获取导出文件下载链接（presigned URL）")
    @GetMapping("/{exportId}/download")
    public ResponseEntity<BffResponse<ExportDownloadResponse>> download(@PathVariable("exportId") UUID exportId) {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        ExportDownloadResponse data = exportApplicationService.getDownloadUrl(userId, exportId);
        return ResponseEntity.ok(BffResponse.success(data).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "删除导出文件（释放存储配额，幂等）")
    @PostMapping("/{exportId}/delete")
    public ResponseEntity<BffResponse<Void>> delete(@PathVariable("exportId") UUID exportId) {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        exportApplicationService.deleteExport(userId, exportId);
        return ResponseEntity.ok(BffResponse.<Void>success(null).withTraceId(TraceUtils.getTraceId()));
    }
}
