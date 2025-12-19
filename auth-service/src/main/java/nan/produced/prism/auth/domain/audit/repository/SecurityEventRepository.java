package nan.produced.prism.auth.domain.audit.repository;

import java.util.UUID;
import nan.produced.prism.auth.domain.audit.SecurityEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityEventRepository extends JpaRepository<SecurityEventEntity, Long> {

    Page<SecurityEventEntity> findByUserId(UUID userId, Pageable pageable);
}

