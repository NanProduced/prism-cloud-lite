package nan.produced.prism.core.assistant.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.assistant.domain.AssistantToolCallAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssistantToolCallAuditRepositoryJpa extends JpaRepository<AssistantToolCallAuditEntity, UUID> {

    List<AssistantToolCallAuditEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<AssistantToolCallAuditEntity> findByToolNameOrderByCreatedAtDesc(String toolName);

    List<AssistantToolCallAuditEntity> findByToolCallId(String toolCallId);

    List<AssistantToolCallAuditEntity> findByUserIdAndSuccessOrderByCreatedAtDesc(UUID userId, Boolean success);
}
