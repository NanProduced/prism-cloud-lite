package nan.produced.prism.core.program.application.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.program.application.port.inbound.ProgramSearchFacade;
import nan.produced.prism.core.program.application.port.inbound.ProgramSearchItem;
import nan.produced.prism.core.program.domain.ProgramEntity;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramRepositoryJpa;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProgramSearchApplicationService implements ProgramSearchFacade {

    private final ProgramRepositoryJpa programRepositoryJpa;

    @Override
    @Transactional(readOnly = true)
    public List<ProgramSearchItem> searchPrograms(UUID userId, String keyword, Integer limit) {
        if (userId == null) {
            return List.of();
        }
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }

        int safeLimit = (limit == null || limit <= 0) ? 5 : Math.min(limit, 10);
        var page = PageRequest.of(0, safeLimit);
        List<ProgramEntity> programs = programRepositoryJpa.findByUserIdAndNameLike(userId, keyword.trim(), page);
        if (programs == null || programs.isEmpty()) {
            return List.of();
        }

        return programs.stream()
                .map(p -> new ProgramSearchItem(
                        p.getId(),
                        p.getName(),
                        p.getWidth() != null && p.getHeight() != null ? (p.getWidth() + "x" + p.getHeight()) : null,
                        p.getUpdatedAt()
                ))
                .toList();
    }
}

