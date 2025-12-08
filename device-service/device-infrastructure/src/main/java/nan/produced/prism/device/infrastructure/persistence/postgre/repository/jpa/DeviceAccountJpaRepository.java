package nan.produced.prism.device.infrastructure.persistence.postgre.repository.jpa;

import nan.produced.prism.device.infrastructure.persistence.postgre.entity.DeviceAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface DeviceAccountJpaRepository extends JpaRepository<DeviceAccountEntity, Long> {

    Optional<DeviceAccountEntity> findByAccount(String account);

    boolean existsByAccount(String account);

    @Modifying
    @Query("""
        update DeviceAccountEntity d
           set d.firstLoginTime = COALESCE(d.firstLoginTime, :loginTime),
               d.lastLoginTime = :loginTime,
               d.lastLoginIp = :clientIp,
               d.updateTime = :loginTime
         where d.deviceId = :deviceId
    """)
    int updateLoginTimeImmediate(@Param("deviceId") Long deviceId,
                                 @Param("clientIp") String clientIp,
                                 @Param("loginTime") LocalDateTime loginTime);

    @Modifying
    @Query("""
        update DeviceAccountEntity d
           set d.lastLoginTime = :loginTime,
               d.lastLoginIp = :clientIp,
               d.updateTime = :loginTime
         where d.deviceId = :deviceId
    """)
    int updateLoginTime(@Param("deviceId") Long deviceId,
                        @Param("clientIp") String clientIp,
                        @Param("loginTime") LocalDateTime loginTime);
}

