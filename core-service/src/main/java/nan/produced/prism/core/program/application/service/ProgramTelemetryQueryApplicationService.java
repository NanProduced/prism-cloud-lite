package nan.produced.prism.core.program.application.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.program.application.port.inbound.ProgramTelemetryQueryFacade;
import nan.produced.prism.core.program.domain.ProgramEntity;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramReleaseRepositoryJpa;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramRepositoryJpa;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProgramTelemetryQueryApplicationService implements ProgramTelemetryQueryFacade {

    private final ProgramReleaseRepositoryJpa programReleaseRepositoryJpa;
    private final ProgramRepositoryJpa programRepositoryJpa;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> findProgramNamesByIds(UUID userId, Set<UUID> programIds) {
        if (userId == null || programIds == null || programIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, String> namesById = new HashMap<>();
        for (ProgramEntity program : programRepositoryJpa.findAllById(programIds)) {
            if (program == null) {
                continue;
            }
            if (userId.equals(program.getUserId())) {
                namesById.put(program.getId(), program.getName());
            }
        }
        return namesById;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReleaseInfo> findReleaseByVsnMeta(UUID userId, String vsnMd5, Long vsnSizeBytes) {
        if (userId == null || !StringUtils.hasText(vsnMd5) || vsnSizeBytes == null) {
            return Optional.empty();
        }

        String md5 = vsnMd5.trim();
        return programReleaseRepositoryJpa
                .findByUserIdAndVsnMd5AndVsnSizeBytes(userId, md5, vsnSizeBytes)
                .map(r -> new ReleaseInfo(r.getProgramId(), r.getVersion()));
    }
}

