package nan.produced.prism.auth.domain.user.repository;

import java.util.Optional;
import nan.produced.prism.auth.domain.user.LoginAliasEntity;
import nan.produced.prism.auth.domain.user.LoginAliasType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginAliasRepository extends JpaRepository<LoginAliasEntity, Long> {

    @Query("select a from LoginAliasEntity a where upper(a.aliasValue) = upper(:value)")
    Optional<LoginAliasEntity> findAnyByValue(@Param("value") String aliasValue);

    @Query("select a from LoginAliasEntity a where upper(a.aliasValue) = upper(:value) and a.aliasType = :type")
    Optional<LoginAliasEntity> findByValueAndType(@Param("value") String aliasValue,
                                                  @Param("type") LoginAliasType type);
}