package nan.produced.prism.core.program.api.dto.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpsertScheduleContentsRuleReq {

    private Long id;

    /**
     * rotation / spot
     */
    @NotBlank
    private String type;

    @NotNull
    private Integer priority;

    /**
     * ProgramReleaseEntity.deviceProgramId (Integer).
     */
    @NotNull
    private Integer releaseProgramId;

    private Boolean ifLimitTime;

    private JsonNode limitTime;

    private Boolean ifLimitDate;

    private JsonNode limitDate;

    private Boolean ifLimitWeekday;

    private JsonNode limitWeekday;
}

