package nan.produced.prism.device.infrastructure.internal.core;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.infrastructure.internal.ApiResponse;
import nan.produced.prism.device.infrastructure.internal.core.dto.CoreDeviceProgramDTO;
import nan.produced.prism.device.infrastructure.internal.core.dto.CoreDeviceProgramMediaDTO;
import nan.produced.prism.device.infrastructure.internal.core.dto.DeviceProgramDTO;
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

    /**
     * 查询设备的节目列表
     * @param deviceId 设备ID
     * @param baseUrl 当前 Web 应用的根路径
     * @return 设备节目列表
     */
    public List<DeviceProgramDTO> listDevicePrograms(Long deviceId, String baseUrl) {
        ResponseEntity<ApiResponse<List<CoreDeviceProgramDTO>>> response;
        try {
            response = coreProgramDistributionClient.listDevicePrograms(deviceId);
        } catch (Exception e) {
            log.error("Call core-service listDevicePrograms failed, deviceId={}", deviceId, e);
            return List.of();
        }
        List<CoreDeviceProgramDTO> listDevicePrograms = extractData(response, "listDevicePrograms");
        return convertToDeviceApiProgramDTO(listDevicePrograms, baseUrl);
    }

    /**
     * 查询设备的节目的素材列表
     * @param deviceId 设备ID
     * @param deviceProgramId 设备节目ID
     * @return 设备节目的素材列表
     */
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
     * 转换核心服务返回的节目列表为对外接口的节目列表
     * @param programs 节目列表
     * @param baseUrl 当前 Web 应用的根路径
     * @return 设备节目列表
     */
    private List<DeviceProgramDTO> convertToDeviceApiProgramDTO(List<CoreDeviceProgramDTO> programs, String baseUrl) {
        if (programs == null || programs.isEmpty()) {
            return List.of();
        }
        List<DeviceProgramDTO> list = new ArrayList<>();
        for (CoreDeviceProgramDTO program : programs) {
            if (program == null || program.getDeviceProgramId() == null) {
                continue;
            }
            Integer programId = program.getDeviceProgramId();
            OffsetDateTime createdAt = program.getCreatedAt();
            OffsetDateTime assignedAt = program.getAssignedAt();

            String title = program.getTitle() != null ? program.getTitle() : "";

            String createdStr = formatWpTime(createdAt != null ? createdAt : assignedAt);
            String modifiedStr = formatWpTime(assignedAt != null ? assignedAt : createdAt);

            DeviceProgramDTO.Title t = new DeviceProgramDTO.Title();
            t.setRendered(title);

            DeviceProgramDTO.AttachmentUrl attachmentUrl = new DeviceProgramDTO.AttachmentUrl();
            attachmentUrl.setHref(baseUrl + "/wp-json/wp/v2/media?parent=" + programId);

            DeviceProgramDTO.Links links = new DeviceProgramDTO.Links();
            links.setAttachmentUrls(List.of(attachmentUrl));

            list.add(DeviceProgramDTO.builder()
                    .id(programId)
                    .date(createdStr)
                    .dateGmt(createdStr)
                    .modified(modifiedStr)
                    .modifiedGmt(modifiedStr)
                    .type("program")
                    .title(t)
                    .links(links)
                    .build());
        }
        return list;
    }

    private static final DateTimeFormatter WP_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * 格式化时间，兼容 core-service 返回的时间格式。
     * @param time offsetDateTime
     * @return 时间字符串
     */
    private String formatWpTime(OffsetDateTime time) {
        if (time == null) {
            return WP_TIME_FORMAT.format(OffsetDateTime.now(ZoneOffset.UTC));
        }
        return WP_TIME_FORMAT.format(time.withOffsetSameInstant(ZoneOffset.UTC));
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

