package nan.produced.prism.core.device.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.device.api.dto.CreateCustomFieldReq;
import nan.produced.prism.core.device.api.dto.PatchDeviceCustomFieldValuesReq;
import nan.produced.prism.core.device.api.dto.UpdateCustomFieldReq;
import nan.produced.prism.core.device.application.port.inbound.DeviceCustomFieldOptionSpec;
import nan.produced.prism.core.device.application.port.inbound.DeviceCustomFieldUseCase;
import nan.produced.prism.core.device.domain.dto.DeviceCustomFieldDefVO;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "设备自定义列", description = "设备自定义列定义与字段值写入（GET/POST）")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/devices")
public class DeviceCustomFieldController {

    private final DeviceCustomFieldUseCase deviceCustomFieldUseCase;

    @GetMapping("/custom-fields")
    @Operation(summary = "获取当前用户自定义列定义列表", description = "用于 Grid 视图列管理加载自定义列定义（字段类型、选项、Pro 限制等）。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回自定义列定义列表",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DeviceCustomFieldDefVO.class))))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<List<DeviceCustomFieldDefVO>>> listCustomFields() {
        UUID userId = currentUserId();
        List<DeviceCustomFieldDefVO> defs = deviceCustomFieldUseCase.listCustomFieldDefs(userId);
        return ResponseEntity.ok(BffResponse.success(defs).withTraceId(TraceUtils.getTraceId()));
    }

    @PostMapping("/custom-fields")
    @Operation(summary = "创建自定义列", description = ".创建一条自定义列定义；自定义列数量受订阅配额限制，且 Free 用户不允许创建 Pro-only 列。")
    @ApiResponse(
            responseCode = "200",
            description = "成功创建自定义列",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeviceCustomFieldDefVO.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<DeviceCustomFieldDefVO>> createCustomField(@RequestBody @Validated CreateCustomFieldReq req) {
        UUID userId = currentUserId();
        String tier = currentTier();

        DeviceCustomFieldDefVO created = deviceCustomFieldUseCase.createCustomFieldDef(
                userId,
                tier,
                req.getDisplayName(),
                req.getFieldType(),
                req.getFieldKey(),
                req.getDescription(),
                req.getIcon(),
                req.getPlanTierRequired(),
                req.getSequence(),
                toOptionSpecs(req.getOptions()));

        return ResponseEntity.ok(BffResponse.success(created).withTraceId(TraceUtils.getTraceId()));
    }

    @PostMapping("/custom-fields/{fieldId}")
    @Operation(summary = "更新自定义列定义", description = "更新自定义列的显示名称/顺序/Pro 限制/选项等；仅允许更新当前用户创建的字段。")
    @ApiResponse(
            responseCode = "200",
            description = "成功更新自定义列",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DeviceCustomFieldDefVO.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<DeviceCustomFieldDefVO>> updateCustomField(
            @PathVariable("fieldId") @NotNull Long fieldId,
            @RequestBody @Valid UpdateCustomFieldReq req) {

        UUID userId = currentUserId();
        String tier = currentTier();

        DeviceCustomFieldDefVO updated = deviceCustomFieldUseCase.updateCustomFieldDef(
                userId,
                tier,
                fieldId,
                req.getDisplayName(),
                req.getDescription(),
                req.getIcon(),
                req.getPlanTierRequired(),
                req.getSequence(),
                toOptionSpecs(req.getOptions()));

        return ResponseEntity.ok(BffResponse.success(updated).withTraceId(TraceUtils.getTraceId()));
    }

    @PostMapping("/custom-fields/{fieldId}/delete")
    @Operation(summary = "删除自定义列", description = "删除当前用户的自定义列定义；会同时删除该列的所有设备值。")
    @ApiResponse(responseCode = "200", description = "成功删除自定义列")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<Void>> deleteCustomField(@PathVariable("fieldId") @NotNull Long fieldId) {
        UUID userId = currentUserId();
        String tier = currentTier();
        deviceCustomFieldUseCase.deleteCustomFieldDef(userId, tier, fieldId);
        return ResponseEntity.ok(BffResponse.<Void>success(null).withTraceId(TraceUtils.getTraceId()));
    }

    @PostMapping("/{deviceId}/custom-fields")
    @Operation(
            summary = "写入设备自定义列值（批量）",
            description = """
                    用于 Grid 单元格编辑：一次提交多个 fieldId 的值更新。

                    - values 的 key 为 fieldId（数字字符串）；
                    - value 支持 string/number/boolean/string[]/null；
                    - 返回 map 的 key 为 fieldKey，value 为写入后的值（便于前端直接刷新单元格）。
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "成功写入自定义列值",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<Map<String, Object>>> patchDeviceCustomFieldValues(
            @PathVariable("deviceId") @NotNull Long deviceId,
            @RequestBody @Validated PatchDeviceCustomFieldValuesReq req) {

        UUID userId = currentUserId();
        String tier = currentTier();
        Map<Long, Object> values = parseFieldIdMap(req.getValues());
        Map<String, Object> updated = deviceCustomFieldUseCase.patchDeviceCustomFieldValues(userId, tier, deviceId, values);
        return ResponseEntity.ok(BffResponse.success(updated).withTraceId(TraceUtils.getTraceId()));
    }

    private static List<DeviceCustomFieldOptionSpec> toOptionSpecs(List<CreateCustomFieldReq.Option> options) {
        if (options == null || options.isEmpty()) {
            return Collections.emptyList();
        }
        return options.stream()
                .map(o -> new DeviceCustomFieldOptionSpec(
                        o.getOptionKey(),
                        o.getDisplayName(),
                        o.getDescription(),
                        o.getSequence(),
                        o.getActive(),
                        o.getColor()))
                .toList();
    }

    private static Map<Long, Object> parseFieldIdMap(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<Long, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_VALUE_INVALID);
            }
            try {
                result.put(Long.parseLong(key.trim()), entry.getValue());
            } catch (NumberFormatException ex) {
                throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_VALUE_INVALID);
            }
        }
        return result;
    }

    private static UUID currentUserId() {
        return UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
    }

    private static String currentTier() {
        return CloudAuthContext.getCurrentUser().tier();
    }
}
