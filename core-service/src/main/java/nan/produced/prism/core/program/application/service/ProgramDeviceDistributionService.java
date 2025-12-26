package nan.produced.prism.core.program.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.util.JsonUtils;
import nan.produced.prism.core.media.application.port.outbound.MediaObjectUrlPort;
import nan.produced.prism.core.program.api.dto.internal.InternalDeviceProgramMediaResp;
import nan.produced.prism.core.program.api.dto.internal.InternalDeviceProgramResp;
import nan.produced.prism.core.program.domain.ProgramAssignmentEntity;
import nan.produced.prism.core.program.domain.ProgramReleaseEntity;
import nan.produced.prism.core.program.domain.schedule.ScheduleContentsRuleEntity;
import nan.produced.prism.core.program.domain.schedule.ScheduleDeviceBindingEntity;
import nan.produced.prism.core.program.domain.schedule.ScheduleEntity;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramAssignmentRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramReleaseRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ScheduleContentsRuleRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ScheduleDeviceBindingRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ScheduleRepositoryJpa;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 设备节目分发查询服务（core-service）。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>为 device-service 提供内部查询能力（programs/media）</li>
 *   <li>仅做查询与数据组装，不承载“发布业务”</li>
 * </ul>
 *
 * <p>重要约束：</p>
 * <ul>
 *   <li>device-service 不直连 core DB；此服务是分库后的唯一查询入口</li>
 *   <li>返回给设备的媒体必须是可直接下载的 URL（CDN/对象存储公开地址）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramDeviceDistributionService {

    private final ProgramAssignmentRepositoryJpa programAssignmentRepositoryJpa;
    private final ProgramReleaseRepositoryJpa programReleaseRepositoryJpa;
    private final ScheduleDeviceBindingRepositoryJpa scheduleDeviceBindingRepositoryJpa;
    private final ScheduleRepositoryJpa scheduleRepositoryJpa;
    private final ScheduleContentsRuleRepositoryJpa scheduleContentsRuleRepositoryJpa;
    private final MediaObjectUrlPort mediaObjectUrlPort;

    /**
     * 查询指定设备当前已绑定的节目列表（设备侧节目ID = release.deviceProgramId）。
     */
    public List<InternalDeviceProgramResp> listDevicePrograms(Long deviceId) {
        if (deviceId == null || deviceId <= 0) {
            return List.of();
        }

        Map<Integer, OffsetDateTime> desiredByProgramId = new HashMap<>();

        // 1) direct publish assignments
        List<ProgramAssignmentEntity> assignments = programAssignmentRepositoryJpa.findByDeviceIdOrderByAssignedAtDesc(deviceId);
        if (assignments != null) {
            for (ProgramAssignmentEntity assignment : assignments) {
                if (assignment == null || assignment.getReleaseProgramId() == null) {
                    continue;
                }
                mergeAssignedAt(desiredByProgramId, assignment.getReleaseProgramId(), assignment.getAssignedAt());
            }
        }

        // 2) schedule contents (one schedule per device)
        ScheduleDeviceBindingEntity binding = scheduleDeviceBindingRepositoryJpa.findByDeviceId(deviceId).orElse(null);
        if (binding != null && binding.getScheduleId() != null) {
            ScheduleEntity schedule = scheduleRepositoryJpa.findById(binding.getScheduleId()).orElse(null);
            if (schedule != null && Boolean.TRUE.equals(schedule.getEnabled())) {
                OffsetDateTime scheduleAssignedAt = schedule.getUpdatedAt() != null ? schedule.getUpdatedAt() : schedule.getCreatedAt();
                OffsetDateTime boundAt = binding.getBoundAt();
                if (boundAt != null && (scheduleAssignedAt == null || boundAt.isAfter(scheduleAssignedAt))) {
                    scheduleAssignedAt = boundAt;
                }
                for (ScheduleContentsRuleEntity rule : scheduleContentsRuleRepositoryJpa.findByScheduleIdOrderByPriorityAsc(binding.getScheduleId())) {
                    if (rule == null || rule.getReleaseProgramId() == null) {
                        continue;
                    }
                    mergeAssignedAt(desiredByProgramId, rule.getReleaseProgramId(), scheduleAssignedAt);
                }
            }
        }

        List<Integer> releaseProgramIds = desiredByProgramId.keySet().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (releaseProgramIds.isEmpty()) {
            return List.of();
        }

        Map<Integer, ProgramReleaseEntity> releaseById = new HashMap<>();
        for (ProgramReleaseEntity release : programReleaseRepositoryJpa.findAllById(releaseProgramIds)) {
            if (release == null || release.getDeviceProgramId() == null) {
                continue;
            }
            releaseById.put(release.getDeviceProgramId(), release);
        }

        List<InternalDeviceProgramResp> list = new ArrayList<>();
        for (Integer releaseProgramId : releaseProgramIds) {
            ProgramReleaseEntity release = releaseById.get(releaseProgramId);
            if (release == null) {
                continue;
            }
            OffsetDateTime assignedAt = desiredByProgramId.get(releaseProgramId);
            list.add(InternalDeviceProgramResp.builder()
                    .deviceProgramId(release.getDeviceProgramId())
                    .title(release.getDeviceTitleSnapshot())
                    .createdAt(release.getCreatedAt())
                    .assignedAt(assignedAt)
                    .build());
        }

        // Align with old behavior (assignedAt desc)
        list.sort((a, b) -> {
            OffsetDateTime atA = a != null ? a.getAssignedAt() : null;
            OffsetDateTime atB = b != null ? b.getAssignedAt() : null;
            if (atA == null && atB == null) return 0;
            if (atA == null) return 1;
            if (atB == null) return -1;
            return atB.compareTo(atA);
        });

        return list;
    }

    /**
     * 查询指定设备对某个设备节目（deviceProgramId/parent）的媒体下载清单。
     *
     * <p>这里会校验该设备与该节目是否存在 deployment 绑定关系；无绑定则返回空。</p>
     */
    public List<InternalDeviceProgramMediaResp> listDeviceProgramMedia(Long deviceId, Integer deviceProgramId) {
        if (deviceId == null || deviceId <= 0 || deviceProgramId == null || deviceProgramId <= 0) {
            return List.of();
        }

        boolean allowed = programAssignmentRepositoryJpa.findByDeviceIdAndReleaseProgramId(deviceId, deviceProgramId).isPresent();
        if (!allowed) {
            ScheduleDeviceBindingEntity binding = scheduleDeviceBindingRepositoryJpa.findByDeviceId(deviceId).orElse(null);
            if (binding != null && binding.getScheduleId() != null) {
                ScheduleEntity schedule = scheduleRepositoryJpa.findById(binding.getScheduleId()).orElse(null);
                if (schedule != null && Boolean.TRUE.equals(schedule.getEnabled())) {
                    allowed = scheduleContentsRuleRepositoryJpa.existsByScheduleIdAndReleaseProgramId(binding.getScheduleId(), deviceProgramId);
                }
            }
        }
        if (!allowed) {
            // device-service 可能会因固件缓存等原因重复拉取，返回空比抛错更友好
            return List.of();
        }

        ProgramReleaseEntity release = programReleaseRepositoryJpa.findById(deviceProgramId).orElse(null);
        if (release == null || !StringUtils.hasText(release.getManifestJson())) {
            return List.of();
        }

        JsonNode manifest;
        try {
            manifest = JsonUtils.getDefaultObjectMapper().readTree(release.getManifestJson());
        } catch (Exception e) {
            log.warn("ProgramDeviceDistribution - manifest_json parse failed, deviceId={}, deviceProgramId={}", deviceId, deviceProgramId, e);
            return List.of();
        }

        List<InternalDeviceProgramMediaResp> list = new ArrayList<>();

        // 约定：manifest.vsn + manifest.materials[]
        JsonNode vsn = manifest.get("vsn");
        addManifestFile(list, vsn);

        JsonNode materials = manifest.get("materials");
        if (materials != null && materials.isArray()) {
            for (JsonNode item : materials) {
                addManifestFile(list, item);
            }
        }

        return list;
    }

    private void mergeAssignedAt(Map<Integer, OffsetDateTime> out, Integer deviceProgramId, OffsetDateTime assignedAt) {
        if (out == null || deviceProgramId == null) {
            return;
        }
        OffsetDateTime current = out.get(deviceProgramId);
        if (current == null) {
            out.put(deviceProgramId, assignedAt);
            return;
        }
        if (assignedAt != null && assignedAt.isAfter(current)) {
            out.put(deviceProgramId, assignedAt);
        }
    }

    /**
     * 将 manifest 里的文件条目转为设备可直接下载的 URL。
     *
     * <p>manifest 结构来自发布时固化的快照；核心字段：</p>
     * <ul>
     *   <li>objectKey：对象存储 key</li>
     *   <li>sizeBytes：文件大小（设备端需要）</li>
     * </ul>
     */
    private void addManifestFile(List<InternalDeviceProgramMediaResp> out, JsonNode node) {
        if (out == null || node == null || !node.isObject()) {
            return;
        }
        String objectKey = text(node, "objectKey");
        long sizeBytes = longValue(node, "sizeBytes");
        if (!StringUtils.hasText(objectKey) || sizeBytes <= 0) {
            return;
        }

        String url = mediaObjectUrlPort.toPublicUrl(objectKey);
        if (!StringUtils.hasText(url)) {
            return;
        }
        out.add(InternalDeviceProgramMediaResp.builder()
                .url(url)
                .sizeBytes(sizeBytes)
                .build());
    }

    private String text(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private long longValue(JsonNode node, String field) {
        if (node == null || field == null) {
            return 0L;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return 0L;
        }
        if (value.isNumber()) {
            return value.longValue();
        }
        try {
            return Long.parseLong(value.asText());
        } catch (Exception ignore) {
            return 0L;
        }
    }
}
