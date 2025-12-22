package nan.produced.prism.core.program.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.util.JsonUtils;
import nan.produced.prism.core.program.domain.ProgramReleaseEntity;
import nan.produced.prism.core.program.domain.schedule.ScheduleCommandRuleEntity;
import nan.produced.prism.core.program.domain.schedule.ScheduleContentsRuleEntity;
import nan.produced.prism.core.program.domain.schedule.ScheduleDeviceBindingEntity;
import nan.produced.prism.core.program.domain.schedule.ScheduleEntity;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramReleaseRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ScheduleCommandRuleRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ScheduleContentsRuleRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ScheduleDeviceBindingRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ScheduleRepositoryJpa;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 设备排程分发查询服务（core-service）。
 *
 * <p>对外：供 device-service 适配 /wp-json/wp/v3/schedules。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleDeviceDistributionService {

    private static final String EMPTY_SCHEDULE_JSON = "{\"contentsSchedule\":[],\"commandSchedule\":[]}";

    private final ScheduleDeviceBindingRepositoryJpa scheduleDeviceBindingRepositoryJpa;
    private final ScheduleRepositoryJpa scheduleRepositoryJpa;
    private final ScheduleContentsRuleRepositoryJpa scheduleContentsRuleRepositoryJpa;
    private final ScheduleCommandRuleRepositoryJpa scheduleCommandRuleRepositoryJpa;
    private final ProgramReleaseRepositoryJpa programReleaseRepositoryJpa;

    public String getDeviceScheduleJson(Long deviceId) {
        if (deviceId == null || deviceId <= 0) {
            return EMPTY_SCHEDULE_JSON;
        }

        ScheduleDeviceBindingEntity binding = scheduleDeviceBindingRepositoryJpa.findByDeviceId(deviceId).orElse(null);
        if (binding == null || binding.getScheduleId() == null) {
            return EMPTY_SCHEDULE_JSON;
        }

        ScheduleEntity schedule = scheduleRepositoryJpa.findById(binding.getScheduleId()).orElse(null);
        if (schedule == null || !Boolean.TRUE.equals(schedule.getEnabled())) {
            return EMPTY_SCHEDULE_JSON;
        }

        ObjectMapper objectMapper = JsonUtils.getDefaultObjectMapper();
        ObjectNode root = objectMapper.createObjectNode();

        ArrayNode contents = objectMapper.createArrayNode();
        ArrayNode commands = objectMapper.createArrayNode();

        List<ScheduleContentsRuleEntity> contentRules = scheduleContentsRuleRepositoryJpa.findByScheduleIdOrderByPriorityAsc(binding.getScheduleId());
        Map<Integer, ProgramReleaseEntity> releaseById = loadReleases(contentRules);

        for (ScheduleContentsRuleEntity rule : contentRules) {
            ObjectNode node = toDeviceContentsRule(objectMapper, rule, releaseById);
            if (node != null) {
                contents.add(node);
            }
        }

        List<ScheduleCommandRuleEntity> commandRules = scheduleCommandRuleRepositoryJpa.findByScheduleIdOrderByUpdatedAtDesc(binding.getScheduleId());
        for (ScheduleCommandRuleEntity rule : commandRules) {
            JsonNode node = parseJson(objectMapper, rule != null ? rule.getPayloadJson() : null);
            if (node != null && node.isObject()) {
                commands.add(node);
            }
        }

        root.set("contentsSchedule", contents);
        root.set("commandSchedule", commands);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("ScheduleDeviceDistribution - serialize failed, deviceId={}, scheduleId={}", deviceId, binding.getScheduleId(), e);
            return EMPTY_SCHEDULE_JSON;
        }
    }

    private Map<Integer, ProgramReleaseEntity> loadReleases(List<ScheduleContentsRuleEntity> rules) {
        if (rules == null || rules.isEmpty()) {
            return Map.of();
        }
        List<Integer> ids = rules.stream()
                .map(ScheduleContentsRuleEntity::getReleaseProgramId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Integer, ProgramReleaseEntity> map = new HashMap<>();
        for (ProgramReleaseEntity release : programReleaseRepositoryJpa.findAllById(ids)) {
            if (release == null || release.getDeviceProgramId() == null) {
                continue;
            }
            map.put(release.getDeviceProgramId(), release);
        }
        return map;
    }

    private ObjectNode toDeviceContentsRule(ObjectMapper objectMapper, ScheduleContentsRuleEntity rule, Map<Integer, ProgramReleaseEntity> releaseById) {
        if (objectMapper == null || rule == null || rule.getReleaseProgramId() == null) {
            return null;
        }

        String type = StringUtils.hasText(rule.getType()) ? rule.getType().trim().toLowerCase(Locale.ROOT) : null;
        if (!"rotation".equals(type) && !"spot".equals(type)) {
            return null;
        }

        ProgramReleaseEntity release = releaseById != null ? releaseById.get(rule.getReleaseProgramId()) : null;
        if (release == null || release.getDeviceProgramId() == null) {
            return null;
        }

        String vsn = buildVsnFilename(release);
        if (!StringUtils.hasText(vsn)) {
            return null;
        }

        int typePriority = "spot".equals(type) ? 100 : 200;

        ObjectNode node = objectMapper.createObjectNode();
        node.put("type_priority", typePriority);
        node.put("priority", rule.getPriority() != null ? rule.getPriority() : 0);

        boolean ifLimitTime = Boolean.TRUE.equals(rule.getIfLimitTime());
        node.put("if_limit_time", ifLimitTime);
        if (ifLimitTime) {
            JsonNode limitTime = parseJson(objectMapper, rule.getLimitTime());
            if (limitTime != null && limitTime.isObject()) {
                node.set("limit_time", limitTime);
            }
        }

        boolean ifLimitDate = Boolean.TRUE.equals(rule.getIfLimitDate());
        node.put("if_limit_date", ifLimitDate);
        if (ifLimitDate) {
            JsonNode limitDate = parseJson(objectMapper, rule.getLimitDate());
            if (limitDate != null && limitDate.isObject()) {
                node.set("limit_date", limitDate);
            }
        }

        boolean ifLimitWeekday = Boolean.TRUE.equals(rule.getIfLimitWeekday());
        node.put("if_limit_weekday", ifLimitWeekday);
        if (ifLimitWeekday) {
            JsonNode limitWeekday = parseJson(objectMapper, rule.getLimitWeekday());
            if (limitWeekday != null && limitWeekday.isArray()) {
                node.set("limit_weekday", limitWeekday);
            }
        }

        ObjectNode operation = objectMapper.createObjectNode();
        operation.put("id", release.getDeviceProgramId());
        operation.put("name", release.getDeviceTitleSnapshot() != null ? release.getDeviceTitleSnapshot() : "");
        operation.put("vsn", vsn);
        operation.put("source", "internet");

        node.set("operation", operation);
        node.put("type", type);
        node.put("name", "Play_Program");

        return node;
    }

    private JsonNode parseJson(ObjectMapper objectMapper, String json) {
        if (objectMapper == null || !StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildVsnFilename(ProgramReleaseEntity release) {
        if (release == null) {
            return null;
        }
        if (!StringUtils.hasText(release.getDeviceTitleSnapshot())
                || !StringUtils.hasText(release.getVsnMd5())
                || release.getVsnSizeBytes() == null
                || release.getVsnSizeBytes() <= 0) {
            return null;
        }
        return release.getDeviceTitleSnapshot()
                + "_" + release.getVsnMd5().trim().toLowerCase(Locale.ROOT)
                + "_" + release.getVsnSizeBytes()
                + ".vsn";
    }
}

