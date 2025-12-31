package nan.produced.prism.core.program.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.program.api.dto.ProgramDeleteBlockedDetails;
import nan.produced.prism.core.common.config.StoragePathProperties;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.common.util.ContentTypeUtils;
import nan.produced.prism.core.common.util.IdGenerator;
import nan.produced.prism.core.common.util.FileNameUtils;
import nan.produced.prism.core.common.util.JsonUtils;
import nan.produced.prism.core.common.util.ObjectKeyUtils;
import nan.produced.prism.core.device.api.UntrackedDeviceCommandFacade;
import nan.produced.prism.core.device.api.DeviceStatusFacade;
import nan.produced.prism.core.integration.device.client.DeviceInternalClient;
import nan.produced.prism.core.integration.device.dto.command.DeviceCommandReq;
import nan.produced.prism.core.integration.device.dto.command.DeviceCommandResp;
import nan.produced.prism.core.media.application.domain.FileEntity;
import nan.produced.prism.core.media.application.domain.MediaAssetEntity;
import nan.produced.prism.core.media.application.port.outbound.MediaObjectUrlPort;
import nan.produced.prism.core.media.application.repository.MediaAssetRepository;
import nan.produced.prism.core.program.application.constant.ProgramScheduleConstant;
import nan.produced.prism.core.program.api.dto.CreateProgramReq;
import nan.produced.prism.core.program.api.dto.ProgramAuditLogResp;
import nan.produced.prism.core.program.api.dto.ProgramDeploymentResp;
import nan.produced.prism.core.program.api.dto.ProgramDetailResp;
import nan.produced.prism.core.program.api.dto.ProgramDraftResp;
import nan.produced.prism.core.program.api.dto.ProgramListResp;
import nan.produced.prism.core.program.api.dto.ProgramPublishDeviceResultResp;
import nan.produced.prism.core.program.api.dto.ProgramPublishMode;
import nan.produced.prism.core.program.api.dto.ProgramPublishReq;
import nan.produced.prism.core.program.api.dto.ProgramPublishResp;
import nan.produced.prism.core.program.api.dto.ProgramPublishScope;
import nan.produced.prism.core.program.api.dto.ProgramPublishVersionMode;
import nan.produced.prism.core.program.api.dto.ProgramRenameReq;
import nan.produced.prism.core.program.api.dto.ProgramTemplateResp;
import nan.produced.prism.core.program.api.dto.ProgramUnpublishReq;
import nan.produced.prism.core.program.api.dto.ProgramUnpublishResp;
import nan.produced.prism.core.program.api.dto.ProgramVersionResp;
import nan.produced.prism.core.program.api.dto.SaveProgramDraftReq;
import nan.produced.prism.core.program.domain.ProgramAssignmentEntity;
import nan.produced.prism.core.program.domain.ProgramAuditAction;
import nan.produced.prism.core.program.domain.ProgramAuditLogEntity;
import nan.produced.prism.core.program.domain.ProgramDeploymentEntity;
import nan.produced.prism.core.program.domain.ProgramDraftEntity;
import nan.produced.prism.core.program.domain.ProgramEntity;
import nan.produced.prism.core.program.domain.ProgramReleaseEntity;
import nan.produced.prism.core.program.domain.schedule.ScheduleEntity;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramAssignmentRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramAuditLogRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramDeploymentRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramDraftRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramReleaseRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramTemplateRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ScheduleContentsRuleRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ScheduleRepositoryJpa;
import nan.produced.prism.core.resource.application.service.ResourceTombstoneService;
import nan.produced.prism.core.resource.domain.ResourceType;
import nan.produced.prism.core.message.api.MessageCenterFacade;
import nan.produced.prism.core.security.api.CloudAuthContext;
import nan.produced.prism.core.system.api.SubscriptionQuotaFacade;
import nan.produced.prism.core.user.api.UserQuotaFacade;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramApplicationService {

    private static final String UNTRACKED_COMMAND_TYPE_PROGRAM_DIRTY = "PROGRAM_DIRTY";
    private static final String UNTRACKED_COMMAND_TYPE_PROGRAM_DELETE = "PROGRAM_DELETE";
    private static final String DEVICE_COMMAND_RAW_DELETE_VSN = "{\"command\":\"\"}";
    private static final int DELETE_BLOCKER_SAMPLE_SIZE = 20;

    private final ProgramRepositoryJpa programRepositoryJpa;
    private final ProgramDraftRepositoryJpa programDraftRepositoryJpa;
    private final ProgramReleaseRepositoryJpa programReleaseRepositoryJpa;
    private final ProgramDeploymentRepositoryJpa programDeploymentRepositoryJpa;
    private final ProgramAssignmentRepositoryJpa programAssignmentRepositoryJpa;
    private final ProgramTemplateRepositoryJpa programTemplateRepositoryJpa;
    private final ProgramAuditLogRepositoryJpa programAuditLogRepositoryJpa;
    private final SubscriptionQuotaFacade subscriptionQuotaFacade;

    private final MediaAssetRepository mediaAssetRepository;
    private final DeviceStatusFacade deviceStatusFacade;
    private final DeviceInternalClient deviceInternalClient;
    private final MediaObjectUrlPort mediaObjectUrlPort;
    private final StoragePathProperties storagePathProperties;
    private final S3Client s3Client;
    private final MessageCenterFacade messageCenterFacade;
    private final ProgramQuotaSignalPublisher programQuotaSignalPublisher;
    private final UserQuotaFacade userQuotaFacade;
    private final UntrackedDeviceCommandFacade untrackedDeviceCommandRegistry;
    private final ScheduleContentsRuleRepositoryJpa scheduleContentsRuleRepositoryJpa;
    private final ScheduleRepositoryJpa scheduleRepositoryJpa;
    private final ResourceTombstoneService resourceTombstoneService;

    @Value("${prism.media.s3.bucket}")
    private String s3Bucket;

    @Transactional
    public ProgramDetailResp createProgram(UUID userId, String tier, CreateProgramReq req) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (req == null || !StringUtils.hasText(req.getName()) || req.getWidth() == null || req.getHeight() == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "name/width/height is required");
        }

        ensureProgramLimit(userId, tier);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ProgramEntity entity = ProgramEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(req.getName().trim())
                .width(req.getWidth())
                .height(req.getHeight())
                .defaultVersion(null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        programRepositoryJpa.save(entity);
        writeAudit(userId, entity.getId(), ProgramAuditAction.CREATE, null);
        onProgramsChangedBestEffort(userId, tier);

        return getProgramDetail(userId, entity.getId());
    }

    @Transactional(readOnly = true)
    public List<ProgramListResp> listPrograms(UUID userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }

        List<ProgramEntity> programs = programRepositoryJpa.findByUserIdOrderByUpdatedAtDesc(userId);
        return toProgramListResps(programs);
    }

    @Transactional(readOnly = true)
    public List<ProgramListResp> listProgramsByIds(UUID userId, List<UUID> programIds) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (programIds == null || programIds.isEmpty()) {
            return List.of();
        }

        List<UUID> distinctIds = programIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return List.of();
        }

        List<ProgramEntity> programs = programRepositoryJpa.findByUserIdAndIdIn(userId, distinctIds);
        if (programs == null || programs.isEmpty()) {
            return List.of();
        }

        Map<UUID, ProgramEntity> programById = new HashMap<>();
        for (ProgramEntity program : programs) {
            if (program != null && program.getId() != null) {
                programById.put(program.getId(), program);
            }
        }

        List<ProgramEntity> ordered = distinctIds.stream()
                .map(programById::get)
                .filter(Objects::nonNull)
                .toList();

        return toProgramListResps(ordered);
    }

    private List<ProgramListResp> toProgramListResps(List<ProgramEntity> programs) {
        if (programs == null || programs.isEmpty()) {
            return List.of();
        }

        List<UUID> programIds = programs.stream()
                .map(ProgramEntity::getId)
                .filter(Objects::nonNull)
                .toList();
        if (programIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, ProgramReleaseEntity> latestReleaseByProgramId = pickLatestReleaseByProgramId(
                programReleaseRepositoryJpa.findByProgramIdInOrderByProgramIdAscVersionDesc(programIds));
        Map<UUID, ProgramDraftEntity> latestDraftByProgramId = pickLatestDraftByProgramId(
                programDraftRepositoryJpa.findByProgramIdInOrderByProgramIdAscUpdatedAtDesc(programIds));

        return programs.stream().map(program -> {
            ProgramReleaseEntity latestRelease = latestReleaseByProgramId.get(program.getId());
            ProgramDraftEntity latestDraft = latestDraftByProgramId.get(program.getId());

            OffsetDateTime latestReleaseAt = latestRelease != null ? latestRelease.getCreatedAt() : null;
            OffsetDateTime latestDraftAt = latestDraft != null ? latestDraft.getUpdatedAt() : null;

            String coverUrl = null;
            if (latestRelease != null && StringUtils.hasText(latestRelease.getCoverObjectKey())) {
                coverUrl = mediaObjectUrlPort.toPublicUrl(latestRelease.getCoverObjectKey());
            } else if (latestDraft != null && StringUtils.hasText(latestDraft.getCoverObjectKey())) {
                coverUrl = mediaObjectUrlPort.toPublicUrl(latestDraft.getCoverObjectKey());
            }

            Boolean unpublishedChanges = null;
            if (latestDraftAt != null) {
                unpublishedChanges = (latestReleaseAt == null) || latestDraftAt.isAfter(latestReleaseAt);
            }

            return ProgramListResp.builder()
                    .id(program.getId())
                    .name(program.getName())
                    .width(program.getWidth())
                    .height(program.getHeight())
                    .defaultVersion(program.getDefaultVersion())
                    .latestVersion(latestRelease != null ? latestRelease.getVersion() : null)
                    .latestReleaseAt(latestReleaseAt)
                    .latestDraftAt(latestDraftAt)
                    .unpublishedChanges(unpublishedChanges)
                    .coverUrl(coverUrl)
                    .createdAt(program.getCreatedAt())
                    .updatedAt(program.getUpdatedAt())
                    .build();
        }).toList();
    }

    @Transactional(readOnly = true)
    public ProgramDetailResp getProgramDetail(UUID userId, UUID programId) {
        ProgramEntity program = findOwnedProgram(userId, programId);

        List<ProgramDraftResp> drafts = programDraftRepositoryJpa.findByProgramIdOrderByUpdatedAtDesc(programId).stream()
                .map(this::toDraftResp)
                .toList();

        List<ProgramVersionResp> versions = programReleaseRepositoryJpa.findByProgramIdOrderByVersionDesc(programId).stream()
                .map(this::toVersionResp)
                .toList();

        Map<Long, ProgramDeploymentEntity> deploymentByDeviceId = new HashMap<>();
        for (ProgramDeploymentEntity deployment : programDeploymentRepositoryJpa.findByProgramIdOrderByAssignedAtDesc(programId)) {
            if (deployment == null || deployment.getDeviceId() == null) {
                continue;
            }
            deploymentByDeviceId.put(deployment.getDeviceId(), deployment);
        }

        List<ProgramDeploymentResp> deployments = programAssignmentRepositoryJpa.findByProgramIdOrderByAssignedAtDesc(programId).stream()
                .map(assignment -> {
                    if (assignment == null || assignment.getDeviceId() == null) {
                        return null;
                    }
                    ProgramDeploymentEntity observed = deploymentByDeviceId.get(assignment.getDeviceId());
                    return ProgramDeploymentResp.builder()
                            .programId(assignment.getProgramId())
                            .deviceId(assignment.getDeviceId())
                            .deviceName(null)
                            .releaseVersion(assignment.getReleaseVersion())
                            .releaseProgramId(assignment.getReleaseProgramId())
                            .assignedAt(assignment.getAssignedAt())
                            .status(observed != null ? observed.getStatus() : null)
                            .updatedAt(observed != null ? observed.getUpdatedAt() : assignment.getUpdatedAt())
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();

        return ProgramDetailResp.builder()
                .id(program.getId())
                .name(program.getName())
                .width(program.getWidth())
                .height(program.getHeight())
                .defaultVersion(program.getDefaultVersion())
                .drafts(drafts)
                .versions(versions)
                .deployments(deployments)
                .createdAt(program.getCreatedAt())
                .updatedAt(program.getUpdatedAt())
                .build();
    }

    @Transactional
    public ProgramDetailResp renameProgram(UUID userId, UUID programId, ProgramRenameReq req) {
        ProgramEntity program = findOwnedProgram(userId, programId);
        if (req == null || !StringUtils.hasText(req.getName())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "name is required");
        }

        program.setName(req.getName().trim());
        program.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        programRepositoryJpa.save(program);

        writeAudit(userId, programId, ProgramAuditAction.RENAME, null);
        return getProgramDetail(userId, programId);
    }

    @Transactional
    public void deleteProgram(UUID userId, UUID programId) {
        ProgramEntity program = findOwnedProgram(userId, programId);

        ProgramDeleteBlockedDetails blockedDetails = checkProgramDeleteBlocked(userId, programId);
        if (blockedDetails != null) {
            throw new BizException(ErrorCode.PROGRAM_DELETE_BLOCKED, "program delete blocked", blockedDetails);
        }

        writeAudit(userId, programId, ProgramAuditAction.DELETE, null);

        // Best-effort tombstone, for telemetry queries after deletion (retain 60 days).
        resourceTombstoneService.markDeleted(
                userId,
                ResourceType.PROGRAM,
                programId.toString(),
                ResourceTombstoneService.NO_VERSION,
                program.getName());
        List<ProgramReleaseEntity> releases = programReleaseRepositoryJpa.findByProgramIdOrderByVersionDesc(programId);
        if (releases != null && !releases.isEmpty()) {
            for (ProgramReleaseEntity r : releases) {
                if (r == null || r.getVersion() == null) {
                    continue;
                }
                resourceTombstoneService.markDeleted(
                        userId,
                        ResourceType.PROGRAM_RELEASE,
                        programId.toString(),
                        r.getVersion(),
                        program.getName());
            }
        }

        try {
            programRepositoryJpa.delete(program);
            // 强制触发约束检查，避免异常在事务提交阶段抛出导致无法返回结构化原因
            programRepositoryJpa.flush();
        } catch (DataIntegrityViolationException e) {
            ProgramDeleteBlockedDetails details = checkProgramDeleteBlocked(userId, programId);
            if (details == null) {
                details = ProgramDeleteBlockedDetails.builder().programId(programId).build();
            }
            throw new BizException(
                    ErrorCode.PROGRAM_DELETE_BLOCKED,
                    "program delete blocked by constraint",
                    details,
                    e);
        }

        onProgramsChangedBestEffort(userId, resolveTierFromContext());
    }

    private ProgramDeleteBlockedDetails checkProgramDeleteBlocked(UUID userId, UUID programId) {
        if (userId == null || programId == null) {
            return null;
        }

        // 1) schedule 引用（release_program_id -> pcc_program_release.device_program_id）
        List<ProgramReleaseEntity> releases = programReleaseRepositoryJpa.findByProgramIdOrderByVersionDesc(programId);
        List<Integer> releaseProgramIds = releases == null
                ? List.of()
                : releases.stream()
                        .map(ProgramReleaseEntity::getDeviceProgramId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        List<UUID> scheduleIds = List.of();
        if (!releaseProgramIds.isEmpty()) {
            scheduleIds = scheduleContentsRuleRepositoryJpa.findDistinctScheduleIdsByReleaseProgramIdIn(releaseProgramIds);
        }

        ProgramDeleteBlockedDetails.ScheduleRef scheduleRef = null;
        if (scheduleIds != null && !scheduleIds.isEmpty()) {
            Map<UUID, ScheduleEntity> scheduleById = new HashMap<>();
            for (ScheduleEntity s : scheduleRepositoryJpa.findAllById(scheduleIds)) {
                if (s == null || s.getScheduleId() == null || !userId.equals(s.getUserId())) {
                    continue;
                }
                scheduleById.put(s.getScheduleId(), s);
            }

            List<UUID> uniq = scheduleIds.stream().filter(Objects::nonNull).distinct().toList();
            List<ProgramDeleteBlockedDetails.ScheduleItem> items = uniq.stream()
                    .limit(DELETE_BLOCKER_SAMPLE_SIZE)
                    .map(id -> {
                        ScheduleEntity s = scheduleById.get(id);
                        return ProgramDeleteBlockedDetails.ScheduleItem.builder()
                                .scheduleId(id)
                                .name(s != null ? s.getName() : null)
                                .enabled(s != null ? s.getEnabled() : null)
                                .build();
                    })
                    .toList();

            scheduleRef = ProgramDeleteBlockedDetails.ScheduleRef.builder()
                    .count(uniq.size())
                    .items(items)
                    .build();
        }

        // 2) direct publish assignment 引用（仍在分发）
        List<ProgramAssignmentEntity> assignments = programAssignmentRepositoryJpa.findByProgramIdOrderByAssignedAtDesc(programId);
        int assignmentCount = 0;
        List<Long> assignmentDeviceIds = List.of();
        if (assignments != null && !assignments.isEmpty()) {
            List<Long> distinctIds = assignments.stream()
                    .filter(a -> a != null && a.getDeviceId() != null && userId.equals(a.getUserId()))
                    .map(ProgramAssignmentEntity::getDeviceId)
                    .distinct()
                    .toList();
            assignmentCount = distinctIds.size();
            assignmentDeviceIds = distinctIds.stream().limit(DELETE_BLOCKER_SAMPLE_SIZE).toList();
        }

        ProgramDeleteBlockedDetails.AssignmentRef assignmentRef = null;
        if (assignmentCount > 0) {
            assignmentRef = ProgramDeleteBlockedDetails.AssignmentRef.builder()
                    .count(assignmentCount)
                    .deviceIds(assignmentDeviceIds)
                    .build();
        }

        // 3) deployment 引用（设备仍持有/下载中）
        var deployments = programDeploymentRepositoryJpa.findByProgramIdOrderByAssignedAtDesc(programId);
        ProgramDeleteBlockedDetails.DeploymentRef deploymentRef = null;
        if (deployments != null && !deployments.isEmpty()) {
            List<ProgramDeleteBlockedDetails.DeploymentItem> items = deployments.stream()
                    .filter(d -> d != null && d.getDeviceId() != null && Objects.equals(userId, d.getUserId()))
                    .map(d -> ProgramDeleteBlockedDetails.DeploymentItem.builder()
                            .deviceId(d.getDeviceId())
                            .status(d.getStatus() != null ? d.getStatus().name() : null)
                            .releaseProgramId(d.getReleaseProgramId())
                            .releaseVersion(d.getReleaseVersion())
                            .build())
                    .distinct()
                    .limit(DELETE_BLOCKER_SAMPLE_SIZE)
                    .toList();

            int count = (int) deployments.stream()
                    .filter(d -> d != null && d.getDeviceId() != null && Objects.equals(userId, d.getUserId()))
                    .map(d -> d.getDeviceId().toString())
                    .distinct()
                    .count();

            if (count > 0) {
                deploymentRef = ProgramDeleteBlockedDetails.DeploymentRef.builder()
                        .count(count)
                        .items(items)
                        .build();
            }
        }

        if (scheduleRef == null && assignmentRef == null && deploymentRef == null) {
            return null;
        }

        return ProgramDeleteBlockedDetails.builder()
                .programId(programId)
                .scheduleRef(scheduleRef)
                .assignmentRef(assignmentRef)
                .deploymentRef(deploymentRef)
                .build();
    }

    private void ensureProgramLimit(UUID userId, String tier) {
        Integer limit = resolveProgramLimit(tier);
        if (limit == null || limit < 0) {
            return;
        }

        long current = programRepositoryJpa.countByUserId(userId);
        long next = current + 1;
        if (next > limit) {
            programQuotaSignalPublisher.publishProgramsExceeded(userId, normalizeTier(tier), next, limit);
            throw new BizException(ErrorCode.PROGRAM_LIMIT_EXCEEDED);
        }
    }

    private void onProgramsChangedBestEffort(UUID userId, String tier) {
        if (userId == null) {
            return;
        }

        try {
            long used = programRepositoryJpa.countByUserId(userId);
            Integer limit = resolveProgramLimit(tier);
            programQuotaSignalPublisher.publishProgramsUpdated(userId, normalizeTier(tier), used, limit);
            userQuotaFacade.syncProgramCount(userId, (int) Math.min(Integer.MAX_VALUE, used));
        } catch (Exception ex) {
            log.debug("program count sync/publish failed (ignored): userId={}", userId, ex);
        }
    }

    private String resolveTierFromContext() {
        if (!CloudAuthContext.hasAuthenticatedUser()) {
            return null;
        }
        try {
            return CloudAuthContext.getCurrentUser().tier();
        } catch (Exception ignore) {
            return null;
        }
    }

    private String normalizeTier(String tier) {
        if (!StringUtils.hasText(tier)) {
            return "FREE";
        }
        String normalized = tier.trim().toUpperCase();
        return "PRO".equals(normalized) ? "PRO" : "FREE";
    }

    private Integer resolveProgramLimit(String tier) {
        try {
            return subscriptionQuotaFacade.getQuota(normalizeTier(tier)).programLimit();
        } catch (Exception ex) {
            return null;
        }
    }

    @Transactional
    public ProgramDraftResp ensureDraft(UUID userId, UUID programId, Integer baseVersion) {
        ProgramEntity program = findOwnedProgram(userId, programId);

        int normalizedBase = baseVersion != null ? baseVersion : 0;
        ProgramDraftEntity existing = programDraftRepositoryJpa.findByProgramIdAndBaseVersion(programId, normalizedBase).orElse(null);
        if (existing != null) {
            return toDraftResp(existing);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String vsnJson = buildInitialDraftVsnJson(program, normalizedBase);

        ProgramDraftEntity created = ProgramDraftEntity.builder()
                .draftId(UUID.randomUUID())
                .programId(programId)
                .baseVersion(normalizedBase)
                .vsnJson(vsnJson)
                .contentHash(null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        programDraftRepositoryJpa.save(created);
        return toDraftResp(created);
    }

    @Transactional
    public ProgramDraftResp saveDraft(UUID userId, UUID programId, UUID draftId, SaveProgramDraftReq req) {
        findOwnedProgram(userId, programId);

        ProgramDraftEntity draft = programDraftRepositoryJpa.findById(draftId)
                .orElseThrow(() -> new BizException(ErrorCode.PROGRAM_DRAFT_NOT_FOUND));
        if (!programId.equals(draft.getProgramId())) {
            throw new BizException(ErrorCode.PROGRAM_DRAFT_NOT_FOUND);
        }

        if (req == null || !StringUtils.hasText(req.getVsnJson())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "vsnJson is required");
        }

        draft.setVsnJson(req.getVsnJson());
        draft.setContentHash(StringUtils.hasText(req.getContentHash()) ? req.getContentHash().trim() : null);

        if (StringUtils.hasText(req.getCoverBase64())) {
            CoverUpload coverUpload = parseCover(req.getCoverBase64(), req.getCoverContentType());
            if (coverUpload.bytes().length > 0) {
                String objectKey = putDraftCover(programId, draftId, coverUpload.bytes(), coverUpload.contentType());
                draft.setCoverObjectKey(objectKey);
                draft.setCoverContentType(coverUpload.contentType());
                draft.setCoverSizeBytes((long) coverUpload.bytes().length);
            }
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        draft.setUpdatedAt(now);
        programDraftRepositoryJpa.save(draft);

        ProgramEntity program = programRepositoryJpa.findById(programId).orElse(null);
        if (program != null) {
            program.setUpdatedAt(now);
            programRepositoryJpa.save(program);
        }

        return toDraftResp(draft);
    }

    @Transactional
    public void deleteDraft(UUID userId, UUID programId, UUID draftId) {
        findOwnedProgram(userId, programId);
        ProgramDraftEntity draft = programDraftRepositoryJpa.findById(draftId)
                .orElseThrow(() -> new BizException(ErrorCode.PROGRAM_DRAFT_NOT_FOUND));
        if (!programId.equals(draft.getProgramId())) {
            throw new BizException(ErrorCode.PROGRAM_DRAFT_NOT_FOUND);
        }
        programDraftRepositoryJpa.delete(draft);
    }

    @Transactional
    public ProgramPublishResp publish(UUID userId, String tier, UUID programId, ProgramPublishReq req) {
        ProgramEntity program = findOwnedProgram(userId, programId);
        if (req == null || req.getVersionMode() == null || req.getScope() == null || req.getMode() == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "versionMode/scope/mode is required");
        }

        List<Long> targetDeviceIds = resolveTargetDeviceIds(programId, req);
        if (targetDeviceIds.isEmpty()) {
            throw new BizException(ErrorCode.PROGRAM_PUBLISH_TARGET_EMPTY);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ProgramReleaseEntity targetRelease;
        boolean createdNewVersion = false;
        if (req.getVersionMode() == ProgramPublishVersionMode.EXISTING) {
            if (req.getExistingVersion() == null || req.getExistingVersion() <= 0) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "existingVersion is required for EXISTING");
            }
            targetRelease = programReleaseRepositoryJpa.findByProgramIdAndVersion(programId, req.getExistingVersion())
                    .orElseThrow(() -> new BizException(ErrorCode.PROGRAM_VERSION_NOT_FOUND));
        } else {
            createdNewVersion = true;
            targetRelease = createNewRelease(userId, tier, program, req, now);
        }

        int targetVersion = targetRelease.getVersion();

        // 设置默认版本为本次发布目标版本（提升 UX：下一次默认从该版本继续编辑/发布）
        program.setDefaultVersion(targetVersion);
        program.setUpdatedAt(now);
        programRepositoryJpa.save(program);

        Map<Long, ProgramAssignmentEntity> existingByDeviceId = new HashMap<>();
        for (ProgramAssignmentEntity assignment : programAssignmentRepositoryJpa.findByProgramIdOrderByAssignedAtDesc(programId)) {
            if (assignment == null || assignment.getDeviceId() == null) {
                continue;
            }
            existingByDeviceId.put(assignment.getDeviceId(), assignment);
        }

        List<DeviceCommandReq> commands = new java.util.ArrayList<>();
        Map<String, ProgramPublishDeviceResultResp> resultByCommandId = new HashMap<>();
        List<ProgramPublishDeviceResultResp> results = new java.util.ArrayList<>();

        int affected = 0;
        List<Long> affectedDeviceIds = new java.util.ArrayList<>();

        for (Long deviceId : targetDeviceIds) {
            if (deviceId == null) {
                continue;
            }

            ProgramAssignmentEntity existing = existingByDeviceId.get(deviceId);
            String action = computeAction(existing != null ? existing.getReleaseVersion() : null, targetVersion);

            boolean shouldAffect = shouldAffect(existing != null, req.getMode());
            if (!shouldAffect) {
                results.add(ProgramPublishDeviceResultResp.builder()
                        .deviceId(deviceId)
                        .action(ProgramScheduleConstant.ProgramPublishAction.SKIP)
                        .affected(false)
                        .accepted(false)
                        .build());
                continue;
            }

            affected++;
            affectedDeviceIds.add(deviceId);

            ProgramAssignmentEntity assignment = existing != null ? existing : ProgramAssignmentEntity.builder()
                    .programId(programId)
                    .deviceId(deviceId)
                    .userId(userId)
                    .createdAt(now)
                    .build();

            assignment.setReleaseVersion(targetVersion);
            assignment.setReleaseProgramId(targetRelease.getDeviceProgramId());
            assignment.setAssignedAt(now);
            assignment.setUpdatedAt(now);

            programAssignmentRepositoryJpa.save(assignment);

            String commandId = UUID.randomUUID().toString();
            if (untrackedDeviceCommandRegistry != null) {
                untrackedDeviceCommandRegistry.markByCommandId(commandId, UNTRACKED_COMMAND_TYPE_PROGRAM_DIRTY);
            }
            DeviceCommandReq command = buildProgramDirtyCommand(deviceId, commandId);
            commands.add(command);

            ProgramPublishDeviceResultResp deviceResult = ProgramPublishDeviceResultResp.builder()
                    .deviceId(deviceId)
                    .action(action)
                    .affected(true)
                    .commandId(commandId)
                    .accepted(false)
                    .build();
            results.add(deviceResult);
            resultByCommandId.put(commandId, deviceResult);
        }

        if (!affectedDeviceIds.isEmpty()) {
            List<Long> onlineDeviceIds = deviceStatusFacade.findOnlineDeviceIds(userId, affectedDeviceIds);
            messageCenterFacade.startProgramPublishTracking(
                userId,
                programId,
                program.getName(),
                targetVersion,
                targetRelease.getDeviceProgramId(),
                affectedDeviceIds,
                onlineDeviceIds);
        }

        if (!commands.isEmpty()) {
            DeviceCommandResp resp = callDeviceService(commands);
            applyDeviceCommandResults(resp, resultByCommandId);
            markUntrackedProgramDirtyQueues(results);
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("version", targetVersion);
        details.put("deviceProgramId", targetRelease.getDeviceProgramId());
        details.put("createdNewVersion", createdNewVersion);
        details.put("scope", req.getScope() != null ? req.getScope().name() : null);
        details.put("mode", req.getMode() != null ? req.getMode().name() : null);
        details.put("targets", targetDeviceIds.size());
        details.put("affected", affected);
        writeAudit(userId, programId, ProgramAuditAction.PUBLISH, JsonUtils.toJson(details));

        return ProgramPublishResp.builder()
                .programId(programId)
                .version(targetVersion)
                .deviceProgramId(targetRelease.getDeviceProgramId())
                .totalTargets(targetDeviceIds.size())
                .affected(affected)
                .results(results)
                .build();
    }

    @Transactional
    public ProgramUnpublishResp unpublish(UUID userId, UUID programId, ProgramUnpublishReq req) {
        ProgramEntity program = findOwnedProgram(userId, programId);
        if (req == null || req.getScope() == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "scope is required");
        }

        List<Long> targetDeviceIds = resolveTargetDeviceIds(programId, req.getScope(), req.getDeviceIds());
        if (targetDeviceIds.isEmpty()) {
            return ProgramUnpublishResp.builder()
                    .programId(programId)
                    .totalTargets(0)
                    .removed(0)
                    .results(List.of())
                    .build();
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Map<Long, ProgramAssignmentEntity> existingByDeviceId = new HashMap<>();
        for (ProgramAssignmentEntity assignment : programAssignmentRepositoryJpa.findByProgramIdOrderByAssignedAtDesc(programId)) {
            if (assignment == null || assignment.getDeviceId() == null) {
                continue;
            }
            existingByDeviceId.put(assignment.getDeviceId(), assignment);
        }

        List<DeviceCommandReq> commands = new java.util.ArrayList<>();
        Map<String, ProgramPublishDeviceResultResp> resultByCommandId = new HashMap<>();
        Map<String, Long> deleteDeviceIdByCommandId = new HashMap<>();
        List<ProgramPublishDeviceResultResp> results = new java.util.ArrayList<>();

        int removed = 0;

        for (Long deviceId : targetDeviceIds) {
            if (deviceId == null) {
                continue;
            }

            ProgramAssignmentEntity existing = existingByDeviceId.get(deviceId);
            if (existing == null) {
                results.add(ProgramPublishDeviceResultResp.builder()
                        .deviceId(deviceId)
                        .action(ProgramScheduleConstant.ProgramPublishAction.SKIP)
                        .affected(false)
                        .accepted(false)
                        .build());
                continue;
            }

            programAssignmentRepositoryJpa.delete(existing);
            removed++;

            String vsnName = resolveVsnFilename(existing.getReleaseProgramId());
            if (StringUtils.hasText(vsnName)) {
                for (String source : List.of("internet", "lan")) {
                    String deleteCommandId = UUID.randomUUID().toString();
                    if (untrackedDeviceCommandRegistry != null) {
                        untrackedDeviceCommandRegistry.markByCommandId(deleteCommandId, UNTRACKED_COMMAND_TYPE_PROGRAM_DELETE);
                    }
                    commands.add(buildDeleteVsnCommand(deviceId, deleteCommandId, source, vsnName));
                    deleteDeviceIdByCommandId.put(deleteCommandId, deviceId);
                }
            } else {
                log.warn(
                        "Unpublish delete skipped: failed to resolve vsnName, programId={}, deviceId={}, releaseProgramId={}",
                        programId,
                        deviceId,
                        existing.getReleaseProgramId());
            }

            String commandId = UUID.randomUUID().toString();
            if (untrackedDeviceCommandRegistry != null) {
                untrackedDeviceCommandRegistry.markByCommandId(commandId, UNTRACKED_COMMAND_TYPE_PROGRAM_DIRTY);
            }
            DeviceCommandReq command = buildProgramDirtyCommand(deviceId, commandId);
            commands.add(command);

            ProgramPublishDeviceResultResp deviceResult = ProgramPublishDeviceResultResp.builder()
                    .deviceId(deviceId)
                    .action(ProgramScheduleConstant.ProgramPublishAction.UNDEPLOY)
                    .affected(true)
                    .commandId(commandId)
                    .accepted(false)
                    .build();
            results.add(deviceResult);
            resultByCommandId.put(commandId, deviceResult);
        }

        if (!commands.isEmpty()) {
            DeviceCommandResp resp = callDeviceService(commands);
            applyDeviceCommandResults(resp, resultByCommandId);
            markUntrackedProgramDirtyQueues(results);
            markUntrackedDeleteQueues(resp, deleteDeviceIdByCommandId);
        }

        program.setUpdatedAt(now);
        programRepositoryJpa.save(program);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scope", req.getScope() != null ? req.getScope().name() : null);
        details.put("targets", targetDeviceIds.size());
        details.put("removed", removed);
        writeAudit(userId, programId, ProgramAuditAction.UNPUBLISH, JsonUtils.toJson(details));

        return ProgramUnpublishResp.builder()
                .programId(programId)
                .totalTargets(targetDeviceIds.size())
                .removed(removed)
                .results(results)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ProgramAuditLogResp> listAuditLogs(UUID userId, UUID programId) {
        findOwnedProgram(userId, programId);
        return programAuditLogRepositoryJpa.findByProgramIdOrderByCreatedAtDesc(programId).stream()
                .map(this::toAuditLogResp)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProgramTemplateResp> listTemplates(UUID userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        return programTemplateRepositoryJpa.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(template -> ProgramTemplateResp.builder()
                        .templateId(template.getTemplateId())
                        .name(template.getName())
                        .description(template.getDescription())
                        .width(template.getWidth())
                        .height(template.getHeight())
                        .createdAt(template.getCreatedAt())
                        .updatedAt(template.getUpdatedAt())
                        .build())
                .toList();
    }

    private List<Long> resolveTargetDeviceIds(UUID programId, ProgramPublishReq req) {
        if (req.getScope() == ProgramPublishScope.RUNNING) {
            return programAssignmentRepositoryJpa.findByProgramIdOrderByAssignedAtDesc(programId).stream()
                    .map(ProgramAssignmentEntity::getDeviceId)
                    .filter(id -> id != null && id > 0)
                    .distinct()
                    .toList();
        }

        if (req.getDeviceIds() == null || req.getDeviceIds().isEmpty()) {
            return List.of();
        }
        return req.getDeviceIds().stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private List<Long> resolveTargetDeviceIds(UUID programId, ProgramPublishScope scope, List<Long> deviceIds) {
        if (scope == ProgramPublishScope.RUNNING) {
            return programAssignmentRepositoryJpa.findByProgramIdOrderByAssignedAtDesc(programId).stream()
                    .map(ProgramAssignmentEntity::getDeviceId)
                    .filter(id -> id != null && id > 0)
                    .distinct()
                    .toList();
        }

        if (deviceIds == null || deviceIds.isEmpty()) {
            return List.of();
        }
        return deviceIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private boolean shouldAffect(boolean hasExisting, ProgramPublishMode mode) {
        if (mode == ProgramPublishMode.APPEND) {
            return !hasExisting;
        }
        return true;
    }

    private String computeAction(Integer currentVersion, int targetVersion) {
        if (currentVersion == null) {
            return ProgramScheduleConstant.ProgramPublishAction.DEPLOY;
        }
        if (currentVersion == targetVersion) {
            return ProgramScheduleConstant.ProgramPublishAction.NO_CHANGE;
        }
        return currentVersion < targetVersion
                ? ProgramScheduleConstant.ProgramPublishAction.UPDATE
                : ProgramScheduleConstant.ProgramPublishAction.ROLLBACK;
    }

    private DeviceCommandReq buildProgramDirtyCommand(long deviceId, String commandId) {
        return DeviceCommandReq.builder()
                .deviceId(deviceId)
                .commandId(commandId)
                .authorUrl(ProgramScheduleConstant.DeviceCommandDefaults.AUTHOR_URL_EMPTY)
                .karma(ProgramScheduleConstant.DeviceCommandDefaults.KARMA_DEFAULT)
                .content(DeviceCommandReq.Content.builder().raw(ProgramScheduleConstant.DeviceCommandRaw.PROGRAM_DIRTY).build())
                .build();
    }

    private DeviceCommandReq buildDeleteVsnCommand(long deviceId, String commandId, String source, String vsnName) {
        return DeviceCommandReq.builder()
                .deviceId(deviceId)
                .commandId(commandId)
                .authorUrl("api/vsns/sources/" + source + "/vsns/" + vsnName)
                .karma(3)
                .content(DeviceCommandReq.Content.builder().raw(DEVICE_COMMAND_RAW_DELETE_VSN).build())
                .build();
    }

    private void applyDeviceCommandResults(
            DeviceCommandResp resp,
            Map<String, ProgramPublishDeviceResultResp> resultByCommandId) {
        if (resp == null || resp.getResults() == null || resultByCommandId == null || resultByCommandId.isEmpty()) {
            return;
        }
        for (DeviceCommandResp.CommandResult cr : resp.getResults()) {
            if (cr == null || cr.getCommandId() == null) {
                continue;
            }
            ProgramPublishDeviceResultResp r = resultByCommandId.get(cr.getCommandId());
            if (r == null) {
                continue;
            }
            r.setAccepted(cr.isAccepted());
            r.setQueuedId(cr.getQueuedId());
            r.setErrorMessage(cr.getErrorMessage());
        }
    }

    private void markUntrackedProgramDirtyQueues(List<ProgramPublishDeviceResultResp> results) {
        if (untrackedDeviceCommandRegistry == null || results == null || results.isEmpty()) {
            return;
        }
        for (ProgramPublishDeviceResultResp r : results) {
            if (r == null || !r.isAccepted() || r.getDeviceId() == null || r.getQueuedId() == null) {
                continue;
            }
            if (!StringUtils.hasText(r.getCommandId())) {
                continue;
            }
            untrackedDeviceCommandRegistry.markByQueue(
                    r.getDeviceId(),
                    r.getQueuedId(),
                    r.getCommandId(),
                    UNTRACKED_COMMAND_TYPE_PROGRAM_DIRTY);
        }
    }

    private void markUntrackedDeleteQueues(DeviceCommandResp resp, Map<String, Long> deleteDeviceIdByCommandId) {
        if (untrackedDeviceCommandRegistry == null
                || resp == null
                || resp.getResults() == null
                || deleteDeviceIdByCommandId == null
                || deleteDeviceIdByCommandId.isEmpty()) {
            return;
        }
        for (DeviceCommandResp.CommandResult cr : resp.getResults()) {
            if (cr == null || !cr.isAccepted() || cr.getQueuedId() == null || !StringUtils.hasText(cr.getCommandId())) {
                continue;
            }
            Long deviceId = deleteDeviceIdByCommandId.get(cr.getCommandId());
            if (deviceId == null) {
                continue;
            }
            untrackedDeviceCommandRegistry.markByQueue(
                    deviceId,
                    cr.getQueuedId(),
                    cr.getCommandId(),
                    UNTRACKED_COMMAND_TYPE_PROGRAM_DELETE);
        }
    }

    private String resolveVsnFilename(Integer releaseProgramId) {
        if (releaseProgramId == null) {
            return null;
        }
        ProgramReleaseEntity release = programReleaseRepositoryJpa.findById(releaseProgramId).orElse(null);
        return buildVsnFilename(release);
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

    private DeviceCommandResp callDeviceService(List<DeviceCommandReq> commands) {
        ResponseEntity<ApiResponse<DeviceCommandResp>> response;
        try {
            response = deviceInternalClient.sendCommand(commands);
        } catch (Exception ex) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "调用 device-service 下发节目发布指令失败", ex);
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

    private boolean isDeviceServiceSuccessCode(String code) {
        return "200".equals(code) || ErrorCode.SUCCESS.getCode().equals(code);
    }

    private ProgramReleaseEntity createNewRelease(UUID userId, String tier, ProgramEntity program, ProgramPublishReq req, OffsetDateTime now) {
        ProgramReleaseEntity latest = programReleaseRepositoryJpa.findTopByProgramIdOrderByVersionDesc(program.getId()).orElse(null);
        int nextVersion = latest != null && latest.getVersion() != null ? latest.getVersion() + 1 : 1;
        Integer versionLimit = subscriptionQuotaFacade.getQuota(tier).programVersionLimit();
        if (versionLimit != null && versionLimit >= 0 && nextVersion > versionLimit) {
            programQuotaSignalPublisher.publishProgramVersionsExceeded(userId, tier, program.getId(), nextVersion, versionLimit);
            throw new BizException(ErrorCode.PROGRAM_VERSION_LIMIT_EXCEEDED);
        }

        ProgramDraftEntity draft = null;
        if (req.getDraftId() != null) {
            draft = programDraftRepositoryJpa.findById(req.getDraftId())
                    .orElseThrow(() -> new BizException(ErrorCode.PROGRAM_DRAFT_NOT_FOUND));
            if (!program.getId().equals(draft.getProgramId())) {
                throw new BizException(ErrorCode.PROGRAM_DRAFT_NOT_FOUND);
            }
        }

        String sourceVsnJson = draft != null ? draft.getVsnJson() : req.getVsnJson();
        if (!StringUtils.hasText(sourceVsnJson)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "vsnJson is required for CREATE");
        }

        ObjectMapper objectMapper = JsonUtils.getDefaultObjectMapper();
        JsonNode root;
        try {
            root = objectMapper.readTree(sourceVsnJson);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PROGRAM_VSN_JSON_INVALID, "vsnJson is not valid JSON", e);
        }
        if (!(root instanceof ObjectNode)) {
            throw new BizException(ErrorCode.PROGRAM_VSN_JSON_INVALID, "vsnJson root must be an object");
        }
        if (root.get(ProgramVsnConstants.KEY_PROGRAMS) == null) {
            throw new BizException(ErrorCode.PROGRAM_VSN_JSON_INVALID, "vsnJson must contain root.Programs");
        }

        Set<String> resourceIds = new HashSet<>();
        collectResourceIds(root, resourceIds);

        Map<String, MaterialFile> materialById = new HashMap<>();
        for (String resourceId : resourceIds) {
            if (!StringUtils.hasText(resourceId) || ProgramVsnConstants.RESOURCE_ID_EMPTY.equals(resourceId)) {
                continue;
            }

            MediaAssetEntity asset = mediaAssetRepository.findWithFilesByIdAndUserId(resourceId, userId)
                    .orElseThrow(() -> new BizException(ErrorCode.MEDIA_ASSET_NOT_FOUND, "material not found: " + resourceId));

            FileEntity originalFile = asset.getOriginalFile();
            if (originalFile == null) {
                throw new BizException(ErrorCode.MEDIA_MISSING_ORIGINAL_FILE, "material missing original file: " + resourceId);
            }
            if (!StringUtils.hasText(originalFile.getS3Key())) {
                throw new BizException(ErrorCode.PROGRAM_MATERIAL_INVALID, "material missing s3Key: " + resourceId);
            }
            if (!StringUtils.hasText(originalFile.getMd5()) || originalFile.getSize() == null) {
                throw new BizException(ErrorCode.PROGRAM_MATERIAL_INVALID, "material md5/size is required: " + resourceId);
            }

            String md5 = originalFile.getMd5().trim().toUpperCase(Locale.ROOT);
            long sizeBytes = originalFile.getSize();
            String contentType = ContentTypeUtils.normalize(originalFile.getMimeType(), "application/octet-stream");
            String ext = FileNameUtils.resolveExtensionOrDefault(originalFile.getS3Key(), contentType, "bin");

            String fileBaseName = "F_" + md5 + "_" + sizeBytes;
            String filename = fileBaseName + "." + ext;
            String filePath = storagePathProperties.getProgram().getVsnResFilesPath() + filename;
            String originName = resourceId;

            materialById.put(resourceId, new MaterialFile(
                    resourceId,
                    originalFile.getS3Key(),
                    md5,
                    sizeBytes,
                    contentType,
                    filename,
                    originName,
                    filePath));
        }

        fillFileSources(root, materialById);
        removeInternalFields(root);
        removeNullFields(root);

        String frozenVsnJson;
        try {
            frozenVsnJson = objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_SERVER_ERROR, "serialize vsnJson failed", e);
        }

        String vsnXml = renderVsnXml(root);
        byte[] vsnBytes = vsnXml.getBytes(StandardCharsets.UTF_8);

        String vsnMd5 = md5Hex(vsnBytes);
        long vsnSizeBytes = vsnBytes.length;

        String deviceTitleSnapshot = sanitizeDeviceTitleSnapshot(program.getName() + "-v" + nextVersion);
        String vsnFilename = deviceTitleSnapshot + "_" + vsnMd5 + "_" + vsnSizeBytes + ".vsn";
        String vsnObjectKey = putReleaseObject(program.getId(), nextVersion, vsnFilename, vsnBytes, "application/octet-stream");

        CoverInfo coverInfo = buildReleaseCover(program.getId(), nextVersion, req, draft);

        ProgramManifest manifest = new ProgramManifest(
                new ManifestFile(vsnFilename, vsnObjectKey, vsnMd5, vsnSizeBytes, "application/octet-stream", null),
                materialById.values().stream()
                        .map(m -> new ManifestFile(m.filename(), m.objectKey(), m.md5(), m.sizeBytes(), m.contentType(), m.resourceId()))
                        .toList());

        ProgramReleaseEntity release = ProgramReleaseEntity.builder()
                .programId(program.getId())
                .version(nextVersion)
                .deviceTitleSnapshot(deviceTitleSnapshot)
                .sourceDraftId(draft != null ? draft.getDraftId() : null)
                .vsnJson(frozenVsnJson)
                .vsnObjectKey(vsnObjectKey)
                .vsnMd5(vsnMd5)
                .vsnSizeBytes(vsnSizeBytes)
                .coverObjectKey(coverInfo.objectKey())
                .coverContentType(coverInfo.contentType())
                .coverSizeBytes(coverInfo.sizeBytes())
                .manifestJson(JsonUtils.toJson(manifest))
                .createdAt(now)
                .build();

        ProgramReleaseEntity saved = programReleaseRepositoryJpa.save(release);
        if (saved.getDeviceProgramId() == null) {
            throw new BizException(ErrorCode.INTERNAL_SERVER_ERROR, "deviceProgramId not generated");
        }
        if (saved.getVersion() != null) {
            programQuotaSignalPublisher.publishProgramVersionsUpdated(userId, tier, program.getId(), saved.getVersion(), versionLimit);
        }
        return saved;
    }

    private record MaterialFile(
            String resourceId,
            String objectKey,
            String md5,
            long sizeBytes,
            String contentType,
            String filename,
            String originName,
            String filePath) {
    }

    private record ManifestFile(
            String filename,
            String objectKey,
            String md5,
            long sizeBytes,
            String contentType,
            String resourceId) {
    }

    private record ProgramManifest(ManifestFile vsn, List<ManifestFile> materials) {
    }

    private record CoverInfo(String objectKey, String contentType, Long sizeBytes) {
    }

    private CoverInfo buildReleaseCover(UUID programId, int version, ProgramPublishReq req, ProgramDraftEntity draft) {
        if (StringUtils.hasText(req.getCoverBase64())) {
            CoverUpload coverUpload = parseCover(req.getCoverBase64(), req.getCoverContentType());
            String normalized = ContentTypeUtils.normalize(coverUpload.contentType(), "application/octet-stream");
            String ext = ContentTypeUtils.guessExtensionOrDefault(normalized, "bin");
            String objectKey = buildReleaseObjectKey(programId, version, "cover." + ext);

            putObject(objectKey, coverUpload.bytes(), normalized);
            return new CoverInfo(objectKey, normalized, (long) coverUpload.bytes().length);
        }

        if (draft != null && StringUtils.hasText(draft.getCoverObjectKey())) {
            String normalized = ContentTypeUtils.normalize(draft.getCoverContentType(), "application/octet-stream");
            String ext = ContentTypeUtils.guessExtensionOrDefault(normalized, "bin");
            String objectKey = buildReleaseObjectKey(programId, version, "cover." + ext);
            copyObject(draft.getCoverObjectKey(), objectKey);
            return new CoverInfo(objectKey, normalized, draft.getCoverSizeBytes());
        }

        return new CoverInfo(null, null, null);
    }

    private void putObject(String objectKey, byte[] bytes, String contentType) {
        if (!StringUtils.hasText(s3Bucket)) {
            throw new BizException(ErrorCode.INTERNAL_SERVER_ERROR, "S3 bucket not configured");
        }
        var request = PutObjectRequest.builder()
                .bucket(s3Bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(bytes));
    }

    private void copyObject(String sourceKey, String destKey) {
        if (!StringUtils.hasText(s3Bucket)) {
            throw new BizException(ErrorCode.INTERNAL_SERVER_ERROR, "S3 bucket not configured");
        }
        if (!StringUtils.hasText(sourceKey) || !StringUtils.hasText(destKey)) {
            return;
        }
        s3Client.copyObject(CopyObjectRequest.builder()
                .copySource(s3Bucket + "/" + sourceKey)
                .bucket(s3Bucket)
                .key(destKey)
                .build());
    }

    private String putReleaseObject(UUID programId, int version, String filename, byte[] bytes, String contentType) {
        String objectKey = buildReleaseObjectKey(programId, version, filename);
        putObject(objectKey, bytes, contentType);
        return objectKey;
    }

    private String buildReleaseObjectKey(UUID programId, int version, String filename) {
        StoragePathProperties.Program program = storagePathProperties.getProgram();
        String versionDir = program.getReleaseVersionPrefix() + version;
        return ObjectKeyUtils.join(
                program.getRootPrefix(),
                programId.toString(),
                program.getReleaseDir(),
                versionDir,
                filename);
    }

    private void collectResourceIds(JsonNode node, Set<String> out) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            var obj = (ObjectNode) node;
            JsonNode idNode = obj.get(ProgramVsnConstants.KEY_RESOURCE_ID);
            if (idNode != null && !idNode.isNull() && idNode.isValueNode()) {
                String id = idNode.asText();
                if (StringUtils.hasText(id) && !ProgramVsnConstants.RESOURCE_ID_EMPTY.equals(id)) {
                    out.add(id);
                }
            }

            obj.fieldNames().forEachRemaining(field -> {
                if (field != null && field.startsWith(ProgramVsnConstants.INTERNAL_FIELD_PREFIX)) {
                    return;
                }
                collectResourceIds(obj.get(field), out);
            });
            return;
        }

        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (JsonNode child : array) {
                collectResourceIds(child, out);
            }
        }
    }

    private void fillFileSources(JsonNode node, Map<String, MaterialFile> materialsById) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;

            JsonNode idNode = obj.get(ProgramVsnConstants.KEY_RESOURCE_ID);
            if (idNode != null && !idNode.isNull() && idNode.isValueNode()) {
                String resourceId = idNode.asText();
                if (StringUtils.hasText(resourceId) && !ProgramVsnConstants.RESOURCE_ID_EMPTY.equals(resourceId)) {
                    MaterialFile material = materialsById.get(resourceId);
                    if (material == null) {
                        throw new BizException(ErrorCode.MEDIA_ASSET_NOT_FOUND, "material not found: " + resourceId);
                    }
                    obj.put(ProgramVsnConstants.KEY_IS_RELATIVE, ProgramVsnConstants.FLAG_TRUE);
                    obj.put(ProgramVsnConstants.KEY_ORIGIN_NAME, material.originName());
                    obj.put(ProgramVsnConstants.KEY_FILE_PATH, material.filePath());
                }
            }

            // 避免边遍历边修改导致迭代器异常：先拷贝 keys
            java.util.List<String> keys = new java.util.ArrayList<>();
            obj.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) {
                if (key != null && key.startsWith(ProgramVsnConstants.INTERNAL_FIELD_PREFIX)) {
                    continue;
                }
                fillFileSources(obj.get(key), materialsById);
            }
            return;
        }

        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (JsonNode child : array) {
                fillFileSources(child, materialsById);
            }
        }
    }

    private String renderVsnXml(JsonNode root) {
        if (!(root instanceof ObjectNode obj)) {
            throw new BizException(ErrorCode.PROGRAM_VSN_JSON_INVALID, "vsnJson root must be an object");
        }
        JsonNode programs = obj.get(ProgramVsnConstants.KEY_PROGRAMS);
        if (programs == null || programs.isNull()) {
            throw new BizException(ErrorCode.PROGRAM_VSN_JSON_INVALID, "vsnJson must contain Programs");
        }

        ObjectMapper jsonMapper = JsonUtils.getDefaultObjectMapper();
        Object value = jsonMapper.convertValue(programs, Object.class);

        JacksonXmlModule module = new JacksonXmlModule();
        module.setDefaultUseWrapper(false);
        XmlMapper xmlMapper = new XmlMapper(module);

        try {
            String xmlBody = xmlMapper.writer().withRootName(ProgramVsnConstants.KEY_PROGRAMS).writeValueAsString(value);
            return ProgramVsnConstants.VSN_XML_DECLARATION + xmlBody;
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_SERVER_ERROR, "serialize vsn xml failed", e);
        }
    }

    private void removeInternalFields(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            java.util.List<String> keys = new java.util.ArrayList<>();
            obj.fieldNames().forEachRemaining(keys::add);

                for (String key : keys) {
                if (key != null && key.startsWith(ProgramVsnConstants.INTERNAL_FIELD_PREFIX)) {
                    obj.remove(key);
                    continue;
                }
                removeInternalFields(obj.get(key));
            }
            return;
        }

        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (JsonNode child : arr) {
                removeInternalFields(child);
            }
        }
    }

    /**
     * VSN JSON 清理：删除 null / "null" / 空字符串等无效字段，避免渲染出设备端不兼容的 XML 节点。
     * <p>例如：BgFile=null 时，设备可能会将其视为“有背景图但路径为空”，进而解析失败。</p>
     */
    private void removeNullFields(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            java.util.List<String> keys = new java.util.ArrayList<>();
            obj.fieldNames().forEachRemaining(keys::add);

            for (String key : keys) {
                if (key == null) {
                    continue;
                }
                JsonNode child = obj.get(key);
                if (child == null || child.isNull()) {
                    obj.remove(key);
                    continue;
                }
                if (child.isTextual()) {
                    String text = child.asText();
                    boolean isNullLiteral = "null".equalsIgnoreCase(text);
                    boolean isBlank = !StringUtils.hasText(text);
                    if (isNullLiteral || ("BgFile".equals(key) && isBlank)) {
                        obj.remove(key);
                        continue;
                    }
                }
                removeNullFields(child);
            }
            return;
        }

        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (JsonNode child : arr) {
                removeNullFields(child);
            }
        }
    }

    private String md5Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes != null ? bytes : new byte[0]);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BizException(ErrorCode.INTERNAL_SERVER_ERROR, "MD5 not supported", e);
        }
    }

    private String sanitizeDeviceTitleSnapshot(String raw) {
        if (!StringUtils.hasText(raw)) {
            return ProgramVsnConstants.DEFAULT_DEVICE_TITLE;
        }

        String s = raw.trim();
        s = s.replace('_', '-');
        s = s.replace('\\', '-').replace('/', '-');
        s = s.replaceAll("[<>:\"|?*]", "-");
        s = s.replaceAll("-{2,}", "-").trim();
        if (s.isBlank()) {
            s = ProgramVsnConstants.DEFAULT_DEVICE_TITLE;
        }
        if (s.length() > 160) {
            s = s.substring(0, 160);
        }
        return s;
    }

    private ProgramEntity findOwnedProgram(UUID userId, UUID programId) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (programId == null) {
            throw new BizException(ErrorCode.PROGRAM_NOT_FOUND);
        }
        return programRepositoryJpa.findByIdAndUserId(programId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.PROGRAM_NOT_FOUND));
    }

    private ProgramDraftResp toDraftResp(ProgramDraftEntity draft) {
        String coverUrl = StringUtils.hasText(draft.getCoverObjectKey()) ? mediaObjectUrlPort.toPublicUrl(draft.getCoverObjectKey()) : null;
        return ProgramDraftResp.builder()
                .draftId(draft.getDraftId())
                .programId(draft.getProgramId())
                .baseVersion(draft.getBaseVersion())
                .vsnJson(draft.getVsnJson())
                .coverUrl(coverUrl)
                .contentHash(draft.getContentHash())
                .createdAt(draft.getCreatedAt())
                .updatedAt(draft.getUpdatedAt())
                .build();
    }

    private ProgramVersionResp toVersionResp(ProgramReleaseEntity release) {
        String coverUrl = StringUtils.hasText(release.getCoverObjectKey()) ? mediaObjectUrlPort.toPublicUrl(release.getCoverObjectKey()) : null;
        return ProgramVersionResp.builder()
                .programId(release.getProgramId())
                .version(release.getVersion())
                .deviceProgramId(release.getDeviceProgramId())
                .deviceTitleSnapshot(release.getDeviceTitleSnapshot())
                .vsnMd5(release.getVsnMd5())
                .vsnSizeBytes(release.getVsnSizeBytes())
                .coverUrl(coverUrl)
                .createdAt(release.getCreatedAt())
                .build();
    }

    private Map<UUID, ProgramReleaseEntity> pickLatestReleaseByProgramId(List<ProgramReleaseEntity> releasesSorted) {
        Map<UUID, ProgramReleaseEntity> latest = new HashMap<>();
        if (releasesSorted == null) {
            return latest;
        }
        for (ProgramReleaseEntity release : releasesSorted) {
            if (release == null) {
                continue;
            }
            latest.putIfAbsent(release.getProgramId(), release);
        }
        return latest;
    }

    private Map<UUID, ProgramDraftEntity> pickLatestDraftByProgramId(List<ProgramDraftEntity> draftsSorted) {
        Map<UUID, ProgramDraftEntity> latest = new HashMap<>();
        if (draftsSorted == null) {
            return latest;
        }
        for (ProgramDraftEntity draft : draftsSorted) {
            if (draft == null) {
                continue;
            }
            latest.putIfAbsent(draft.getProgramId(), draft);
        }
        return latest;
    }

    private String buildInitialDraftVsnJson(ProgramEntity program, int baseVersion) {
        if (baseVersion > 0) {
            ProgramReleaseEntity release = programReleaseRepositoryJpa.findByProgramIdAndVersion(program.getId(), baseVersion)
                    .orElseThrow(() -> new BizException(ErrorCode.PROGRAM_VERSION_NOT_FOUND));
            return release.getVsnJson();
        }

        String w = String.valueOf(program.getWidth());
        String h = String.valueOf(program.getHeight());
        return """
                {"Programs":{"Program":{"Information":{"Width":"%s","Height":"%s","Scale":null},"Pages":{"Page":[]}}}}
                """.formatted(w, h).trim();
    }

    private CoverUpload parseCover(String coverBase64, String explicitContentType) {
        if (!StringUtils.hasText(coverBase64)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "coverBase64 is blank");
        }

        String trimmed = coverBase64.trim();
        String contentType = StringUtils.hasText(explicitContentType) ? explicitContentType.trim().toLowerCase(Locale.ROOT) : null;
        String base64Payload = trimmed;

        if (trimmed.startsWith("data:")) {
            int comma = trimmed.indexOf(',');
            if (comma > 0) {
                String meta = trimmed.substring(5, comma); // skip "data:"
                base64Payload = trimmed.substring(comma + 1);
                int semi = meta.indexOf(';');
                contentType = semi > 0 ? meta.substring(0, semi) : meta;
            }
        }

        if (!StringUtils.hasText(contentType)) {
            contentType = "image/png";
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Payload.trim());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "invalid coverBase64", e);
        }

        return new CoverUpload(bytes, contentType);
    }

    private record CoverUpload(byte[] bytes, String contentType) {
    }

    private String putDraftCover(UUID programId, UUID draftId, byte[] bytes, String contentType) {
        if (!StringUtils.hasText(s3Bucket)) {
            throw new BizException(ErrorCode.INTERNAL_SERVER_ERROR, "S3 bucket not configured");
        }

        String normalizedContentType = ContentTypeUtils.normalize(contentType, "application/octet-stream");
        String ext = ContentTypeUtils.guessExtensionOrDefault(normalizedContentType, "bin");
        StoragePathProperties.Program program = storagePathProperties.getProgram();
        String objectKey = ObjectKeyUtils.join(
                program.getRootPrefix(),
                programId.toString(),
                program.getDraftDir(),
                draftId.toString(),
                "cover." + ext);

        var request = PutObjectRequest.builder()
                .bucket(s3Bucket)
                .key(objectKey)
                .contentType(normalizedContentType)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(bytes));
        return objectKey;
    }

    private void writeAudit(UUID userId, UUID programId, ProgramAuditAction action, String detailsJson) {
        if (userId == null || programId == null || action == null) {
            return;
        }
        ProgramAuditLogEntity entity = ProgramAuditLogEntity.builder()
                .id(IdGenerator.nextId())
                .userId(userId)
                .programId(programId)
                .action(action)
                .details(StringUtils.hasText(detailsJson) ? detailsJson : null)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        programAuditLogRepositoryJpa.save(entity);
    }

    private ProgramAuditLogResp toAuditLogResp(ProgramAuditLogEntity logEntity) {
        if (logEntity == null) {
            return null;
        }
        return ProgramAuditLogResp.builder()
                .id(logEntity.getId())
                .userId(logEntity.getUserId())
                .programId(logEntity.getProgramId())
                .action(logEntity.getAction())
                .details(logEntity.getDetails())
                .createdAt(logEntity.getCreatedAt())
                .build();
    }
}
