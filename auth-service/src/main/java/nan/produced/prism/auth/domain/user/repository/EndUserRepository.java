package nan.produced.prism.auth.domain.user.repository;

import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.auth.domain.user.EndUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA repository backed by PostgreSQL for reading end-user records.
 */
public interface EndUserRepository extends JpaRepository<EndUserEntity, UUID> {

    Optional<EndUserEntity> findByEmailIgnoreCase(String email);

    Optional<EndUserEntity> findByPhone(String phone);

    @Query("select u from EndUserEntity u where u.publicId = :publicId")
    Optional<EndUserEntity> findByPublicId(@Param("publicId") String publicId);
}