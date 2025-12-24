package nan.produced.prism.core.device.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.device.api.converter.DeviceConverter;
import nan.produced.prism.core.device.api.dto.CreateDeviceReq;
import nan.produced.prism.core.device.api.dto.CreateDeviceResp;
import nan.produced.prism.core.device.api.dto.DeviceDetailResp;
import nan.produced.prism.core.device.api.dto.FilterDeviceReq;
import nan.produced.prism.core.device.application.port.inbound.DeviceManageUseCase;
import nan.produced.prism.core.device.application.port.inbound.DeviceSearchUseCase;
import nan.produced.prism.core.device.domain.dto.CreateDeviceDTO;
import nan.produced.prism.core.device.domain.dto.DeviceListVO;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "设备", description = "设备创建与列表查询（面向 SPA，经由 Gateway 访问）")
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/devices", "/device"})
@Validated
public class DeviceCommonController {

    private final DeviceConverter deviceConverter;

    private final DeviceManageUseCase deviceManageUseCase;

    private final DeviceSearchUseCase deviceSearchUseCase;

    @Operation(
            summary = "创建设备",
            description = """
                    创建一台新设备并在 core-service 中建立设备与用户的归属关系。

                    注意：当前接口已包含设备配额校验；设备截图初始化将在后续补齐。
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "成功创建设备",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateDeviceResp.class)))
    @ApiResponse(responseCode = "400", description = "设备配额不足（已达到当前套餐设备上限）")
    @ApiResponse(responseCode = "502", description = "调用 device-service 失败或返回异常")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @PostMapping({"", "/create"})
    public ResponseEntity<BffResponse<CreateDeviceResp>> createDevice(@RequestBody @Validated CreateDeviceReq req) {
        String userUuid = CloudAuthContext.getCurrentUser().userUuid();
        String tier = CloudAuthContext.getCurrentUser().tier();
        UUID userId = UUID.fromString(userUuid);
        CreateDeviceDTO createDeviceDTO = deviceConverter.toCreateDeviceDTO(userId, tier, req);
        Long deviceId = deviceManageUseCase.createDevice(createDeviceDTO);
        return ResponseEntity.ok(BffResponse.success(new CreateDeviceResp(deviceId, req.getDisplayName(), req.getAccount(), req.getPassword()))
                .withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(
            summary = "查询当前用户设备列表（不分页）",
            description = """
                    用于设备模块 Card/Table 视图加载设备概览数据；Lite 为个人用户场景，默认不分页，直接返回用户全部设备。

                    - 自定义列定义请使用 `GET /api/v1/devices/custom-fields` 单独获取并缓存；
                    - 本接口返回 `customFieldValues`（key=fieldKey），前端将其与 defs 关联后渲染自定义列。
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "成功返回设备列表",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DeviceListVO.class))))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping
    public ResponseEntity<BffResponse<List<DeviceListVO>>> listDevices() {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        List<DeviceListVO> list = deviceSearchUseCase.listAllUsersDevices(userId);
        return ResponseEntity.ok(BffResponse.success(list).withTraceId(TraceUtils.getTraceId()));
    }

    /**
     * 获取设备详情
     * @param deviceId 设备ID
     * @return 设备详情
     */
    @Operation(
            summary = "获取设备详情",
            description = """
                    用于设备详情页加载单台设备的完整信息（基础字段 + tags + customFieldValues + properties）。

                    - 自定义列定义请使用 `GET /api/v1/devices/custom-fields` 单独获取并缓存；
                    - 本接口返回 `customFieldValues`（key=fieldKey），前端将其与 defs 关联后渲染自定义列。
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "成功返回设备详情",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeviceDetailResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "设备不存在或无权访问")
    @GetMapping({"/{deviceId:\\d+}/detail", "/{deviceId:\\d+}"})
    public ResponseEntity<BffResponse<DeviceDetailResp>> getDeviceDetail(@PathVariable("deviceId") @NotNull Long deviceId) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        DeviceDetailResp detail = deviceSearchUseCase.getDeviceDetail(userId, deviceId);
        return ResponseEntity.ok(BffResponse.success(detail).withTraceId(TraceUtils.getTraceId()));
    }

    /**
     * 筛选设备(前端目前不使用，预留用于用户API调用)
     * @param req 筛选参数
     * @return 设备列表
     */
    @Operation(
            summary = "筛选设备（服务端过滤，不分页）",
            description = """
                    用于开放 API/后续扩展的服务端筛选能力；当前 Lite 个人场景设备数量较少，前端默认全量加载后本地过滤。

                    当前支持的筛选条件为交集：keyword、networkType、onlineStatus、model。
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "成功返回筛选后的设备列表",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DeviceListVO.class))))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @PostMapping("/filter")
    public ResponseEntity<BffResponse<List<DeviceListVO>>> filterDevices(@RequestBody(required = false) FilterDeviceReq req) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        List<DeviceListVO> list = deviceSearchUseCase.filterDevices(userId, req);
        return ResponseEntity.ok(BffResponse.success(list).withTraceId(TraceUtils.getTraceId()));
    }
}
