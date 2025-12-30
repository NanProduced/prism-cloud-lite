package nan.produced.prism.core.telemetry.api.dto;

/**
 * Resource lifecycle status for telemetry responses.
 *
 * <p>ACTIVE: the referenced resource currently exists.</p>
 * <p>DELETED: the resource was deleted (tombstone exists).</p>
 * <p>UNKNOWN: the resource cannot be resolved (likely deleted before tombstone was introduced).</p>
 */
public enum ResourceStatus {
    ACTIVE,
    DELETED,
    UNKNOWN
}

