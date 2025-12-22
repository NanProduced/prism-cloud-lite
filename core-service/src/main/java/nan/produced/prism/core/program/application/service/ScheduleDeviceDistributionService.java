package nan.produced.prism.core.program.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.util.JsonUtils;
import nan.produced.prism.core.program.api.dto.internal.InternalDeviceScheduleCommandRuleResp;
import nan.produced.prism.core.program.api.dto.internal.InternalDeviceScheduleContentsOperationResp;
import nan.produced.prism.core.program.api.dto.internal.InternalDeviceScheduleContentsRuleResp;
import nan.produced.prism.core.program.api.dto.internal.InternalDeviceSchedulesResp;
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
    private static final InternalDeviceSchedulesResp EMPTY_SCHEDULE =
            InternalDeviceSchedulesResp.builder().contentsSchedule(List.of()).commandSchedule(List.of()).build();

    private final ScheduleDeviceBindingRepositoryJpa scheduleDeviceBindingRepositoryJpa;
    private final ScheduleRepositoryJpa scheduleRepositoryJpa;
    private final ScheduleContentsRuleRepositoryJpa scheduleContentsRuleRepositoryJpa;
    private final ScheduleCommandRuleRepositoryJpa scheduleCommandRuleRepositoryJpa;
    private final ProgramReleaseRepositoryJpa programReleaseRepositoryJpa;

    public String getDeviceScheduleJson(Long deviceId) {
        InternalDeviceSchedulesResp resp = getDeviceSchedules(deviceId);
        try {
            return JsonUtils.getDefaultObjectMapper().writeValueAsString(resp);
        } catch (Exception e) {
            log.warn("ScheduleDeviceDistribution - serialize failed, deviceId={}", deviceId, e);
            return EMPTY_SCHEDULE_JSON;
        }
    }

    public InternalDeviceSchedulesResp getDeviceSchedules(Long deviceId) {
        if (deviceId == null || deviceId <= 0) {
            return EMPTY_SCHEDULE;
        }

        ScheduleDeviceBindingEntity binding = scheduleDeviceBindingRepositoryJpa.findByDeviceId(deviceId).orElse(null);
        if (binding == null || binding.getScheduleId() == null) {
            return EMPTY_SCHEDULE;
        }

        ScheduleEntity schedule = scheduleRepositoryJpa.findById(binding.getScheduleId()).orElse(null);
        if (schedule == null || !Boolean.TRUE.equals(schedule.getEnabled())) {
            return EMPTY_SCHEDULE;
        }

        ObjectMapper objectMapper = JsonUtils.getDefaultObjectMapper();
        List<InternalDeviceScheduleContentsRuleResp> contents = new ArrayList<>();
        List<InternalDeviceScheduleCommandRuleResp> commands = new ArrayList<>();

        List<ScheduleContentsRuleEntity> contentRules = scheduleContentsRuleRepositoryJpa.findByScheduleIdOrderByPriorityAsc(binding.getScheduleId());
        Map<Integer, ProgramReleaseEntity> releaseById = loadReleases(contentRules);

        for (ScheduleContentsRuleEntity rule : contentRules) {
            InternalDeviceScheduleContentsRuleResp resp = toDeviceContentsRule(objectMapper, rule, releaseById);
            if (resp != null) {
                contents.add(resp);
            }
        }

        List<ScheduleCommandRuleEntity> commandRules = scheduleCommandRuleRepositoryJpa.findByScheduleIdOrderByUpdatedAtDesc(binding.getScheduleId());
        for (ScheduleCommandRuleEntity rule : commandRules) {
            InternalDeviceScheduleCommandRuleResp cmd = parseCommandRule(objectMapper, rule != null ? rule.getPayloadJson() : null);
            if (cmd != null) {
                commands.add(cmd);
            }
        }

        return InternalDeviceSchedulesResp.builder()
                .contentsSchedule(contents)
                .commandSchedule(commands)
                .build();
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

    private InternalDeviceScheduleContentsRuleResp toDeviceContentsRule(
            ObjectMapper objectMapper,
            ScheduleContentsRuleEntity rule,
            Map<Integer, ProgramReleaseEntity> releaseById) {
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

        boolean ifLimitTime = Boolean.TRUE.equals(rule.getIfLimitTime());
        JsonNode limitTime = null;
        if (ifLimitTime) {
            JsonNode node = parseJson(objectMapper, rule.getLimitTime());
            limitTime = node != null && node.isObject() ? node : null;
        }

        boolean ifLimitDate = Boolean.TRUE.equals(rule.getIfLimitDate());
        JsonNode limitDate = null;
        if (ifLimitDate) {
            JsonNode node = parseJson(objectMapper, rule.getLimitDate());
            limitDate = node != null && node.isObject() ? node : null;
        }

        boolean ifLimitWeekday = Boolean.TRUE.equals(rule.getIfLimitWeekday());
        JsonNode limitWeekday = null;
        if (ifLimitWeekday) {
            JsonNode node = parseJson(objectMapper, rule.getLimitWeekday());
            limitWeekday = node != null && node.isArray() ? node : null;
        }

        InternalDeviceScheduleContentsOperationResp operation = InternalDeviceScheduleContentsOperationResp.builder()
                .id(release.getDeviceProgramId())
                .name(release.getDeviceTitleSnapshot() != null ? release.getDeviceTitleSnapshot() : "")
                .vsn(vsn)
                .source("internet")
                .build();

        return InternalDeviceScheduleContentsRuleResp.builder()
                .typePriority(typePriority)
                .priority(rule.getPriority() != null ? rule.getPriority() : 0)
                .ifLimitTime(ifLimitTime)
                .limitTime(limitTime)
                .ifLimitDate(ifLimitDate)
                .limitDate(limitDate)
                .ifLimitWeekday(ifLimitWeekday)
                .limitWeekday(limitWeekday)
                .operation(operation)
                .type(type)
                .name("Play_Program")
                .build();
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

    private InternalDeviceScheduleCommandRuleResp parseCommandRule(ObjectMapper objectMapper, String json) {
        if (objectMapper == null || !StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, InternalDeviceScheduleCommandRuleResp.class);
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
