package nan.produced.prism.core.user.repository;

import nan.produced.prism.core.user.domain.UserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 用户资料 Repository
 */
@Repository
public interface UserProfileRepository extends JpaRepository<UserProfileEntity, UUID> {

    /**
     * 根据邮箱查询用户（不区分大小写）
     * @param email 邮箱地址
     * @return 用户资料实体
     */
    Optional<UserProfileEntity> findByEmailIgnoreCase(String email);

    /**
     * 根据公共ID查询用户
     * @param publicId 公共标识符
     * @return 用户资料实体
     */
    Optional<UserProfileEntity> findByPublicId(String publicId);
}
