package nan.produced.prism.core.device.infrastructure.persistence;

import nan.produced.prism.core.device.domain.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 设备实体 JPA 操作接口
 * 这是一个 Spring Data JPA Repository，用于直接操作 DeviceEntity
 * 作为 {@link DeviceRepositoryAdapter} 的底层数据访问接口
 *
 * @author Nan
 */
@Repository
public interface DeviceRepositoryJpa extends JpaRepository<DeviceEntity, Long> {

    /**
     * 根据设备 ID 查询设备信息
     *
     * @param deviceId 设备 ID
     * @return 设备实体，如果不存在返回 null
     */
    DeviceEntity findByDeviceId(Long deviceId);

    /**
     * 根据设备 ID 和用户 ID 查询设备信息
     *
     * @param deviceId 设备 ID
     * @param userId 用户 ID
     * @return 设备实体，如果不存在返回 null
     */
    DeviceEntity findByDeviceIdAndUserId(Long deviceId, UUID userId);

    /**
     * 查询用户的全部设备
     *
     * @param userId 用户ID
     * @return 设备列表（按创建时间倒序）
     */
    List<DeviceEntity> findByUserIdOrderByCreateTimeDesc(UUID userId);

    List<DeviceEntity> findByUserIdAndDeviceIdIn(UUID userId, Collection<Long> deviceIds);

    @Query("""
        SELECT d
          FROM DeviceEntity d
         WHERE d.userId = :userId
           AND LOWER(d.deviceName) LIKE CONCAT('%', LOWER(:keyword), '%')
        """)
    List<DeviceEntity> findByUserIdAndDeviceNameLike(@Param("userId") UUID userId,
                                                     @Param("keyword") String keyword,
                                                     Pageable pageable);

    /**
     * 统一搜索（全局搜索）使用：尽量覆盖 deviceName/description/model/version，以及 properties(JSONB) 中的 serialno/ip 等信息。
     *
     * <p>说明：Lite 用户设备量通常有限，此处使用 properties::text 作为兜底匹配可接受；后续可按需要补充专用索引。</p>
     */
    @Query(value = """
            SELECT *
              FROM pcc_device d
             WHERE d.user_id = :userId
               AND (
                    LOWER(d.device_name) LIKE CONCAT('%', LOWER(:keyword), '%')
                 OR LOWER(COALESCE(d.description, '')) LIKE CONCAT('%', LOWER(:keyword), '%')
                 OR LOWER(COALESCE(d.model, '')) LIKE CONCAT('%', LOWER(:keyword), '%')
                 OR LOWER(COALESCE(d.version, '')) LIKE CONCAT('%', LOWER(:keyword), '%')
                 OR (d.properties IS NOT NULL AND LOWER(CAST(d.properties AS TEXT)) LIKE CONCAT('%', LOWER(:keyword), '%'))
               )
            ORDER BY
                CASE
                  WHEN LOWER(d.device_name) = LOWER(:keyword) THEN 0
                  WHEN LOWER(d.device_name) LIKE CONCAT(LOWER(:keyword), '%') THEN 1
                  WHEN LOWER(d.device_name) LIKE CONCAT('%', LOWER(:keyword), '%') THEN 2
                  ELSE 3
                END,
                d.last_report_time DESC NULLS LAST,
                d.device_id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<DeviceEntity> searchForUnifiedSearch(
            @Param("userId") UUID userId,
            @Param("keyword") String keyword,
            @Param("limit") int limit);

    @Query("SELECT d.userId FROM DeviceEntity d WHERE d.deviceId = :deviceId")
    Optional<UUID> findUserIdByDeviceId(@Param("deviceId") Long deviceId);


    /**
     * 更新设备在线状态和最后上报时间
     *
     * @param deviceId 设备 ID
     * @param status 状态 (0:离线, 1:在线)
     * @param lastReportTime 最后上报时间
     */
    @Transactional
    @Modifying
    @Query("UPDATE DeviceEntity d SET d.onlineStatus = :status, " +
            "d.lastReportTime = :lastReportTime WHERE d.deviceId = :deviceId")
    void updateStatus(@Param("deviceId") Long deviceId,
                      @Param("status") Integer status,
                      @Param("lastReportTime") OffsetDateTime lastReportTime);

    /**
     * 更新设备在线状态、最后上报时间，以及设置 onboarding 时间（如果为空）
     *
     * @param deviceId 设备 ID
     * @param status 状态 (0:离线, 1:在线)
     * @param lastReportTime 最后上报时间
     * @param onboardingTime 首次上报时间（onboarding）
     */
    @Transactional
    @Modifying
    @Query("UPDATE DeviceEntity d SET d.onlineStatus = :status, " +
            "d.lastReportTime = :lastReportTime, " +
            "d.onboardingTime = COALESCE(d.onboardingTime, :onboardingTime) " +
            "WHERE d.deviceId = :deviceId")
    void updateStatusWithOnboarding(@Param("deviceId") Long deviceId,
                                   @Param("status") Integer status,
                                   @Param("lastReportTime") OffsetDateTime lastReportTime,
                                   @Param("onboardingTime") OffsetDateTime onboardingTime);

    /**
     * 更新设备属性和最后上报时间
     *
     * @param deviceId 设备 ID
     * @param lastReportTime 最后上报时间
     * @param model 设备型号
     * @param version 软件版本
     * @param brightness 亮度
     * @param networkType 网络类型
     * @param playingProgram 当前播放节目
     * @param resolution 屏幕分辨率
     * @param totalStorage 总存储空间
     * @param freeStorage 可用存储空间
     */
    @Transactional
    @Modifying
    @Query("UPDATE DeviceEntity d SET " +
            "d.lastReportTime = :lastReportTime, " +
            "d.model = COALESCE(:model, d.model), " +
            "d.version = COALESCE(:version, d.version), " +
            "d.brightness = COALESCE(:brightness, d.brightness), " +
            "d.networkType = COALESCE(:networkType, d.networkType), " +
            "d.playingProgram = COALESCE(:playingProgram, d.playingProgram), " +
            "d.resolution = COALESCE(:resolution, d.resolution), " +
            "d.totalStorage = COALESCE(:totalStorage, d.totalStorage), " +
            "d.freeStorage = COALESCE(:freeStorage, d.freeStorage) " +
            "WHERE d.deviceId = :deviceId")
    void updateDeviceProperties(@Param("deviceId") Long deviceId,
                                @Param("lastReportTime") OffsetDateTime lastReportTime,
                                @Param("model") String model,
                                @Param("version") String version,
                                @Param("brightness") Integer brightness,
                                @Param("networkType") Integer networkType,
                                @Param("playingProgram") String playingProgram,
                                @Param("resolution") String resolution,
                                @Param("totalStorage") Long totalStorage,
                                @Param("freeStorage") Long freeStorage);

    /**
     * 更新最后上报时间（仅当新时间更晚时更新，防止回退）。
     *
     * @return 实际更新行数（0/1）
     */
    @Transactional
    @Modifying
    @Query("""
            UPDATE DeviceEntity d
               SET d.lastReportTime = :lastReportTime
             WHERE d.deviceId = :deviceId
               AND (d.lastReportTime IS NULL OR d.lastReportTime < :lastReportTime)
            """)
    int updateLastReportTimeIfNewer(@Param("deviceId") Long deviceId,
                                    @Param("lastReportTime") OffsetDateTime lastReportTime);
}
