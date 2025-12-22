package nan.produced.prism.core.program.api.dto.schedule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

@Data
public class CreateScheduleReq {

    @NotBlank
    private String name;

    private String description;

    private Boolean enabled;

    @Valid
    private List<UpsertScheduleContentsRuleReq> contentsRules;

    @Valid
    private List<UpsertScheduleCommandRuleReq> commandRules;
}

