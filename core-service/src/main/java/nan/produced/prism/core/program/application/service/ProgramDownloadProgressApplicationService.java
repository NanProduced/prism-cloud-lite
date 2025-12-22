package nan.produced.prism.core.program.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.util.JsonUtils;
import nan.produced.prism.core.program.application.port.inbound.ProgramDownloadProgressUseCase;
import nan.produced.prism.core.program.domain.ProgramDeploymentEntity;
import nan.produced.prism.core.program.domain.ProgramDeploymentStatus;
import nan.produced.prism.core.program.domain.ProgramEntity;
import nan.produced.prism.core.program.domain.ProgramReleaseEntity;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramDeploymentRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramReleaseRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramRepositoryJpa;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 设备下载进度上报处理（/wp-json/screen/v1/info → MQ report.downloadingProgress）。
 *
 * <p>核心目标：根据设备上报的节目下载状态，更新 pc_program_deployment.status（仅 DOWNLOADING/DOWNLOADED）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramDownloadProgressApplicationService implements ProgramDownloadProgressUseCase {

    private final ProgramDeploymentRepositoryJpa programDeploymentRepositoryJpa;
    private final ProgramReleaseRepositoryJpa programReleaseRepositoryJpa;
    private final ProgramRepositoryJpa programRepositoryJpa;

    @Override
    @Transactional
    public void handleDownloadingProgress(Long deviceId, UUID userId, String reportData, Instant occurredAt, String traceId) {
        if (deviceId == null || !StringUtils.hasText(reportData)) {
            return;
        }

        ObjectMapper objectMapper = JsonUtils.getDefaultObjectMapper();
        JsonNode report;
        try {
            report = objectMapper.readTree(reportData);
        } catch (Exception e) {
            log.debug("ProgramDownloadProgress - invalid report json, deviceId={}, traceId={}", deviceId, traceId, e);
            return;
        }

        // 只处理节目下载进度（what=program_status）；升级包下载进度在 Lite 阶段先不处理
        String what = text(report, "what");
        if (!"program_status".equals(what)) {
            return;
        }

        JsonNode downloading = report.get("downloading");
        if (downloading == null || !downloading.isObject()) {
            return;
        }
        JsonNode programs = downloading.get("programs");
        if (programs == null || !programs.isArray() || programs.isEmpty()) {
            return;
        }

        OffsetDateTime now = occurredAt != null ? occurredAt.atOffset(ZoneOffset.UTC) : OffsetDateTime.now(ZoneOffset.UTC);

        for (JsonNode program : programs) {
            Integer releaseProgramId = intValue(program, "id");
            if (releaseProgramId == null || releaseProgramId <= 0) {
                continue;
            }
            ProgramDeploymentStatus desired = isDownloaded(program) ? ProgramDeploymentStatus.DOWNLOADED : ProgramDeploymentStatus.DOWNLOADING;

            ProgramDeploymentEntity deployment = programDeploymentRepositoryJpa.findByDeviceIdAndReleaseProgramId(deviceId, releaseProgramId).orElse(null);
            if (deployment == null) {
                // 若设备在同一 program 的不同 release 之间切换（releaseProgramId 变化），用底层 programId 复用同一条记录。
                deployment = upsertDeploymentByReleaseProgramId(deviceId, userId, releaseProgramId, desired, now, traceId);
            } else {
                updateStatusIfNeeded(deployment, desired, now, deviceId, releaseProgramId, traceId);
            }
        }
    }

    /**
     * 兼容不同 releaseProgramId（Integer）映射到同一 platform programId（UUID）的情况。
     *
     * <p>当设备开始下载一个新版本时，上报的 programId(Integer) 会变化；服务端需要将其归并到 (programId, deviceId) 这条记录上。</p>
     */
    private ProgramDeploymentEntity upsertDeploymentByReleaseProgramId(
            Long deviceId,
            UUID userId,
            Integer releaseProgramId,
            ProgramDeploymentStatus desired,
            OffsetDateTime now,
            String traceId) {

        if (deviceId == null || releaseProgramId == null) {
            return null;
        }

        ProgramReleaseEntity release = programReleaseRepositoryJpa.findById(releaseProgramId).orElse(null);
        if (release == null || release.getProgramId() == null || release.getDeviceProgramId() == null || release.getVersion() == null) {
            return null;
        }

        // 防止跨用户污染：仅处理 device 所属用户的节目
        if (userId == null) {
            return null;
        }

        ProgramEntity program = programRepositoryJpa.findById(release.getProgramId()).orElse(null);
        if (program == null || program.getUserId() == null || !userId.equals(program.getUserId())) {
            log.debug("ProgramDownloadProgress - skip cross-user report, deviceId={}, releaseProgramId={}, traceId={}", deviceId, releaseProgramId, traceId);
            return null;
        }

        ProgramDeploymentEntity deployment = programDeploymentRepositoryJpa.findByProgramIdAndDeviceId(release.getProgramId(), deviceId).orElse(null);
        if (deployment == null) {
            deployment = ProgramDeploymentEntity.builder()
                    .programId(release.getProgramId())
                    .deviceId(deviceId)
                    .userId(userId)
                    .createdAt(now)
                    .build();
        } else {
            if (deployment.getUserId() != null && !userId.equals(deployment.getUserId())) {
                log.debug("ProgramDownloadProgress - skip mismatched deployment user, deviceId={}, releaseProgramId={}, traceId={}", deviceId, releaseProgramId, traceId);
                return null;
            }
        }

        deployment.setReleaseVersion(release.getVersion());
        deployment.setReleaseProgramId(release.getDeviceProgramId());
        deployment.setAssignedAt(now);
        deployment.setStatus(desired);
        deployment.setUpdatedAt(now);

        programDeploymentRepositoryJpa.save(deployment);
        log.debug("ProgramDownloadProgress - upsert deployment: deviceId={}, releaseProgramId={}, status={}, traceId={}",
                deviceId, releaseProgramId, desired, traceId);

        return deployment;
    }

    private void updateStatusIfNeeded(
            ProgramDeploymentEntity deployment,
            ProgramDeploymentStatus desired,
            OffsetDateTime now,
            Long deviceId,
            Integer releaseProgramId,
            String traceId) {
        if (deployment == null || desired == null) {
            return;
        }
        if (deployment.getStatus() != desired) {
            deployment.setStatus(desired);
            deployment.setUpdatedAt(now);
            programDeploymentRepositoryJpa.save(deployment);
            log.debug("ProgramDownloadProgress - status changed: deviceId={}, programId={}, status={}, traceId={}",
                    deviceId, releaseProgramId, desired, traceId);
        }
    }

    /**
     * 判断该节目在设备侧是否“已下载完成”。
     *
     * <p>依据统一上报协议：当且仅当该节目下所有文件均 downloaded >= total 才视为完成。</p>
     */
    private boolean isDownloaded(JsonNode program) {
        if (program == null || !program.isObject()) {
            return false;
        }

        JsonNode files = program.get("files");
        if (files == null || !files.isArray() || files.isEmpty()) {
            return false;
        }

        for (JsonNode file : files) {
            if (file == null || !file.isObject()) {
                return false;
            }
            long total = longValue(file, "total");
            long downloaded = longValue(file, "downloaded");
            if (total <= 0) {
                return false;
            }
            if (downloaded < total) {
                return false;
            }
        }

        return true;
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

    private Integer intValue(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isInt() || value.isLong() || value.isNumber()) {
            return value.intValue();
        }
        try {
            return Integer.parseInt(value.asText());
        } catch (Exception ignore) {
            return null;
        }
    }

    private long longValue(JsonNode node, String field) {
        if (node == null || field == null) {
            return 0L;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return 0L;
        }
        if (value.isLong() || value.isInt() || value.isNumber()) {
            return value.longValue();
        }
        try {
            return Long.parseLong(value.asText());
        } catch (Exception ignore) {
            return 0L;
        }
    }
}
