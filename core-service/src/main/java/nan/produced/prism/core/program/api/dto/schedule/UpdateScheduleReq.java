package nan.produced.prism.core.program.api.dto.schedule;

import jakarta.validation.Valid;
import java.util.List;
import lombok.Data;

@Data
public class UpdateScheduleReq {

    private String name;

    private String description;

    private Boolean enabled;

    /**
     * When provided (including empty list), replaces all existing contents rules.
     * When null, keeps existing contents rules unchanged.
     */
    @Valid
    private List<UpsertScheduleContentsRuleReq> contentsRules;

    /**
     * When provided (including empty list), replaces all existing command rules.
     * When null, keeps existing command rules unchanged.
     */
    @Valid
    private List<UpsertScheduleCommandRuleReq> commandRules;
}

