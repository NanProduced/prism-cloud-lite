package nan.produced.prism.core.program.application.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.util.VsnFilenameUtils;
import nan.produced.prism.core.device.api.dto.DeleteDeviceProgramResp;
import nan.produced.prism.core.device.api.dto.DeviceActionDispatchResp;
import nan.produced.prism.core.device.application.port.inbound.DeviceActionDispatchUseCase;
import nan.produced.prism.core.device.domain.command.action.ClearProgramAction;
import nan.produced.prism.core.device.domain.command.action.DeleteDeviceVsnAction;
import nan.produced.prism.core.program.domain.ProgramAssignmentEntity;
import nan.produced.prism.core.program.domain.ProgramDeploymentEntity;
import nan.produced.prism.core.program.domain.ProgramReleaseEntity;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramAssignmentRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramDeploymentRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramReleaseRepositoryJpa;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DeviceProgramCommandApplicationService {

    private final DeviceActionDispatchUseCase deviceActionDispatchUseCase;
    private final ProgramAssignmentRepositoryJpa programAssignmentRepositoryJpa;
    private final ProgramDeploymentRepositoryJpa programDeploymentRepositoryJpa;
    private final ProgramReleaseRepositoryJpa programReleaseRepositoryJpa;

    public DeviceActionDispatchResp clearAllPrograms(UUID userId, Long deviceId) {
        return deviceActionDispatchUseCase.dispatchSingle(userId, deviceId, new ClearProgramAction());
    }

    public DeleteDeviceProgramResp deleteProgram(UUID userId, Long deviceId, UUID programId, String vsnName, String source) {
        UUID normalizedProgramId = programId;
        String resolvedVsnName = StringUtils.hasText(vsnName) ? normalizeVsnName(vsnName) : null;

        if (normalizedProgramId == null && resolvedVsnName == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "programId or vsnName is required");
        }
        if (normalizedProgramId != null && resolvedVsnName != null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "programId and vsnName cannot be used together");
        }

        if (resolvedVsnName == null) {
            ProgramReleaseEntity release = resolveDeviceReleaseByProgramId(deviceId, normalizedProgramId);
            resolvedVsnName = buildVsnFilename(release);
            if (!StringUtils.hasText(resolvedVsnName)) {
                throw new BizException(ErrorCode.PROGRAM_VERSION_NOT_FOUND, "failed to resolve vsnName for programId=" + normalizedProgramId);
            }
        }

        List<String> sources = resolveSourcesToTry(source);
        List<DeviceActionDispatchResp> results = new ArrayList<>();
        for (String s : sources) {
            DeleteDeviceVsnAction action = new DeleteDeviceVsnAction();
            action.setSource(s);
            action.setVsnName(resolvedVsnName);
            results.add(deviceActionDispatchUseCase.dispatchSingle(userId, deviceId, action));
        }

        return DeleteDeviceProgramResp.builder()
            .programId(normalizedProgramId)
            .vsnName(resolvedVsnName)
            .sources(sources)
            .results(results)
            .build();
    }

    private ProgramReleaseEntity resolveDeviceReleaseByProgramId(Long deviceId, UUID programId) {
        if (deviceId == null || deviceId <= 0) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "deviceId is invalid");
        }
        if (programId == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "programId is required");
        }

        ProgramAssignmentEntity assignment = programAssignmentRepositoryJpa.findByProgramIdAndDeviceId(programId, deviceId).orElse(null);
        Integer releaseProgramId = assignment != null ? assignment.getReleaseProgramId() : null;

        if (releaseProgramId == null) {
            ProgramDeploymentEntity deployment = programDeploymentRepositoryJpa.findByProgramIdAndDeviceId(programId, deviceId).orElse(null);
            releaseProgramId = deployment != null ? deployment.getReleaseProgramId() : null;
        }

        if (releaseProgramId == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "program is not deployed on this device");
        }

        Integer resolvedReleaseProgramId = releaseProgramId;
        return programReleaseRepositoryJpa.findById(resolvedReleaseProgramId)
            .orElseThrow(() -> new BizException(ErrorCode.PROGRAM_VERSION_NOT_FOUND, "release not found: " + resolvedReleaseProgramId));
    }

    private List<String> resolveSourcesToTry(String source) {
        Set<String> sources = new LinkedHashSet<>();
        if (StringUtils.hasText(source)) {
            sources.add(normalizeSource(source));
        } else {
            sources.add("internet");
            sources.add("lan");
        }
        return new ArrayList<>(sources);
    }

    private String normalizeSource(String source) {
        String s = source.trim().toLowerCase(Locale.ROOT);
        if (!"internet".equals(s) && !"lan".equals(s)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "invalid source: " + source);
        }
        return s;
    }

    private String normalizeVsnName(String vsnName) {
        String raw = vsnName.trim();
        int lastSlash = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'));
        String fileName = lastSlash >= 0 && lastSlash + 1 < raw.length() ? raw.substring(lastSlash + 1) : raw;
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".vsn")) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "invalid vsnName (missing .vsn): " + vsnName);
        }
        if (VsnFilenameUtils.parseVsnMeta(fileName) == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "invalid vsnName: " + vsnName);
        }
        return fileName;
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
