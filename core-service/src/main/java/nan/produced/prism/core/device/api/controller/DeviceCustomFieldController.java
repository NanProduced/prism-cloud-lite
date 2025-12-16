package nan.produced.prism.core.device.api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.*;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.device.api.dto.CreateCustomFieldReq;
import nan.produced.prism.core.device.api.dto.PatchDeviceCustomFieldValuesReq;
import nan.produced.prism.core.device.api.dto.UpdateCustomFieldReq;
import nan.produced.prism.core.device.application.port.inbound.DeviceCustomFieldOptionSpec;
import nan.produced.prism.core.device.application.port.inbound.DeviceCustomFieldUseCase;
import nan.produced.prism.core.device.domain.dto.DeviceCustomFieldDefVO;
import nan.produced.prism.core.security.CloudAuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/devices")
public class DeviceCustomFieldController {

    private final DeviceCustomFieldUseCase deviceCustomFieldUseCase;

    @GetMapping("/custom-fields")
    public ResponseEntity<BffResponse<List<DeviceCustomFieldDefVO>>> listCustomFields() {
        UUID userId = currentUserId();
        List<DeviceCustomFieldDefVO> defs = deviceCustomFieldUseCase.listCustomFieldDefs(userId);
        return ResponseEntity.ok(BffResponse.success(defs));
    }

    @PostMapping("/custom-fields")
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

        return ResponseEntity.ok(BffResponse.success(created));
    }

    @PostMapping("/custom-fields/{fieldId}")
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

        return ResponseEntity.ok(BffResponse.success(updated));
    }

    @PostMapping("/custom-fields/{fieldId}/delete")
    public ResponseEntity<BffResponse<Void>> deleteCustomField(@PathVariable("fieldId") @NotNull Long fieldId) {
        UUID userId = currentUserId();
        String tier = currentTier();
        deviceCustomFieldUseCase.deleteCustomFieldDef(userId, tier, fieldId);
        return ResponseEntity.ok(BffResponse.success(null));
    }

    @PostMapping("/{deviceId}/custom-fields")
    public ResponseEntity<BffResponse<Map<String, Object>>> patchDeviceCustomFieldValues(
            @PathVariable("deviceId") @NotNull Long deviceId,
            @RequestBody @Validated PatchDeviceCustomFieldValuesReq req) {

        UUID userId = currentUserId();
        String tier = currentTier();
        Map<Long, Object> values = parseFieldIdMap(req.getValues());
        Map<String, Object> updated = deviceCustomFieldUseCase.patchDeviceCustomFieldValues(userId, tier, deviceId, values);
        return ResponseEntity.ok(BffResponse.success(updated));
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
