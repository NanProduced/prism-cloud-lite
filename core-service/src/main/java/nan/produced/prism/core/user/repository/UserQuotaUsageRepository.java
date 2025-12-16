package nan.produced.prism.core.user.repository;

import nan.produced.prism.core.user.domain.UserQuotaUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 用户资源配额使用情况
 *
 * @author Nan
 */
@Repository
public interface UserQuotaUsageRepository extends JpaRepository<UserQuotaUsageEntity, UUID> {

    /**
     * 根据用户ID查找配额使用记录
     */
    Optional<UserQuotaUsageEntity> findByUserId(UUID userId);

    /**
     * 增加存储总量（原子操作）
     *
     * @param userId 用户ID
     * @param bytes  字节数增量
     * @return 更新的行数
     */
    @Modifying
    @Query("UPDATE UserQuotaUsageEntity u SET u.storageTotalBytes = u.storageTotalBytes + :bytes WHERE u.userId = :userId")
    int incrementStorageTotalBytes(@Param("userId") UUID userId, @Param("bytes") long bytes);

    @Modifying
    @Query("UPDATE UserQuotaUsageEntity u SET u.deviceCount = u.deviceCount + :delta WHERE u.userId = :userId")
    int incrementDeviceCount(@Param("userId") UUID userId, @Param("delta") int delta);

    @Modifying
    @Query("""
            UPDATE UserQuotaUsageEntity u
            SET u.deviceCount = u.deviceCount + :delta
            WHERE u.userId = :userId
              AND (u.deviceCount + :delta) <= :limit
            """)
    int incrementDeviceCountIfWithinLimit(
            @Param("userId") UUID userId,
            @Param("delta") int delta,
            @Param("limit") int limit);

    @Modifying
    @Query("UPDATE UserQuotaUsageEntity u SET u.customColumnCount = u.customColumnCount + :delta WHERE u.userId = :userId")
    int incrementCustomColumnCount(@Param("userId") UUID userId, @Param("delta") int delta);

    @Modifying
    @Query("""
            UPDATE UserQuotaUsageEntity u
            SET u.customColumnCount = u.customColumnCount + :delta
            WHERE u.userId = :userId
              AND (u.customColumnCount + :delta) <= :limit
            """)
    int incrementCustomColumnCountIfWithinLimit(
            @Param("userId") UUID userId,
            @Param("delta") int delta,
            @Param("limit") int limit);
}
