package nan.produced.prism.core.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.security.api.CloudAuthContext;
import nan.produced.prism.core.user.dto.UserQuotaOverviewView;
import nan.produced.prism.core.user.dto.UserStorageLedgerView;
import nan.produced.prism.core.user.dto.UserStorageQuotaView;
import nan.produced.prism.core.user.service.UserQuotaQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "配额（用户）", description = "Dashboard 卡片：配额与存储用量")
@RestController
@RequestMapping("/api/v1/user/quota")
@RequiredArgsConstructor
public class UserQuotaController {

    private final UserQuotaQueryService userQuotaQueryService;

    @GetMapping("/storage")
    @Operation(
            summary = "快速获取用户存储配额与占用",
            description = """
                    返回当前用户的总存储上限（来自订阅配额）与已用/可用空间（来自配额用量表与对账表）。

                    - 适用于 SPA 顶部/设置页的“存储空间”卡片；
                    - `quotaBytes = -1` 表示无限制；此时 `availableBytes/percent` 为 null。
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "成功返回存储配额",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserStorageQuotaView.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<UserStorageQuotaView>> getStorageQuota() {
        var user = CloudAuthContext.getCurrentUser();
        if (user == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        UUID userId = UUID.fromString(user.userUuid());
        UserStorageQuotaView view = userQuotaQueryService.getStorageQuota(userId, user.tier());
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }

    @GetMapping("/overview")
    @Operation(
            summary = "获取用户各项配额的使用情况与上限",
            description = """
                    返回当前用户的配额使用情况（devices/programs/customColumns/storageBytes）与订阅上限（FREE/PRO）。

                    - 用于 SPA 渲染“配额卡片/进度条”；
                    - `limit = -1` 表示无限制；`percent` 为 null；
                    - `programVersionsPerProgram` 是“每个节目”的版本上限，属于限制项而非用户级 usage。
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "成功返回配额总览",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserQuotaOverviewView.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<UserQuotaOverviewView>> getQuotaOverview() {
        var user = CloudAuthContext.getCurrentUser();
        if (user == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        UUID userId = UUID.fromString(user.userUuid());
        UserQuotaOverviewView view = userQuotaQueryService.getQuotaOverview(userId, user.tier());
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }

    @GetMapping("/storage/ledger")
    @Operation(
            summary = "获取用户存储空间对账明细（全量）",
            description = """
                    返回用户存储空间的全量对账信息：总占用 + 按来源（media-library/screenshot/export/vsn）拆分明细。

                    - 用于 SPA 的“存储占用对账/诊断”页面；
                    - `mismatchBytes` 用于排查 `pcc_user_quota_usage.storage_total_bytes` 与 `pcc_user_storage_usage` 汇总不一致的问题。
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "成功返回对账明细",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserStorageLedgerView.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<UserStorageLedgerView>> getStorageLedger() {
        var user = CloudAuthContext.getCurrentUser();
        if (user == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        UUID userId = UUID.fromString(user.userUuid());
        UserStorageLedgerView view = userQuotaQueryService.getStorageLedger(userId, user.tier());
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }
}

