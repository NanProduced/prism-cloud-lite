package nan.produced.prism.core.device.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.device.domain.command.DeviceCommandLog;
import nan.produced.prism.core.device.domain.command.DeviceCommandStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface DeviceCommandLogRepositoryJpa extends JpaRepository<DeviceCommandLog, Long>, JpaSpecificationExecutor<DeviceCommandLog> {

    Optional<DeviceCommandLog> findByOperationId(UUID operationId);

    Optional<DeviceCommandLog> findFirstByDeviceIdAndQueuedIdOrderByCreatedAtDesc(Long deviceId, Integer queuedId);

    Optional<DeviceCommandLog> findByIdAndUserId(Long id, UUID userId);

    @Transactional
    @Modifying
    @Query("UPDATE DeviceCommandLog d SET d.status = :status, d.updatedAt = :updatedAt WHERE d.operationId = :operationId")
    void updateStatus(
            @Param("operationId") UUID operationId,
            @Param("status") DeviceCommandStatus status,
            @Param("updatedAt") OffsetDateTime updatedAt);
}
