package nan.produced.prism.core.resource.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ResourceTombstoneKey implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 32)
    private ResourceType resourceType;

    @Column(name = "ref_id", nullable = false, length = 128)
    private String refId;

    @Column(name = "ref_version", nullable = false)
    private int refVersion;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResourceTombstoneKey that)) {
            return false;
        }
        return refVersion == that.refVersion
                && Objects.equals(userId, that.userId)
                && resourceType == that.resourceType
                && Objects.equals(refId, that.refId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, resourceType, refId, refVersion);
    }
}

