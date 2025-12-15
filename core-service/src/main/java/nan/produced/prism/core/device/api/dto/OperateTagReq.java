package nan.produced.prism.core.device.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OperateTagReq {

    /**
     * 标签名称
     */
    @NotNull
    @Max(64)
    private String tagName;

    /**
     * 颜色 - 后端不处理，由前端控制映射，后端仅存储颜色值
     */
    @NotNull
    private String color;

    /**
     * 图标 - 后端不处理，由前端控制映射，后端仅存储图标值
     */
    @NotNull
    private String icon;

    /**
     * 描述
     */
    private String description;
}
