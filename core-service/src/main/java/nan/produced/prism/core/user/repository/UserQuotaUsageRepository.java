package nan.produced.prism.core.user.repository;

import nan.produced.prism.core.user.domain.UserQuotaUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * 用户资源配额使用情况
 */
@Repository
public interface UserQuotaUsageRepository extends JpaRepository<UserQuotaUsageEntity, UUID> {


}
