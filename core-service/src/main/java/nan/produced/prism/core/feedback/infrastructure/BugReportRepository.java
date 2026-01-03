package nan.produced.prism.core.feedback.infrastructure;

import java.util.UUID;
import nan.produced.prism.core.feedback.domain.BugReportEntity;
import nan.produced.prism.core.feedback.domain.BugReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BugReportRepository extends JpaRepository<BugReportEntity, UUID> {

    Page<BugReportEntity> findByStatusOrderByCreatedAtDesc(BugReportStatus status, Pageable pageable);

    Page<BugReportEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

