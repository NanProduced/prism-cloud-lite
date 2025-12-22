package nan.produced.prism.core.program.api.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalDeviceScheduleContentsOperationResp {

    private Integer id;

    private String name;

    private String vsn;

    private String source;
}

