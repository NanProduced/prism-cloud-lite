package nan.produced.prism.auth.domain.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.auth.domain.user.AdminUserEntity;
import nan.produced.prism.auth.domain.user.UserType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminUserRepository extends JpaRepository<AdminUserEntity, UUID> {

    Optional<AdminUserEntity> findByEmail(String email);

    Optional<AdminUserEntity> findByPublicId(String publicId);

    Optional<AdminUserEntity> findFirstByUserType(UserType userType);

    Page<AdminUserEntity> findByUserType(UserType userType, Pageable pageable);
}
