package nan.produced.prism.core.assistant.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.assistant.domain.AssistantChatTokenAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssistantChatTokenAuditRepositoryJpa extends JpaRepository<AssistantChatTokenAuditEntity, UUID> {

    List<AssistantChatTokenAuditEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<AssistantChatTokenAuditEntity> findByTraceId(String traceId);

    List<AssistantChatTokenAuditEntity> findByModelOrderByCreatedAtDesc(String model);

    List<AssistantChatTokenAuditEntity> findByProviderOrderByCreatedAtDesc(String provider);
}
