package nan.produced.prism.device.boot.integration.core;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.boot.integration.ApiResponse;
import nan.produced.prism.device.boot.integration.core.dto.CoreDeviceProgramDTO;
import nan.produced.prism.device.boot.integration.core.dto.CoreDeviceProgramMediaDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * core-service 内部接口调用封装（device-service）。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>统一处理 Feign 调用结果（HTTP 状态/响应体/code）</li>
 *   <li>对外提供“查询设备节目/媒体清单”的简单方法，避免控制器写过多过程式逻辑</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoreProgramDistributionService {

    private final CoreProgramDistributionClient coreProgramDistributionClient;

    public List<CoreDeviceProgramDTO> listDevicePrograms(Long deviceId) {
        ResponseEntity<ApiResponse<List<CoreDeviceProgramDTO>>> response;
        try {
            response = coreProgramDistributionClient.listDevicePrograms(deviceId);
        } catch (Exception e) {
            log.error("Call core-service listDevicePrograms failed, deviceId={}", deviceId, e);
            return List.of();
        }
        return extractData(response, "listDevicePrograms");
    }

    public List<CoreDeviceProgramMediaDTO> listDeviceProgramMedia(Long deviceId, Integer deviceProgramId) {
        ResponseEntity<ApiResponse<List<CoreDeviceProgramMediaDTO>>> response;
        try {
            response = coreProgramDistributionClient.listDeviceProgramMedia(deviceId, deviceProgramId);
        } catch (Exception e) {
            log.error("Call core-service listDeviceProgramMedia failed, deviceId={}, programId={}", deviceId, deviceProgramId, e);
            return List.of();
        }
        return extractData(response, "listDeviceProgramMedia");
    }

    /**
     * 兼容不同服务的成功码：
     * <ul>
     *   <li>core-service: CORE-0000</li>
     *   <li>device-service: 200</li>
     * </ul>
     */
    private <T> List<T> extractData(ResponseEntity<ApiResponse<List<T>>> response, String apiName) {
        if (response == null || !response.getStatusCode().is2xxSuccessful()) {
            log.warn("core-service {} http status not ok: {}", apiName, response != null ? response.getStatusCode() : null);
            return List.of();
        }
        ApiResponse<List<T>> body = response.getBody();
        if (body == null) {
            log.warn("core-service {} empty body", apiName);
            return List.of();
        }
        String code = body.getCode();
        if (!isSuccessCode(code)) {
            log.warn("core-service {} failed: code={}, message={}", apiName, body.getCode(), body.getMessage());
            return List.of();
        }
        List<T> data = body.getData();
        return data != null ? data : List.of();
    }

    private boolean isSuccessCode(String code) {
        return "200".equals(code) || "CORE-0000".equals(code);
    }
}

