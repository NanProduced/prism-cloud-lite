package nan.produced.prism.core.user.repository;

import nan.produced.prism.core.user.domain.QuotaUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 用户配额使用量 Repository
 */
@Repository
public interface QuotaUsageRepository extends JpaRepository<QuotaUsageEntity, UUID> {

    /**
     * 根据用户ID查询配额使用量
     * @param userId 用户ID
     * @return 配额使用量实体
     */
    Optional<QuotaUsageEntity> findByUserId(UUID userId);
}
