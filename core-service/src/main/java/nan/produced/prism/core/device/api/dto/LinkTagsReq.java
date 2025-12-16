package nan.produced.prism.core.device.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 关联标签请求
 *
 * @author Nan
 */
@Schema(description = "设备标签全量替换请求")
@Data
public class LinkTagsReq {

    /**
     * 标签Slug列表
     * <p>
     * 传入空列表或 null 表示清空设备的所有标签
     */
    @Schema(description = "标签 slug 列表（传空表示清空）", example = "[\"lobby\",\"outdoor\"]")
    private List<String> tags;
}
