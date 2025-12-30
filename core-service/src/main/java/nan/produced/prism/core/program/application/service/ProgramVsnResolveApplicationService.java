package nan.produced.prism.core.program.application.service;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.util.VsnFilenameUtils;
import nan.produced.prism.core.program.api.dto.ProgramResolveByVsnResp;
import nan.produced.prism.core.program.domain.ProgramEntity;
import nan.produced.prism.core.program.domain.ProgramReleaseEntity;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramReleaseRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramRepositoryJpa;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProgramVsnResolveApplicationService {

    private final ProgramReleaseRepositoryJpa programReleaseRepositoryJpa;
    private final ProgramRepositoryJpa programRepositoryJpa;

    @Transactional(readOnly = true)
    public ProgramResolveByVsnResp resolveByVsn(UUID userId, String vsnFilename) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (!StringUtils.hasText(vsnFilename)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "vsn is required");
        }

        VsnFilenameUtils.VsnMeta meta = VsnFilenameUtils.parseVsnMeta(vsnFilename);
        if (meta == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "invalid vsn: " + vsnFilename);
        }

        ProgramReleaseEntity release = programReleaseRepositoryJpa
            .findByUserIdAndVsnMd5AndVsnSizeBytes(userId, meta.vsnMd5(), meta.vsnSizeBytes())
            .orElseThrow(() -> new BizException(ErrorCode.PROGRAM_VERSION_NOT_FOUND));

        String programName = null;
        if (release.getProgramId() != null) {
            programName = programRepositoryJpa.findByIdAndUserId(release.getProgramId(), userId)
                .map(ProgramEntity::getName)
                .orElse(null);
        }

        return ProgramResolveByVsnResp.builder()
            .programId(release.getProgramId())
            .programName(programName)
            .version(release.getVersion())
            .releaseProgramId(release.getDeviceProgramId())
            .vsnMd5(release.getVsnMd5())
            .vsnSizeBytes(release.getVsnSizeBytes())
            .build();
    }
}
