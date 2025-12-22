package nan.produced.prism.core.program.api.dto.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;

@Data
public class UpsertScheduleCommandRuleReq {

    private Long id;

    /**
     * 指令描述（统一使用 DeviceActionBase；后端将其转换为终端排程协议中的 operation）。
     */
    @Valid
    @NotNull
    private DeviceActionBase operation;

    /**
     * 当天执行时间点（可多次触发）。
     *
     * <p>示例：["10:41:02"]</p>
     */
    @NotEmpty
    private List<String> opTime;

    private Boolean ifLimitDate;

    private JsonNode limitDate;

    private Boolean ifLimitWeekday;

    private JsonNode limitWeekday;
}
