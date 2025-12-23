package nan.produced.prism.core.device.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.device.domain.report.log.DeviceLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface DeviceLogRepositoryJpa extends JpaRepository<DeviceLogEntity, Long>, JpaSpecificationExecutor<DeviceLogEntity> {

    Optional<DeviceLogEntity> findByIdAndUserId(Long id, UUID userId);

    @Transactional
    @Modifying
    @Query("DELETE FROM DeviceLogEntity l WHERE l.createTime < :cutoff")
    int deleteExpired(@Param("cutoff") OffsetDateTime cutoff);
}

