package nan.produced.prism.core.program.domain.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pc_schedule_contents_rule")
public class ScheduleContentsRuleEntity {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "type", nullable = false, length = 16)
    private String type;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "if_limit_time", nullable = false)
    private Boolean ifLimitTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "limit_time", columnDefinition = "jsonb")
    private String limitTime;

    @Column(name = "if_limit_date", nullable = false)
    private Boolean ifLimitDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "limit_date", columnDefinition = "jsonb")
    private String limitDate;

    @Column(name = "if_limit_weekday", nullable = false)
    private Boolean ifLimitWeekday;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "limit_weekday", columnDefinition = "jsonb")
    private String limitWeekday;

    @Column(name = "release_program_id", nullable = false)
    private Integer releaseProgramId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

