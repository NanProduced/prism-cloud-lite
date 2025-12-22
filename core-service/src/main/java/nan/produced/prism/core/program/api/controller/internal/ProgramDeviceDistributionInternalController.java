package nan.produced.prism.core.program.api.controller.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.program.api.dto.internal.InternalDeviceProgramMediaResp;
import nan.produced.prism.core.program.api.dto.internal.InternalDeviceProgramResp;
import nan.produced.prism.core.program.application.service.ProgramDeviceDistributionService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部接口：device-service 调用 core-service 获取设备节目/素材信息。
 *
 * <p>为什么需要该接口：</p>
 * <ul>
 *   <li>device-service 与 core-service 将来会分库；device-service 不得直查 core DB</li>
 *   <li>设备只认 deviceProgramId(Integer)，并通过 /wp-json/wp/v2/programs + /wp-json/wp/v2/media 拉取下载清单</li>
 * </ul>
 *
 * <p>安全：</p>
 * <ul>
 *   <li>该接口不使用 CLOUD_AUTH（面向设备服务，不面向终端用户）</li>
 *   <li>由 core-service 的 internal 签名校验过滤器进行鉴权（X-Signature/X-Timestamp 等）</li>
 * </ul>
 */
@Tag(name = "内部接口-设备节目分发", description = "供 device-service 调用的内部只读接口")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/internal/devices")
public class ProgramDeviceDistributionInternalController {

    private final ProgramDeviceDistributionService programDeviceDistributionService;

    @Operation(summary = "查询设备已绑定节目列表", description = "返回 deviceProgramId/title/assignedAt 等（供 /wp-json/wp/v2/programs 适配）。")
    @GetMapping("/{deviceId}/programs")
    public ResponseEntity<ApiResponse<List<InternalDeviceProgramResp>>> listPrograms(
            @PathVariable("deviceId") @NotNull Long deviceId) {
        List<InternalDeviceProgramResp> list = programDeviceDistributionService.listDevicePrograms(deviceId);
        return ResponseEntity.ok(ApiResponse.success(list).withMeta(TraceUtils.getTraceId(), null, "core-service"));
    }

    @Operation(summary = "查询设备节目媒体清单", description = "返回可直接下载的 CDN URL 列表（供 /wp-json/wp/v2/media?parent= 适配）。")
    @GetMapping("/{deviceId}/programs/{deviceProgramId}/media")
    public ResponseEntity<ApiResponse<List<InternalDeviceProgramMediaResp>>> listMedia(
            @PathVariable("deviceId") @NotNull Long deviceId,
            @PathVariable("deviceProgramId") @NotNull Integer deviceProgramId) {
        List<InternalDeviceProgramMediaResp> list = programDeviceDistributionService.listDeviceProgramMedia(deviceId, deviceProgramId);
        return ResponseEntity.ok(ApiResponse.success(list).withMeta(TraceUtils.getTraceId(), null, "core-service"));
    }
}

