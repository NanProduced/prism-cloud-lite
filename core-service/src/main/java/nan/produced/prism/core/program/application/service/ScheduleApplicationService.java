package nan.produced.prism.core.program.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.common.util.IdGenerator;
import nan.produced.prism.core.common.util.JsonUtils;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.integration.device.client.DeviceInternalClient;
import nan.produced.prism.core.integration.device.dto.command.DeviceCommandReq;
import nan.produced.prism.core.integration.device.dto.command.DeviceCommandResp;
import nan.produced.prism.core.program.api.dto.schedule.CreateScheduleReq;
import nan.produced.prism.core.program.api.dto.schedule.DeviceProgramAllowlistResp;
import nan.produced.prism.core.program.api.dto.schedule.DeviceScheduleResp;
import nan.produced.prism.core.program.api.dto.schedule.ScheduleBindDevicesReq;
import nan.produced.prism.core.program.api.dto.schedule.ScheduleBindDevicesResp;
import nan.produced.prism.core.program.api.dto.schedule.ScheduleBindDevicesResultResp;
import nan.produced.prism.core.program.api.dto.schedule.ScheduleBindingDeviceResp;
import nan.produced.prism.core.program.api.dto.schedule.ScheduleCommandRuleResp;
import nan.produced.prism.core.program.api.dto.schedule.ScheduleContentsRuleResp;
import nan.produced.prism.core.program.api.dto.schedule.ScheduleDetailResp;
import nan.produced.prism.core.program.api.dto.schedule.ScheduleListResp;
import nan.produced.prism.core.program.api.dto.schedule.SchedulePushReq;
import nan.produced.prism.core.program.api.dto.schedule.SchedulePushResp;
import nan.produced.prism.core.program.api.dto.schedule.SchedulePushResultResp;
import nan.produced.prism.core.program.api.dto.schedule.UpdateScheduleReq;
import nan.produced.prism.core.program.api.dto.schedule.UpsertScheduleCommandRuleReq;
import nan.produced.prism.core.program.api.dto.schedule.UpsertScheduleContentsRuleReq;
import nan.produced.prism.core.program.domain.ProgramAssignmentEntity;
import nan.produced.prism.core.program.domain.ProgramEntity;
import nan.produced.prism.core.program.domain.ProgramDeploymentEntity;
import nan.produced.prism.core.program.domain.ProgramReleaseEntity;
import nan.produced.prism.core.program.domain.device.DeviceBasicEntity;
import nan.produced.prism.core.program.domain.schedule.ScheduleCommandRuleEntity;
import nan.produced.prism.core.program.domain.schedule.ScheduleContentsRuleEntity;
import nan.produced.prism.core.program.domain.schedule.ScheduleDeviceBindingEntity;
import nan.produced.prism.core.program.domain.schedule.ScheduleEntity;
import nan.produced.prism.core.program.infrastructure.persistence.DeviceBasicRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramAssignmentRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramDeploymentRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramReleaseRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ScheduleCommandRuleRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ScheduleContentsRuleRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ScheduleDeviceBindingRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ScheduleRepositoryJpa;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleApplicationService {

    private static final String SCHEDULE_COMMAND_RAW = "{\"program\":\"schedule\"}";

    private final ScheduleRepositoryJpa scheduleRepositoryJpa;
    private final ScheduleContentsRuleRepositoryJpa scheduleContentsRuleRepositoryJpa;
    private final ScheduleCommandRuleRepositoryJpa scheduleCommandRuleRepositoryJpa;
    private final ScheduleDeviceBindingRepositoryJpa scheduleDeviceBindingRepositoryJpa;
    private final ProgramAssignmentRepositoryJpa programAssignmentRepositoryJpa;
    private final ProgramDeploymentRepositoryJpa programDeploymentRepositoryJpa;
    private final ProgramReleaseRepositoryJpa programReleaseRepositoryJpa;
    private final ProgramRepositoryJpa programRepositoryJpa;
    private final DeviceBasicRepositoryJpa deviceBasicRepositoryJpa;
    private final DeviceInternalClient deviceInternalClient;
    private final ScheduleDeviceDistributionService scheduleDeviceDistributionService;

    @Transactional(readOnly = true)
    public List<ScheduleListResp> listSchedules(UUID userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }

        List<ScheduleEntity> schedules = scheduleRepositoryJpa.findByUserIdOrderByUpdatedAtDesc(userId);
        if (schedules == null || schedules.isEmpty()) {
            return List.of();
        }

        List<ScheduleListResp> list = new ArrayList<>();
        for (ScheduleEntity schedule : schedules) {
            if (schedule == null || schedule.getScheduleId() == null) {
                continue;
            }

            UUID scheduleId = schedule.getScheduleId();

            int boundDevices = scheduleDeviceBindingRepositoryJpa.findByScheduleId(scheduleId).size();
            int programRules = scheduleContentsRuleRepositoryJpa.findByScheduleIdOrderByPriorityAsc(scheduleId).size();
            int commandRules = scheduleCommandRuleRepositoryJpa.findByScheduleIdOrderByUpdatedAtDesc(scheduleId).size();

            list.add(ScheduleListResp.builder()
                    .scheduleId(scheduleId)
                    .name(schedule.getName())
                    .description(schedule.getDescription())
                    .enabled(schedule.getEnabled())
                    .boundDevices(boundDevices)
                    .programRules(programRules)
                    .commandRules(commandRules)
                    .createdAt(schedule.getCreatedAt())
                    .updatedAt(schedule.getUpdatedAt())
                    .build());
        }

        return list;
    }

    @Transactional
    public ScheduleDetailResp createSchedule(UUID userId, CreateScheduleReq req) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (req == null || !StringUtils.hasText(req.getName())) {
            throw new BizException(ErrorCode.SCHEDULE_NAME_REQUIRED);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ScheduleEntity schedule = ScheduleEntity.builder()
                .scheduleId(UUID.randomUUID())
                .userId(userId)
                .name(req.getName().trim())
                .description(StringUtils.hasText(req.getDescription()) ? req.getDescription().trim() : null)
                .enabled(req.getEnabled() != null ? req.getEnabled() : Boolean.TRUE)
                .createdAt(now)
                .updatedAt(now)
                .build();

        scheduleRepositoryJpa.save(schedule);

        if (req.getContentsRules() != null) {
            replaceContentsRules(userId, schedule.getScheduleId(), req.getContentsRules(), now);
        }
        if (req.getCommandRules() != null) {
            replaceCommandRules(userId, schedule.getScheduleId(), req.getCommandRules(), now);
        }

        return getScheduleDetail(userId, schedule.getScheduleId());
    }

    @Transactional(readOnly = true)
    public ScheduleDetailResp getScheduleDetail(UUID userId, UUID scheduleId) {
        ScheduleEntity schedule = findOwnedSchedule(userId, scheduleId);

        List<Long> boundDeviceIds = scheduleDeviceBindingRepositoryJpa.findByScheduleId(scheduleId).stream()
                .map(ScheduleDeviceBindingEntity::getDeviceId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();

        List<ScheduleContentsRuleEntity> contents = scheduleContentsRuleRepositoryJpa.findByScheduleIdOrderByPriorityAsc(scheduleId);
        Map<Integer, ProgramReleaseEntity> releaseById = loadReleases(contents);
        Map<UUID, ProgramEntity> programById = loadPrograms(releaseById.values());

        List<ScheduleContentsRuleResp> contentsRules = new ArrayList<>();
        for (ScheduleContentsRuleEntity rule : contents) {
            if (rule == null) {
                continue;
            }
            ProgramReleaseEntity release = rule.getReleaseProgramId() != null ? releaseById.get(rule.getReleaseProgramId()) : null;
            UUID programId = release != null ? release.getProgramId() : null;
            ProgramEntity program = programId != null ? programById.get(programId) : null;

            contentsRules.add(ScheduleContentsRuleResp.builder()
                    .id(rule.getId())
                    .scheduleId(rule.getScheduleId())
                    .type(rule.getType())
                    .priority(rule.getPriority())
                    .releaseProgramId(rule.getReleaseProgramId())
                    .programId(program != null ? program.getId() : programId)
                    .releaseVersion(release != null ? release.getVersion() : null)
                    .deviceTitleSnapshot(release != null ? release.getDeviceTitleSnapshot() : null)
                    .ifLimitTime(rule.getIfLimitTime())
                    .limitTime(parseJson(rule.getLimitTime()))
                    .ifLimitDate(rule.getIfLimitDate())
                    .limitDate(parseJson(rule.getLimitDate()))
                    .ifLimitWeekday(rule.getIfLimitWeekday())
                    .limitWeekday(parseJson(rule.getLimitWeekday()))
                    .createdAt(rule.getCreatedAt())
                    .updatedAt(rule.getUpdatedAt())
                    .build());
        }

        List<ScheduleCommandRuleResp> commandRules = new ArrayList<>();
        for (ScheduleCommandRuleEntity rule : scheduleCommandRuleRepositoryJpa.findByScheduleIdOrderByUpdatedAtDesc(scheduleId)) {
            if (rule == null) {
                continue;
            }
            commandRules.add(ScheduleCommandRuleResp.builder()
                    .id(rule.getId())
                    .scheduleId(rule.getScheduleId())
                    .payload(parseJson(rule.getPayloadJson()))
                    .createdAt(rule.getCreatedAt())
                    .updatedAt(rule.getUpdatedAt())
                    .build());
        }

        return ScheduleDetailResp.builder()
                .scheduleId(schedule.getScheduleId())
                .name(schedule.getName())
                .description(schedule.getDescription())
                .enabled(schedule.getEnabled())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .boundDeviceIds(boundDeviceIds)
                .contentsRules(contentsRules)
                .commandRules(commandRules)
                .build();
    }

    @Transactional
    public ScheduleDetailResp updateSchedule(UUID userId, UUID scheduleId, UpdateScheduleReq req) {
        ScheduleEntity schedule = findOwnedSchedule(userId, scheduleId);
        if (req == null) {
            return getScheduleDetail(userId, scheduleId);
        }

        boolean changed = false;
        if (req.getName() != null) {
            if (!StringUtils.hasText(req.getName())) {
                throw new BizException(ErrorCode.SCHEDULE_NAME_REQUIRED);
            }
            schedule.setName(req.getName().trim());
            changed = true;
        }

        if (req.getDescription() != null) {
            schedule.setDescription(StringUtils.hasText(req.getDescription()) ? req.getDescription().trim() : null);
            changed = true;
        }

        if (req.getEnabled() != null) {
            schedule.setEnabled(req.getEnabled());
            changed = true;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (req.getContentsRules() != null) {
            replaceContentsRules(userId, scheduleId, req.getContentsRules(), now);
            changed = true;
        }

        if (req.getCommandRules() != null) {
            replaceCommandRules(userId, scheduleId, req.getCommandRules(), now);
            changed = true;
        }

        if (changed) {
            schedule.setUpdatedAt(now);
            scheduleRepositoryJpa.save(schedule);
        }

        return getScheduleDetail(userId, scheduleId);
    }

    @Transactional
    public void deleteSchedule(UUID userId, UUID scheduleId) {
        ScheduleEntity schedule = findOwnedSchedule(userId, scheduleId);
        scheduleRepositoryJpa.delete(schedule);
    }

    @Transactional(readOnly = true)
    public List<ScheduleBindingDeviceResp> listBindings(UUID userId, UUID scheduleId) {
        findOwnedSchedule(userId, scheduleId);

        List<ScheduleDeviceBindingEntity> bindings = scheduleDeviceBindingRepositoryJpa.findByScheduleId(scheduleId);
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }

        List<ScheduleBindingDeviceResp> list = new ArrayList<>();
        for (ScheduleDeviceBindingEntity binding : bindings) {
            if (binding == null || binding.getDeviceId() == null || binding.getDeviceId() <= 0) {
                continue;
            }
            DeviceBasicEntity device = deviceBasicRepositoryJpa.findByDeviceIdAndUserId(binding.getDeviceId(), userId).orElse(null);
            list.add(ScheduleBindingDeviceResp.builder()
                    .deviceId(binding.getDeviceId())
                    .deviceName(device != null ? device.getDeviceName() : null)
                    .onlineStatus(device != null ? device.getOnlineStatus() : null)
                    .boundAt(binding.getBoundAt())
                    .build());
        }

        list.sort((a, b) -> {
            OffsetDateTime atA = a != null ? a.getBoundAt() : null;
            OffsetDateTime atB = b != null ? b.getBoundAt() : null;
            if (atA == null && atB == null) return 0;
            if (atA == null) return 1;
            if (atB == null) return -1;
            return atB.compareTo(atA);
        });

        return list;
    }

    @Transactional
    public ScheduleBindDevicesResp bindDevices(UUID userId, UUID scheduleId, ScheduleBindDevicesReq req) {
        findOwnedSchedule(userId, scheduleId);
        if (req == null || req.getDeviceIds() == null || req.getDeviceIds().isEmpty()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "deviceIds is required");
        }

        boolean replaceExisting = Boolean.TRUE.equals(req.getReplaceExisting());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Set<Long> deviceIds = new LinkedHashSet<>();
        for (Long deviceId : req.getDeviceIds()) {
            if (deviceId == null || deviceId <= 0) {
                continue;
            }
            deviceIds.add(deviceId);
        }

        List<ScheduleBindDevicesResultResp> results = new ArrayList<>();
        int bound = 0;
        int conflicts = 0;

        for (Long deviceId : deviceIds) {
            DeviceBasicEntity device = deviceBasicRepositoryJpa.findByDeviceIdAndUserId(deviceId, userId).orElse(null);
            if (device == null) {
                results.add(ScheduleBindDevicesResultResp.builder()
                        .deviceId(deviceId)
                        .status("skip")
                        .build());
                continue;
            }

            ScheduleDeviceBindingEntity existing = scheduleDeviceBindingRepositoryJpa.findByDeviceId(deviceId).orElse(null);
            if (existing == null) {
                ScheduleDeviceBindingEntity binding = ScheduleDeviceBindingEntity.builder()
                        .deviceId(deviceId)
                        .scheduleId(scheduleId)
                        .userId(userId)
                        .boundAt(now)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                scheduleDeviceBindingRepositoryJpa.save(binding);
                bound++;
                results.add(ScheduleBindDevicesResultResp.builder()
                        .deviceId(deviceId)
                        .status("bound")
                        .build());
                continue;
            }

            if (scheduleId.equals(existing.getScheduleId())) {
                results.add(ScheduleBindDevicesResultResp.builder()
                        .deviceId(deviceId)
                        .status("no-change")
                        .build());
                continue;
            }

            if (!replaceExisting) {
                conflicts++;
                results.add(ScheduleBindDevicesResultResp.builder()
                        .deviceId(deviceId)
                        .status("conflict")
                        .previousScheduleId(existing.getScheduleId())
                        .build());
                continue;
            }

            UUID previousScheduleId = existing.getScheduleId();
            existing.setScheduleId(scheduleId);
            existing.setUserId(userId);
            existing.setBoundAt(now);
            existing.setUpdatedAt(now);
            if (existing.getCreatedAt() == null) {
                existing.setCreatedAt(now);
            }
            scheduleDeviceBindingRepositoryJpa.save(existing);

            bound++;
            results.add(ScheduleBindDevicesResultResp.builder()
                    .deviceId(deviceId)
                    .status("bound")
                    .previousScheduleId(previousScheduleId)
                    .build());
        }

        return ScheduleBindDevicesResp.builder()
                .totalTargets(deviceIds.size())
                .bound(bound)
                .conflicts(conflicts)
                .results(results)
                .build();
    }

    @Transactional
    public void unbindDevice(UUID userId, UUID scheduleId, Long deviceId) {
        findOwnedSchedule(userId, scheduleId);

        if (deviceId == null || deviceId <= 0) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "deviceId is required");
        }

        DeviceBasicEntity device = deviceBasicRepositoryJpa.findByDeviceIdAndUserId(deviceId, userId).orElse(null);
        if (device == null) {
            throw new BizException(ErrorCode.DEVICE_NOT_FOUND_IN_CORE);
        }

        ScheduleDeviceBindingEntity binding = scheduleDeviceBindingRepositoryJpa.findByDeviceId(deviceId).orElse(null);
        if (binding == null) {
            return;
        }
        if (!userId.equals(binding.getUserId())) {
            throw new BizException(ErrorCode.DEVICE_NOT_FOUND_IN_CORE);
        }
        if (!scheduleId.equals(binding.getScheduleId())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "device is bound to another schedule");
        }

        scheduleDeviceBindingRepositoryJpa.delete(binding);
    }

    @Transactional
    public SchedulePushResp pushSchedule(UUID userId, UUID scheduleId, SchedulePushReq req) {
        findOwnedSchedule(userId, scheduleId);

        List<Long> boundDeviceIds = scheduleDeviceBindingRepositoryJpa.findByScheduleId(scheduleId).stream()
                .map(ScheduleDeviceBindingEntity::getDeviceId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        Set<Long> boundSet = new HashSet<>(boundDeviceIds);

        List<Long> requestedDeviceIds;
        if (req != null && req.getDeviceIds() != null && !req.getDeviceIds().isEmpty()) {
            Set<Long> unique = new LinkedHashSet<>();
            for (Long id : req.getDeviceIds()) {
                if (id == null || id <= 0) {
                    continue;
                }
                unique.add(id);
            }
            requestedDeviceIds = new ArrayList<>(unique);
        } else {
            requestedDeviceIds = boundDeviceIds;
        }

        if (requestedDeviceIds.isEmpty()) {
            return SchedulePushResp.builder()
                    .totalTargets(0)
                    .accepted(0)
                    .results(List.of())
                    .build();
        }

        List<DeviceCommandReq> commands = new ArrayList<>();
        List<SchedulePushResultResp> results = new ArrayList<>();
        Map<String, SchedulePushResultResp> resultByCommandId = new HashMap<>();

        for (Long deviceId : requestedDeviceIds) {
            if (!boundSet.contains(deviceId)) {
                results.add(SchedulePushResultResp.builder()
                        .deviceId(deviceId)
                        .accepted(false)
                        .errorMessage("device is not bound to this schedule")
                        .build());
                continue;
            }

            String commandId = UUID.randomUUID().toString();
            DeviceCommandReq command = DeviceCommandReq.builder()
                    .deviceId(deviceId)
                    .commandId(commandId)
                    .authorUrl("")
                    .karma(0)
                    .content(DeviceCommandReq.Content.builder().raw(SCHEDULE_COMMAND_RAW).build())
                    .build();
            commands.add(command);

            SchedulePushResultResp r = SchedulePushResultResp.builder()
                    .deviceId(deviceId)
                    .commandId(commandId)
                    .accepted(false)
                    .build();
            results.add(r);
            resultByCommandId.put(commandId, r);
        }

        int accepted = 0;
        if (!commands.isEmpty()) {
            DeviceCommandResp resp = callDeviceService(commands);
            accepted = resp.getAccepted();
            if (resp.getResults() != null) {
                for (DeviceCommandResp.CommandResult cr : resp.getResults()) {
                    if (cr == null || cr.getCommandId() == null) {
                        continue;
                    }
                    SchedulePushResultResp r = resultByCommandId.get(cr.getCommandId());
                    if (r == null) {
                        continue;
                    }
                    r.setAccepted(cr.isAccepted());
                    r.setQueuedId(cr.getQueuedId());
                    r.setErrorMessage(cr.getErrorMessage());
                }
            }
        }

        return SchedulePushResp.builder()
                .totalTargets(requestedDeviceIds.size())
                .accepted(accepted)
                .results(results)
                .build();
    }

    @Transactional(readOnly = true)
    public DeviceScheduleResp getDeviceSchedule(UUID userId, Long deviceId) {
        requireOwnedDevice(userId, deviceId);

        ScheduleDeviceBindingEntity binding = scheduleDeviceBindingRepositoryJpa.findByDeviceId(deviceId).orElse(null);
        if (binding == null || binding.getScheduleId() == null) {
            return DeviceScheduleResp.builder()
                    .deviceId(deviceId)
                    .programRulesCount(0)
                    .commandRulesCount(0)
                    .contentsRules(List.of())
                    .commandRules(List.of())
                    .build();
        }

        ScheduleEntity schedule = scheduleRepositoryJpa.findByScheduleIdAndUserId(binding.getScheduleId(), userId).orElse(null);
        if (schedule == null) {
            return DeviceScheduleResp.builder()
                    .deviceId(deviceId)
                    .scheduleId(binding.getScheduleId())
                    .boundAt(binding.getBoundAt())
                    .programRulesCount(0)
                    .commandRulesCount(0)
                    .contentsRules(List.of())
                    .commandRules(List.of())
                    .build();
        }

        UUID scheduleId = schedule.getScheduleId();

        List<ScheduleContentsRuleEntity> contents = scheduleContentsRuleRepositoryJpa.findByScheduleIdOrderByPriorityAsc(scheduleId);
        Map<Integer, ProgramReleaseEntity> releaseById = loadReleases(contents);
        Map<UUID, ProgramEntity> programById = loadPrograms(releaseById.values());

        List<ScheduleContentsRuleResp> contentsRules = new ArrayList<>();
        for (ScheduleContentsRuleEntity rule : contents) {
            if (rule == null) {
                continue;
            }
            ProgramReleaseEntity release = rule.getReleaseProgramId() != null ? releaseById.get(rule.getReleaseProgramId()) : null;
            UUID programId = release != null ? release.getProgramId() : null;
            ProgramEntity program = programId != null ? programById.get(programId) : null;

            contentsRules.add(ScheduleContentsRuleResp.builder()
                    .id(rule.getId())
                    .scheduleId(rule.getScheduleId())
                    .type(rule.getType())
                    .priority(rule.getPriority())
                    .releaseProgramId(rule.getReleaseProgramId())
                    .programId(program != null ? program.getId() : programId)
                    .releaseVersion(release != null ? release.getVersion() : null)
                    .deviceTitleSnapshot(release != null ? release.getDeviceTitleSnapshot() : null)
                    .ifLimitTime(rule.getIfLimitTime())
                    .limitTime(parseJson(rule.getLimitTime()))
                    .ifLimitDate(rule.getIfLimitDate())
                    .limitDate(parseJson(rule.getLimitDate()))
                    .ifLimitWeekday(rule.getIfLimitWeekday())
                    .limitWeekday(parseJson(rule.getLimitWeekday()))
                    .createdAt(rule.getCreatedAt())
                    .updatedAt(rule.getUpdatedAt())
                    .build());
        }

        List<ScheduleCommandRuleResp> commandRules = new ArrayList<>();
        for (ScheduleCommandRuleEntity rule : scheduleCommandRuleRepositoryJpa.findByScheduleIdOrderByUpdatedAtDesc(scheduleId)) {
            if (rule == null) {
                continue;
            }
            commandRules.add(ScheduleCommandRuleResp.builder()
                    .id(rule.getId())
                    .scheduleId(rule.getScheduleId())
                    .payload(parseJson(rule.getPayloadJson()))
                    .createdAt(rule.getCreatedAt())
                    .updatedAt(rule.getUpdatedAt())
                    .build());
        }

        return DeviceScheduleResp.builder()
                .deviceId(deviceId)
                .scheduleId(schedule.getScheduleId())
                .scheduleName(schedule.getName())
                .scheduleDescription(schedule.getDescription())
                .scheduleEnabled(schedule.getEnabled())
                .scheduleCreatedAt(schedule.getCreatedAt())
                .scheduleUpdatedAt(schedule.getUpdatedAt())
                .boundAt(binding.getBoundAt())
                .programRulesCount(contentsRules.size())
                .commandRulesCount(commandRules.size())
                .contentsRules(contentsRules)
                .commandRules(commandRules)
                .build();
    }

    @Transactional(readOnly = true)
    public JsonNode getDeviceScheduleJson(UUID userId, Long deviceId) {
        requireOwnedDevice(userId, deviceId);
        String json = scheduleDeviceDistributionService.getDeviceScheduleJson(deviceId);
        JsonNode node = parseJson(json);
        if (node != null && node.isObject()) {
            return node;
        }
        ObjectMapper mapper = JsonUtils.getDefaultObjectMapper();
        ObjectNode empty = mapper.createObjectNode();
        empty.set("contentsSchedule", mapper.createArrayNode());
        empty.set("commandSchedule", mapper.createArrayNode());
        return empty;
    }

    @Transactional(readOnly = true)
    public List<DeviceProgramAllowlistResp> listDeviceProgramAllowlist(UUID userId, Long deviceId) {
        requireOwnedDevice(userId, deviceId);

        Map<Integer, AllowlistAggregate> aggregateByReleaseProgramId = new HashMap<>();

        List<ProgramAssignmentEntity> assignments = programAssignmentRepositoryJpa.findByDeviceIdOrderByAssignedAtDesc(deviceId);
        if (assignments != null) {
            for (ProgramAssignmentEntity assignment : assignments) {
                if (assignment == null || assignment.getReleaseProgramId() == null) {
                    continue;
                }
                AllowlistAggregate agg = aggregateByReleaseProgramId.computeIfAbsent(assignment.getReleaseProgramId(), k -> new AllowlistAggregate());
                agg.directPublish = true;
                agg.assignedAt = max(agg.assignedAt, assignment.getAssignedAt());
            }
        }

        UUID boundScheduleId = null;
        OffsetDateTime scheduleAssignedAt = null;
        ScheduleDeviceBindingEntity binding = scheduleDeviceBindingRepositoryJpa.findByDeviceId(deviceId).orElse(null);
        if (binding != null && binding.getScheduleId() != null && userId.equals(binding.getUserId())) {
            ScheduleEntity schedule = scheduleRepositoryJpa.findByScheduleIdAndUserId(binding.getScheduleId(), userId).orElse(null);
            if (schedule != null && Boolean.TRUE.equals(schedule.getEnabled())) {
                boundScheduleId = schedule.getScheduleId();
                scheduleAssignedAt = schedule.getUpdatedAt() != null ? schedule.getUpdatedAt() : schedule.getCreatedAt();
                for (ScheduleContentsRuleEntity rule : scheduleContentsRuleRepositoryJpa.findByScheduleIdOrderByPriorityAsc(boundScheduleId)) {
                    if (rule == null || rule.getReleaseProgramId() == null) {
                        continue;
                    }
                    AllowlistAggregate agg = aggregateByReleaseProgramId.computeIfAbsent(rule.getReleaseProgramId(), k -> new AllowlistAggregate());
                    agg.schedule = true;
                    agg.assignedAt = max(agg.assignedAt, scheduleAssignedAt);
                }
            }
        }

        if (aggregateByReleaseProgramId.isEmpty()) {
            return List.of();
        }

        Map<Integer, ProgramReleaseEntity> releaseById = new HashMap<>();
        for (ProgramReleaseEntity release : programReleaseRepositoryJpa.findAllById(aggregateByReleaseProgramId.keySet())) {
            if (release == null || release.getDeviceProgramId() == null) {
                continue;
            }
            releaseById.put(release.getDeviceProgramId(), release);
        }
        if (releaseById.isEmpty()) {
            return List.of();
        }

        Map<UUID, ProgramEntity> programById = loadPrograms(releaseById.values());

        Map<Integer, String> deploymentStatusByReleaseProgramId = new HashMap<>();
        for (ProgramDeploymentEntity deployment : programDeploymentRepositoryJpa.findByDeviceIdOrderByAssignedAtDesc(deviceId)) {
            if (deployment == null || deployment.getReleaseProgramId() == null) {
                continue;
            }
            if (!userId.equals(deployment.getUserId())) {
                continue;
            }
            deploymentStatusByReleaseProgramId.putIfAbsent(
                    deployment.getReleaseProgramId(),
                    deployment.getStatus() != null ? deployment.getStatus().name() : null);
        }

        List<DeviceProgramAllowlistResp> list = new ArrayList<>();
        for (Map.Entry<Integer, AllowlistAggregate> entry : aggregateByReleaseProgramId.entrySet()) {
            Integer releaseProgramId = entry.getKey();
            AllowlistAggregate agg = entry.getValue();
            ProgramReleaseEntity release = releaseById.get(releaseProgramId);
            if (release == null || release.getProgramId() == null) {
                continue;
            }
            ProgramEntity program = programById.get(release.getProgramId());
            if (program == null || !userId.equals(program.getUserId())) {
                continue;
            }

            String source;
            if (agg.directPublish && agg.schedule) {
                source = "both";
            } else if (agg.directPublish) {
                source = "direct-publish";
            } else {
                source = "schedule";
            }

            list.add(DeviceProgramAllowlistResp.builder()
                    .releaseProgramId(releaseProgramId)
                    .programId(release.getProgramId())
                    .programName(program.getName())
                    .releaseVersion(release.getVersion())
                    .deviceTitleSnapshot(release.getDeviceTitleSnapshot())
                    .source(source)
                    .scheduleId(agg.schedule ? boundScheduleId : null)
                    .assignedAt(agg.assignedAt)
                    .deploymentStatus(deploymentStatusByReleaseProgramId.get(releaseProgramId))
                    .build());
        }

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

    private void replaceContentsRules(UUID userId, UUID scheduleId, List<UpsertScheduleContentsRuleReq> rules, OffsetDateTime now) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (scheduleId == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "scheduleId is required");
        }

        scheduleContentsRuleRepositoryJpa.deleteByScheduleId(scheduleId);

        if (rules == null || rules.isEmpty()) {
            return;
        }

        Set<Integer> priorities = new HashSet<>();
        Set<Integer> releaseIds = new HashSet<>();

        for (UpsertScheduleContentsRuleReq req : rules) {
            if (req == null) {
                throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "contentsRules contains null");
            }

            String type = normalizeContentsType(req.getType());
            if (!"rotation".equals(type) && !"spot".equals(type)) {
                throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "invalid type: " + req.getType());
            }

            if (req.getPriority() == null || req.getPriority() < 0) {
                throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "priority must be >= 0");
            }
            if (!priorities.add(req.getPriority())) {
                throw new BizException(ErrorCode.SCHEDULE_CONTENTS_PRIORITY_DUPLICATE);
            }

            if (req.getReleaseProgramId() == null || req.getReleaseProgramId() <= 0) {
                throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "releaseProgramId must be > 0");
            }
            releaseIds.add(req.getReleaseProgramId());

            boolean ifLimitTime = Boolean.TRUE.equals(req.getIfLimitTime());
            validateLimitObject(ifLimitTime, req.getLimitTime(), "limitTime");

            boolean ifLimitDate = Boolean.TRUE.equals(req.getIfLimitDate());
            validateLimitObject(ifLimitDate, req.getLimitDate(), "limitDate");

            boolean ifLimitWeekday = Boolean.TRUE.equals(req.getIfLimitWeekday());
            validateWeekdayArray(ifLimitWeekday, req.getLimitWeekday());
        }

        // ensure all referenced releases exist and belong to current user
        loadOwnedReleasesByIds(userId, releaseIds);

        List<ScheduleContentsRuleEntity> entities = new ArrayList<>();
        for (UpsertScheduleContentsRuleReq req : rules) {
            String type = normalizeContentsType(req.getType());

            boolean ifLimitTime = Boolean.TRUE.equals(req.getIfLimitTime());
            boolean ifLimitDate = Boolean.TRUE.equals(req.getIfLimitDate());
            boolean ifLimitWeekday = Boolean.TRUE.equals(req.getIfLimitWeekday());

            entities.add(ScheduleContentsRuleEntity.builder()
                    .id(IdGenerator.nextId())
                    .scheduleId(scheduleId)
                    .userId(userId)
                    .type(type)
                    .priority(req.getPriority())
                    .ifLimitTime(ifLimitTime)
                    .limitTime(ifLimitTime ? toJson(normalizeLimitTimeObject(req.getLimitTime())) : null)
                    .ifLimitDate(ifLimitDate)
                    .limitDate(ifLimitDate ? toJson(normalizeLimitDateObject(req.getLimitDate())) : null)
                    .ifLimitWeekday(ifLimitWeekday)
                    .limitWeekday(ifLimitWeekday ? toJson(req.getLimitWeekday()) : null)
                    .releaseProgramId(req.getReleaseProgramId())
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }

        scheduleContentsRuleRepositoryJpa.saveAll(entities);
    }

    private void replaceCommandRules(UUID userId, UUID scheduleId, List<UpsertScheduleCommandRuleReq> rules, OffsetDateTime now) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (scheduleId == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "scheduleId is required");
        }

        scheduleCommandRuleRepositoryJpa.deleteByScheduleId(scheduleId);

        if (rules == null || rules.isEmpty()) {
            return;
        }

        ObjectMapper mapper = JsonUtils.getDefaultObjectMapper();

        List<ScheduleCommandRuleEntity> entities = new ArrayList<>();
        for (UpsertScheduleCommandRuleReq req : rules) {
            if (req == null || req.getOperation() == null) {
                throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "command operation is required");
            }
            if (req.getOperation().getType() == null) {
                throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "command operation.type is required");
            }
            if (req.getOpTime() == null || req.getOpTime().isEmpty()) {
                throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "opTime is required");
            }

            boolean ifLimitDate = Boolean.TRUE.equals(req.getIfLimitDate());
            validateLimitObject(ifLimitDate, req.getLimitDate(), "limitDate");

            boolean ifLimitWeekday = Boolean.TRUE.equals(req.getIfLimitWeekday());
            validateWeekdayArray(ifLimitWeekday, req.getLimitWeekday());

            ObjectNode payload = buildCommandSchedulePayload(mapper, req, ifLimitDate, ifLimitWeekday);

            entities.add(ScheduleCommandRuleEntity.builder()
                    .id(IdGenerator.nextId())
                    .scheduleId(scheduleId)
                    .userId(userId)
                    .payloadJson(toJson(payload))
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }

        scheduleCommandRuleRepositoryJpa.saveAll(entities);
    }

    private ObjectNode buildCommandSchedulePayload(
            ObjectMapper mapper,
            UpsertScheduleCommandRuleReq req,
            boolean ifLimitDate,
            boolean ifLimitWeekday) {
        if (mapper == null || req == null || req.getOperation() == null || req.getOperation().getType() == null) {
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "invalid command rule");
        }

        DeviceActionBase action = req.getOperation();
        DeviceActionType actionType = action.getType();
        JsonNode bodyNode = toBodyNode(mapper, action);
        String scheduleName = resolveScheduleCommandName(actionType, bodyNode);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("name", scheduleName);
        payload.put("type", "command");

        ObjectNode operation = mapper.createObjectNode();
        operation.put("author_url", actionType.getUrl());
        operation.put("karma", actionType.getKarma());
        operation.put("content", buildScheduleOperationContent(action));
        payload.set("operation", operation);

        payload.set("op_time", buildOpTimeArray(mapper, req.getOpTime()));

        payload.put("if_limit_date", ifLimitDate);
        if (ifLimitDate) {
            payload.set("limit_date", req.getLimitDate());
        }

        payload.put("if_limit_weekday", ifLimitWeekday);
        if (ifLimitWeekday) {
            payload.set("limit_weekday", req.getLimitWeekday());
        }

        ObjectNode content = buildCommandScheduleContent(mapper, actionType, bodyNode);
        if (content != null) {
            payload.set("content", content);
        }

        return payload;
    }

    private JsonNode toBodyNode(ObjectMapper mapper, DeviceActionBase action) {
        if (mapper == null || action == null || action.getBody() == null) {
            return null;
        }
        return mapper.valueToTree(action.getBody());
    }

    private String resolveScheduleCommandName(DeviceActionType actionType, JsonNode bodyNode) {
        if (actionType == null) {
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "command operation.type is required");
        }
        if (actionType == DeviceActionType.BRIGHTNESS) {
            return "Brightness_Control";
        }
        if (actionType == DeviceActionType.VOLUME) {
            return "Volume_Control";
        }
        if (actionType == DeviceActionType.COLOR_TEMP) {
            return "Colortemp_Control";
        }
        if (actionType == DeviceActionType.CLEAR_CACHE) {
            return "Clear_Cache";
        }
        if (actionType == DeviceActionType.INPUT_MODE) {
            return "Switch_Signal_Source";
        }
        if (actionType == DeviceActionType.POWER) {
            String command = readText(bodyNode, "command");
            if (!StringUtils.hasText(command)) {
                throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "power command is required (sleep/wakeup/reboot)");
            }
            String normalized = command.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "sleep" -> "Sleep";
                case "wakeup" -> "Wakeup";
                case "reboot" -> "Reboot";
                default -> throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "power command must be sleep/wakeup/reboot");
            };
        }

        throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "unsupported schedule command operation.type: " + actionType.name());
    }

    private String buildScheduleOperationContent(DeviceActionBase action) {
        if (action == null || action.getType() == null) {
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "command operation is invalid");
        }
        if (action.getBody() == null) {
            if (action.getType() == DeviceActionType.CLEAR_CACHE) {
                return "{}";
            }
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "command operation.body is required");
        }
        String raw = JsonUtils.toJson(action.getBody());
        if (!StringUtils.hasText(raw)) {
            if (action.getType() == DeviceActionType.CLEAR_CACHE) {
                return "{}";
            }
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "command operation.body is invalid");
        }
        return raw;
    }

    private ObjectNode buildCommandScheduleContent(ObjectMapper mapper, DeviceActionType actionType, JsonNode bodyNode) {
        if (mapper == null || actionType == null || bodyNode == null) {
            return null;
        }

        if (actionType == DeviceActionType.BRIGHTNESS) {
            Integer v = readInt(bodyNode, "brightness");
            return buildValueContent(mapper, v);
        }
        if (actionType == DeviceActionType.VOLUME) {
            Integer v = readInt(bodyNode, "musicvolume");
            if (v == null) v = readInt(bodyNode, "volume");
            return buildValueContent(mapper, v);
        }
        if (actionType == DeviceActionType.COLOR_TEMP) {
            Integer v = readInt(bodyNode, "colortemp");
            return buildValueContent(mapper, v);
        }
        if (actionType == DeviceActionType.INPUT_MODE) {
            String inputmode = readText(bodyNode, "inputmode");
            if (!StringUtils.hasText(inputmode)) {
                return null;
            }
            String normalized = inputmode.trim().toLowerCase(Locale.ROOT);
            String switchValue;
            if ("hdmi".equals(normalized)) {
                switchValue = "sync";
            } else if ("dvi".equals(normalized)) {
                switchValue = "async";
            } else {
                return null;
            }

            ObjectNode content = mapper.createObjectNode();
            content.put("name", "Switch");
            content.put("value", switchValue);
            return content;
        }

        return null;
    }

    private ObjectNode buildValueContent(ObjectMapper mapper, Integer v) {
        if (v == null) {
            return null;
        }
        ObjectNode content = mapper.createObjectNode();
        content.put("name", "Value");
        content.put("value", v);
        return content;
    }

    private ArrayNode buildOpTimeArray(ObjectMapper mapper, List<String> opTime) {
        if (mapper == null) {
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "mapper is null");
        }
        if (opTime == null || opTime.isEmpty()) {
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "opTime is required");
        }
        var arr = mapper.createArrayNode();
        for (String t : opTime) {
            if (!StringUtils.hasText(t)) {
                continue;
            }
            arr.add(normalizeTimeOfDay(t, "opTime"));
        }
        if (arr.isEmpty()) {
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "opTime is required");
        }
        return arr;
    }

    private String normalizeTimeOfDay(String time, String fieldName) {
        if (!StringUtils.hasText(time)) {
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, fieldName + " contains blank time");
        }
        String v = time.trim();
        String[] parts = v.split(":");
        if (parts.length != 2 && parts.length != 3) {
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "invalid " + fieldName + ": " + v);
        }
        int hour = parseTimePart(parts[0], 0, 23, fieldName + ".hour");
        int minute = parseTimePart(parts[1], 0, 59, fieldName + ".minute");
        int second = 0;
        if (parts.length == 3) {
            second = parseTimePart(parts[2], 0, 59, fieldName + ".second");
        }
        return String.format("%02d:%02d:%02d", hour, minute, second);
    }

    private int parseTimePart(String part, int min, int max, String name) {
        if (!StringUtils.hasText(part)) {
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "invalid time " + name);
        }
        try {
            int v = Integer.parseInt(part.trim());
            if (v < min || v > max) {
                throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "invalid time " + name + ": " + v);
            }
            return v;
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "invalid time " + name + ": " + part);
        }
    }

    private String readText(JsonNode body, String field) {
        if (body == null || body.isNull() || !body.isObject() || !StringUtils.hasText(field)) {
            return null;
        }
        JsonNode node = body.get(field);
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String v = node.asText();
        return StringUtils.hasText(v) ? v.trim() : null;
    }

    private Integer readInt(JsonNode body, String field) {
        if (body == null || body.isNull() || !body.isObject() || !StringUtils.hasText(field)) {
            return null;
        }
        JsonNode node = body.get(field);
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.asInt();
    }

    private List<Integer> readRelayStatuses(JsonNode body) {
        if (body == null || body.isNull() || !body.isObject()) {
            return List.of();
        }

        JsonNode node = body.get("value");
        if (node == null || node.isNull()) {
            node = body.get("statuses");
        }
        if (node == null || node.isNull()) {
            node = body.get("relayStatuses");
        }

        if (node == null || node.isNull()) {
            return List.of();
        }

        // allow: [0,1,0] / [true,false,...] / "[0,1,0]"
        if (node.isTextual()) {
            JsonNode parsed = parseJson(node.asText());
            if (parsed != null) {
                node = parsed;
            }
        }

        if (!node.isArray()) {
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "relay statuses must be an array");
        }

        List<Integer> list = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || item.isNull()) {
                continue;
            }
            if (item.isBoolean()) {
                list.add(item.asBoolean() ? 1 : 0);
                continue;
            }
            if (item.isNumber()) {
                list.add(item.asInt());
                continue;
            }
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "relay status must be number or boolean");
        }
        return list;
    }

    private ArrayNode toIntArrayNode(ObjectMapper mapper, List<Integer> values) {
        var arr = mapper.createArrayNode();
        if (values == null) {
            return arr;
        }
        for (Integer v : values) {
            if (v == null) {
                continue;
            }
            arr.add(v);
        }
        return arr;
    }

    private Map<Integer, ProgramReleaseEntity> loadOwnedReleasesByIds(UUID userId, Set<Integer> ids) {
        Map<Integer, ProgramReleaseEntity> releaseById = loadReleasesByIds(ids);
        Map<UUID, ProgramEntity> programById = loadPrograms(releaseById.values());
        for (ProgramReleaseEntity release : releaseById.values()) {
            if (release == null || release.getProgramId() == null) {
                throw new BizException(ErrorCode.SCHEDULE_RELEASE_NOT_FOUND);
            }
            ProgramEntity program = programById.get(release.getProgramId());
            if (program == null || !userId.equals(program.getUserId())) {
                throw new BizException(ErrorCode.SCHEDULE_RELEASE_NOT_FOUND);
            }
        }
        return releaseById;
    }

    private String normalizeContentsType(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        return type.trim().toLowerCase(Locale.ROOT);
    }

    private void validateLimitObject(boolean enabled, JsonNode node, String fieldName) {
        if (!enabled) {
            return;
        }
        if (node == null || node.isNull() || !node.isObject()) {
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, fieldName + " must be a json object");
        }
        if ("limitTime".equals(fieldName)) {
            validateTimeRangeObject(node, fieldName);
        }
        if ("limitDate".equals(fieldName)) {
            validateDateRangeObject(node, fieldName);
        }
    }

    private void validateTimeRangeObject(JsonNode node, String fieldName) {
        if (node == null || node.isNull() || !node.isObject()) {
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, fieldName + " must be a json object");
        }

        JsonNode start = node.get("start_time");
        if (start == null) start = node.get("startTime");
        if (start == null) start = node.get("start");

        JsonNode end = node.get("end_time");
        if (end == null) end = node.get("endTime");
        if (end == null) end = node.get("end");

        if (start == null || start.isNull() || !start.isTextual() || !StringUtils.hasText(start.asText())) {
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, fieldName + ".start_time is required");
        }
        if (end == null || end.isNull() || !end.isTextual() || !StringUtils.hasText(end.asText())) {
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, fieldName + ".end_time is required");
        }
    }

    private void validateDateRangeObject(JsonNode node, String fieldName) {
        if (node == null || node.isNull() || !node.isObject()) {
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, fieldName + " must be a json object");
        }

        JsonNode start = node.get("start");
        JsonNode end = node.get("end");
        if (start == null || start.isNull() || !start.isTextual() || !StringUtils.hasText(start.asText())) {
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, fieldName + ".start is required");
        }
        if (end == null || end.isNull() || !end.isTextual() || !StringUtils.hasText(end.asText())) {
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, fieldName + ".end is required");
        }
    }

    private JsonNode normalizeLimitTimeObject(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return node;
        }
        ObjectNode normalized = ((ObjectNode) node).deepCopy();

        JsonNode start = normalized.get("start_time");
        if (start == null) start = normalized.get("startTime");
        if (start == null) start = normalized.get("start");
        if (start != null && !start.isNull()) {
            if (start.isTextual()) {
                normalized.put("start_time", normalizeTimeOfDay(start.asText(), "limitTime.start_time"));
            } else {
                normalized.set("start_time", start);
            }
            normalized.remove("startTime");
            normalized.remove("start");
        }

        JsonNode end = normalized.get("end_time");
        if (end == null) end = normalized.get("endTime");
        if (end == null) end = normalized.get("end");
        if (end != null && !end.isNull()) {
            if (end.isTextual()) {
                normalized.put("end_time", normalizeTimeOfDay(end.asText(), "limitTime.end_time"));
            } else {
                normalized.set("end_time", end);
            }
            normalized.remove("endTime");
            normalized.remove("end");
        }

        return normalized;
    }

    private JsonNode normalizeLimitDateObject(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return node;
        }
        ObjectNode normalized = ((ObjectNode) node).deepCopy();

        JsonNode startTime = normalized.get("start_time");
        if (startTime == null) startTime = normalized.get("startTime");
        if (startTime != null && !startTime.isNull()) {
            if (startTime.isTextual()) {
                normalized.put("start_time", normalizeTimeOfDay(startTime.asText(), "limitDate.start_time"));
            } else {
                normalized.set("start_time", startTime);
            }
            normalized.remove("startTime");
        }

        JsonNode endTime = normalized.get("end_time");
        if (endTime == null) endTime = normalized.get("endTime");
        if (endTime != null && !endTime.isNull()) {
            if (endTime.isTextual()) {
                normalized.put("end_time", normalizeTimeOfDay(endTime.asText(), "limitDate.end_time"));
            } else {
                normalized.set("end_time", endTime);
            }
            normalized.remove("endTime");
        }

        return normalized;
    }

    private void validateWeekdayArray(boolean enabled, JsonNode node) {
        if (!enabled) {
            return;
        }
        if (node == null || node.isNull() || !node.isArray()) {
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "limitWeekday must be a json array");
        }
        if (node.size() != 7) {
            throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "limitWeekday must have 7 boolean elements");
        }
        for (JsonNode item : node) {
            if (item == null || item.isNull() || !item.isBoolean()) {
                throw new BizException(ErrorCode.SCHEDULE_RULE_INVALID, "limitWeekday must be boolean array");
            }
        }
    }

    private Map<Integer, ProgramReleaseEntity> loadReleases(List<ScheduleContentsRuleEntity> rules) {
        if (rules == null || rules.isEmpty()) {
            return Map.of();
        }
        Set<Integer> ids = new HashSet<>();
        for (ScheduleContentsRuleEntity rule : rules) {
            if (rule == null || rule.getReleaseProgramId() == null) {
                continue;
            }
            ids.add(rule.getReleaseProgramId());
        }
        return loadReleasesByIds(ids);
    }

    private Map<Integer, ProgramReleaseEntity> loadReleasesByIds(Set<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Integer, ProgramReleaseEntity> map = new HashMap<>();
        for (ProgramReleaseEntity release : programReleaseRepositoryJpa.findAllById(ids)) {
            if (release == null || release.getDeviceProgramId() == null) {
                continue;
            }
            map.put(release.getDeviceProgramId(), release);
        }
        if (map.size() != ids.size()) {
            throw new BizException(ErrorCode.SCHEDULE_RELEASE_NOT_FOUND);
        }
        return map;
    }

    private Map<UUID, ProgramEntity> loadPrograms(Iterable<ProgramReleaseEntity> releases) {
        if (releases == null) {
            return Map.of();
        }
        Set<UUID> programIds = new HashSet<>();
        for (ProgramReleaseEntity release : releases) {
            if (release == null || release.getProgramId() == null) {
                continue;
            }
            programIds.add(release.getProgramId());
        }
        if (programIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ProgramEntity> map = new HashMap<>();
        for (ProgramEntity program : programRepositoryJpa.findAllById(programIds)) {
            if (program == null || program.getId() == null) {
                continue;
            }
            map.put(program.getId(), program);
        }
        return map;
    }

    private ScheduleEntity findOwnedSchedule(UUID userId, UUID scheduleId) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (scheduleId == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "scheduleId is required");
        }
        return scheduleRepositoryJpa.findByScheduleIdAndUserId(scheduleId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    private DeviceBasicEntity requireOwnedDevice(UUID userId, Long deviceId) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (deviceId == null || deviceId <= 0) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "deviceId is required");
        }
        DeviceBasicEntity device = deviceBasicRepositoryJpa.findByDeviceIdAndUserId(deviceId, userId).orElse(null);
        if (device == null) {
            throw new BizException(ErrorCode.DEVICE_NOT_FOUND_IN_CORE);
        }
        return device;
    }

    private OffsetDateTime max(OffsetDateTime a, OffsetDateTime b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }

    private static final class AllowlistAggregate {
        boolean directPublish;
        boolean schedule;
        OffsetDateTime assignedAt;
    }

    private JsonNode parseJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        ObjectMapper mapper = JsonUtils.getDefaultObjectMapper();
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String toJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return JsonUtils.getDefaultObjectMapper().writeValueAsString(node);
        } catch (Exception e) {
            throw new BizException(ErrorCode.JSON_SERIALIZATION_EXCEPTION, e);
        }
    }

    private boolean isDeviceServiceSuccessCode(String code) {
        return "200".equals(code) || ErrorCode.SUCCESS.getCode().equals(code);
    }

    private DeviceCommandResp callDeviceService(List<DeviceCommandReq> commands) {
        ResponseEntity<ApiResponse<DeviceCommandResp>> response;
        try {
            response = deviceInternalClient.sendCommand(commands);
        } catch (Exception ex) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "调用 device-service 下发排程指令失败", ex);
        }

        if (response == null) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "device-service 返回 null ResponseEntity");
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "device-service HTTP 状态异常: " + response.getStatusCode());
        }

        ApiResponse<DeviceCommandResp> body = response.getBody();
        if (body == null) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "device-service 返回空响应体");
        }
        if (!isDeviceServiceSuccessCode(body.getCode())) {
            throw new InfraException(
                    ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "device-service 返回失败: code=" + body.getCode() + ", message=" + body.getMessage());
        }

        DeviceCommandResp resp = body.getData();
        if (resp == null) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "device-service 返回空 data");
        }
        return resp;
    }
}
