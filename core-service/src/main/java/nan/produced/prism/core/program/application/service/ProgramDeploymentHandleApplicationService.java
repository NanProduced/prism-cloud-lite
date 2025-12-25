package nan.produced.prism.core.program.application.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.program.application.port.inbound.ProgramDeploymentHandleFacade;
import nan.produced.prism.core.program.domain.ProgramDeploymentEntity;
import nan.produced.prism.core.program.domain.ProgramDeploymentStatus;
import nan.produced.prism.core.program.domain.ProgramReleaseEntity;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramDeploymentRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramReleaseRepositoryJpa;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProgramDeploymentHandleApplicationService implements ProgramDeploymentHandleFacade {

    private final ProgramDeploymentRepositoryJpa programDeploymentRepositoryJpa;
    private final ProgramReleaseRepositoryJpa programReleaseRepositoryJpa;

    @Override
    @Transactional
    public void handleProgramDeploymentConsistency(Long deviceId, List<String> programVsnList) {
        if (deviceId == null || deviceId <= 0 || programVsnList == null || programVsnList.isEmpty()) {
            return;
        }

        UUID userId = resolveUserIdByDeviceId(deviceId);
        if (userId == null) {
            return;
        }

        handleProgramDeploymentConsistency(userId, deviceId, programVsnList);
    }

    @Transactional
    public void handleProgramDeploymentConsistency(UUID userId, Long deviceId, List<String> programVsnList) {
        if (userId == null || deviceId == null || deviceId <= 0 || programVsnList == null || programVsnList.isEmpty()) {
            return;
        }

        Map<UUID, ProgramReleaseEntity> latestReleaseByProgramId = resolveLatestReleaseByProgramId(userId, programVsnList);
        if (latestReleaseByProgramId.isEmpty()) {
            // 无法解析任何 release 时，不做删除/写入，避免误清空 deployment。
            return;
        }

        List<ProgramDeploymentEntity> existingDeployments = programDeploymentRepositoryJpa.findByDeviceIdOrderByAssignedAtDesc(deviceId);

        Map<UUID, ProgramDeploymentEntity> existingByProgramId = new HashMap<>();
        if (existingDeployments != null) {
            for (ProgramDeploymentEntity deployment : existingDeployments) {
                if (deployment == null || deployment.getProgramId() == null) {
                    continue;
                }
                existingByProgramId.putIfAbsent(deployment.getProgramId(), deployment);
            }
        }

        // 1) 删除设备上已不存在的节目 deployment
        Set<UUID> currentProgramIds = latestReleaseByProgramId.keySet();
        List<ProgramDeploymentEntity> toDelete = new ArrayList<>();
        if (existingDeployments != null) {
            for (ProgramDeploymentEntity deployment : existingDeployments) {
                if (deployment == null || deployment.getProgramId() == null) {
                    continue;
                }
                if (!currentProgramIds.contains(deployment.getProgramId())) {
                    toDelete.add(deployment);
                }
            }
        }
        if (!toDelete.isEmpty()) {
            programDeploymentRepositoryJpa.deleteAllInBatch(toDelete);
        }

        // 2) upsert 当前节目，存在则对齐 release 并标记为已下载
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<ProgramDeploymentEntity> toUpsert = new ArrayList<>();

        for (ProgramReleaseEntity release : latestReleaseByProgramId.values()) {
            if (release == null || release.getProgramId() == null || release.getDeviceProgramId() == null || release.getVersion() == null) {
                continue;
            }

            UUID programId = release.getProgramId();
            ProgramDeploymentEntity deployment = existingByProgramId.get(programId);

            if (deployment == null) {
                toUpsert.add(ProgramDeploymentEntity.builder()
                        .programId(programId)
                        .deviceId(deviceId)
                        .userId(userId)
                        .releaseVersion(release.getVersion())
                        .releaseProgramId(release.getDeviceProgramId())
                        .assignedAt(now)
                        .status(ProgramDeploymentStatus.DOWNLOADED)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
                continue;
            }

            boolean changed = false;
            boolean releaseChanged = false;

            if (!Objects.equals(deployment.getUserId(), userId)) {
                deployment.setUserId(userId);
                changed = true;
            }
            if (!Objects.equals(deployment.getReleaseVersion(), release.getVersion())) {
                deployment.setReleaseVersion(release.getVersion());
                changed = true;
                releaseChanged = true;
            }
            if (!Objects.equals(deployment.getReleaseProgramId(), release.getDeviceProgramId())) {
                deployment.setReleaseProgramId(release.getDeviceProgramId());
                changed = true;
                releaseChanged = true;
            }
            if (deployment.getStatus() != ProgramDeploymentStatus.DOWNLOADED) {
                deployment.setStatus(ProgramDeploymentStatus.DOWNLOADED);
                changed = true;
            }

            if (releaseChanged || deployment.getAssignedAt() == null) {
                deployment.setAssignedAt(now);
                changed = true;
            }
            if (deployment.getCreatedAt() == null) {
                deployment.setCreatedAt(now);
                changed = true;
            }

            if (changed) {
                deployment.setUpdatedAt(now);
                toUpsert.add(deployment);
            }
        }

        if (!toUpsert.isEmpty()) {
            programDeploymentRepositoryJpa.saveAll(toUpsert);
        }
    }

    private UUID resolveUserIdByDeviceId(Long deviceId) {
        if (deviceId == null || deviceId <= 0) {
            return null;
        }

        List<ProgramDeploymentEntity> deployments = programDeploymentRepositoryJpa.findByDeviceIdOrderByAssignedAtDesc(deviceId);
        if (deployments == null || deployments.isEmpty()) {
            return null;
        }
        for (ProgramDeploymentEntity deployment : deployments) {
            if (deployment != null && deployment.getUserId() != null) {
                return deployment.getUserId();
            }
        }
        return null;
    }

    @Override
    @Transactional
    public void clearProgramDeployment(Long deviceId) {
        if (deviceId == null || deviceId <= 0) {
            return;
        }

        programDeploymentRepositoryJpa.deleteByDeviceId(deviceId);
    }
    
    /**
     * 解析VSN文件名，获取MD5和大小信息
     */
    private VsnMeta parseVsnMeta(String vsn) {
        if (vsn == null || vsn.trim().isEmpty()) {
            return null;
        }
        String s = vsn.trim();
        int lastSlash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash + 1 < s.length()) {
            s = s.substring(lastSlash + 1);
        }
        if (s.toLowerCase().endsWith(".vsn")) {
            s = s.substring(0, s.length() - 4);
        }

        int lastUnderscore = s.lastIndexOf('_');
        if (lastUnderscore <= 0 || lastUnderscore >= s.length() - 1) {
            return null;
        }
        int secondLastUnderscore = s.lastIndexOf('_', lastUnderscore - 1);
        if (secondLastUnderscore <= 0 || secondLastUnderscore >= lastUnderscore - 1) {
            return null;
        }

        String md5 = s.substring(secondLastUnderscore + 1, lastUnderscore).trim();
        String sizeStr = s.substring(lastUnderscore + 1).trim();
        if (!md5.matches("(?i)[0-9a-f]{32}")) {
            return null;
        }

        long sizeBytes;
        try {
            sizeBytes = Long.parseLong(sizeStr);
        } catch (NumberFormatException e) {
            return null;
        }

        return new VsnMeta(md5.toUpperCase(), sizeBytes);
    }
    
    private Map<UUID, ProgramReleaseEntity> resolveLatestReleaseByProgramId(UUID userId, List<String> programVsnList) {
        if (userId == null || programVsnList == null || programVsnList.isEmpty()) {
            return Map.of();
        }

        Map<UUID, ProgramReleaseEntity> latestByProgramId = new HashMap<>();
        Map<String, ProgramReleaseEntity> cache = new HashMap<>();
        Set<String> miss = new HashSet<>();

        for (String vsn : programVsnList) {
            VsnMeta meta = parseVsnMeta(vsn);
            if (meta == null) {
                continue;
            }

            String key = meta.md5() + ":" + meta.sizeBytes();
            if (miss.contains(key)) {
                continue;
            }

            ProgramReleaseEntity release = cache.get(key);
            if (release == null) {
                release = programReleaseRepositoryJpa
                        .findByUserIdAndVsnMd5AndVsnSizeBytes(userId, meta.md5(), meta.sizeBytes())
                        .orElse(null);
                if (release == null || release.getProgramId() == null) {
                    miss.add(key);
                    continue;
                }
                cache.put(key, release);
            }

            ProgramReleaseEntity current = latestByProgramId.get(release.getProgramId());
            if (current == null) {
                latestByProgramId.put(release.getProgramId(), release);
                continue;
            }
            if (release.getVersion() != null && current.getVersion() != null && release.getVersion() > current.getVersion()) {
                latestByProgramId.put(release.getProgramId(), release);
            }
        }

        return latestByProgramId;
    }
    
    /**
     * VSN元数据记录类
     */
    private record VsnMeta(String md5, long sizeBytes) {}
}
