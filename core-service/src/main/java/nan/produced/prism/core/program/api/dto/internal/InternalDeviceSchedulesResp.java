package nan.produced.prism.core.program.api.dto.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 内部接口：设备排程 JSON 模型（供 device-service 拉取并透传给终端）。
 *
 * <p>注意：字段名需与终端协议保持一致（contentsSchedule / commandSchedule）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalDeviceSchedulesResp {

    @JsonProperty("contentsSchedule")
    private List<InternalDeviceScheduleContentsRuleResp> contentsSchedule;

    @JsonProperty("commandSchedule")
    private List<InternalDeviceScheduleCommandRuleResp> commandSchedule;
}
