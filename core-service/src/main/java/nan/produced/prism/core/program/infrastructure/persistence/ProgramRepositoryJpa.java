package nan.produced.prism.core.program.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.program.domain.ProgramEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgramRepositoryJpa extends JpaRepository<ProgramEntity, UUID> {

    List<ProgramEntity> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    long countByUserId(UUID userId);

    Optional<ProgramEntity> findByIdAndUserId(UUID id, UUID userId);

    List<ProgramEntity> findByUserIdAndIdIn(UUID userId, List<UUID> ids);

    @Query("""
        SELECT p
          FROM ProgramEntity p
         WHERE p.userId = :userId
           AND LOWER(p.name) LIKE CONCAT('%', LOWER(:keyword), '%')
        """)
    List<ProgramEntity> findByUserIdAndNameLike(@Param("userId") UUID userId,
                                                @Param("keyword") String keyword,
                                                Pageable pageable);
}
