package nan.produced.prism.core.message.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "批量标记已读请求")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkReadReq {

    @Schema(description = "消息ID列表")
    private List<UUID> ids;
}

