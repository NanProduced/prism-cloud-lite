package nan.produced.prism.core.device.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 关联标签请求
 *
 * @author Nan
 */
@Data
public class LinkTagsReq {

    /**
     * 标签Slug列表
     * <p>
     * 传入空列表或 null 表示清空设备的所有标签
     */
    private List<String> tags;
}
