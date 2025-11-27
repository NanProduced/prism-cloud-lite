package nan.produced.prism.auth.domain.user;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Administrative identity dedicated to the management console.
 */
@Getter
@Setter
@Entity
@Table(name = "auth_admin_users")
public class AdminUserEntity extends AbstractAuthUserEntity {

    @Override
    protected UserType defaultUserType() {
        return UserType.ADMIN;
    }
}