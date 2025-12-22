package nan.produced.prism.core.program.api.dto.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 内部接口：设备侧节目素材清单条目（用于 device-service 适配 /wp-json/wp/v2/media）。
 *
 * <p>注意：这里返回的是可直接下载的 CDN/对象存储公开 URL，不返回 S3 key。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "内部接口：设备节目素材清单条目")
public class InternalDeviceProgramMediaResp {

    @Schema(description = "可直接下载的 URL（CDN/对象存储公开地址）")
    private String url;

    @Schema(description = "文件大小（bytes）")
    private Long sizeBytes;
}

