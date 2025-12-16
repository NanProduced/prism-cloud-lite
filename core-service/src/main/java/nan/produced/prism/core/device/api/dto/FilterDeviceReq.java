package nan.produced.prism.core.device.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设备筛选参数
 * <p>条件为交集</p>
 *
 * @author Nan
 */
@Data
@Schema(description = "设备筛选请求（服务端过滤，条件为交集）")
public class FilterDeviceReq {

    /**
     * 搜索关键字 (终端名，描述)
     */
    @Schema(description = "搜索关键字（设备名称/描述模糊匹配）", example = "lobby")
    private String keyword;

    @Schema(description = "网络类型（DeviceNetworkType.code）：0-wifi ap，1-wifi，2-lan，3-4g", example = "1")
    private Integer networkType;

    @Schema(description = "在线状态：0-离线，1-在线", example = "1")
    private Integer onlineStatus;

    @Schema(description = "设备型号（模糊匹配）", example = "DS-2000X")
    private String model;

    // todo:sort排序条件

}
