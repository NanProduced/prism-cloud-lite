package nan.produced.prism.auth.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Stores alternative identifiers (email/phone/username) tied to an end user.
 */
@Data
@Entity
@Table(name = "pca_login_alias")
public class LoginAliasEntity {

    /** Surrogate key for alias row. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning end-user reference. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private EndUserEntity user;

    /** Type of alias (email, phone, username). */
    @Enumerated(EnumType.STRING)
    @Column(name = "alias_type", nullable = false, length = 16)
    private LoginAliasType aliasType;

    /** Actual alias value storedΪ citext. */
    @Column(name = "alias_value", nullable = false, columnDefinition = "citext")
    private String aliasValue;
}
