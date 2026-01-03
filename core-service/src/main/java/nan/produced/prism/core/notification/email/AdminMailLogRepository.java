package nan.produced.prism.core.notification.email;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminMailLogRepository extends JpaRepository<AdminMailLogEntity, UUID> {
}

