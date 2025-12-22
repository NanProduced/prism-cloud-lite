package nan.produced.prism.core.program.api.dto.internal;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InternalDeviceScheduleCommandOperationResp {

    private String authorUrl;

    private Integer karma;

    /**
     * 对应终端协议 operation.content（JSON 字符串，例如：{"brightness":77}）。
     */
    private String content;
}

