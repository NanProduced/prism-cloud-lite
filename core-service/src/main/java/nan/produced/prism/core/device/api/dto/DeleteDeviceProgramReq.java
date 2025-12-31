package nan.produced.prism.core.device.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Data;

@Data
@Schema(description = "删除设备上指定节目请求（支持 programId 或 vsnName）")
public class DeleteDeviceProgramReq {

    @Schema(description = "平台节目ID（可选；与 vsnName 二选一）")
    private UUID programId;

    @Schema(description = "VSN 文件名（可选；与 programId 二选一）", example = "Playlist9017_783596d9ee396d7a604dac56a6979546_1332.vsn")
    private String vsnName;

    @Schema(description = "节目来源（可选；不传则同时尝试 internet 与 lan）", example = "internet")
    private String source;
}

