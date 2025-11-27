package nan.produced.prism.auth.domain.user;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * End-user identity for the public Prism surface area.
 */
@Getter
@Setter
@Entity
@Table(name = "auth_users")
public class EndUserEntity extends AbstractAuthUserEntity {

    @Override
    protected UserType defaultUserType() {
        return UserType.END_USER;
    }
}