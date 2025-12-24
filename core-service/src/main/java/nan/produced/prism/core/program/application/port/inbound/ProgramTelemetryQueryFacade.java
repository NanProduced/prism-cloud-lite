package nan.produced.prism.core.program.application.port.inbound;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ProgramTelemetryQueryFacade {

    Map<UUID, String> findProgramNamesByIds(UUID userId, Set<UUID> programIds);

    Optional<ReleaseInfo> findReleaseByVsnMeta(UUID userId, String vsnMd5, Long vsnSizeBytes);

    record ReleaseInfo(UUID programId, Integer version) {
    }
}

