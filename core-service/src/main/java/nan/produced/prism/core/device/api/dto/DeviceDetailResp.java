package nan.produced.prism.core.device.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import nan.produced.prism.core.device.domain.DeviceNetworkType;
import nan.produced.prism.core.device.domain.DeviceProperties;
import nan.produced.prism.core.device.domain.dto.TagVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 设备详情视图（用于查询单个设备详情接口）
 */
@Data
@Schema(description = "设备详情视图（用于查询单个设备详情接口）")
public class DeviceDetailResp {

    /**
     * 设备Id
     */
    @Schema(description = "设备ID", example = "10001")
    private Long deviceId;

    /**
     * 设备名称
     */
    @Schema(description = "设备名称", example = "Lobby Screen A")
    private String deviceName;

    /**
     * 设备描述
     */
    @Schema(description = "设备描述", example = "大厅入口屏幕")
    private String description;

    /**
     * 在线状态: 0-离线 1-在线
     * <p>只有这两种状态</p>
     */
    @Schema(description = "在线状态：0-离线，1-在线", example = "1")
    private Integer onlineStatus;

    /**
     * 上云时间（设备第一次连接到服务器的时间）
     */
    @Schema(description = "上云时间（设备第一次连接到服务器的时间）", example = "2025-12-13T02:37:19")
    private LocalDateTime onboardingTime;

    /**
     * 最后上报时间
     */
    @Schema(description = "最后上报时间", example = "2025-12-13T02:37:19")
    private LocalDateTime lastReportTime;

    /**
     * 创建设备账号的时间
     */
    @Schema(description = "创建设备账号的时间", example = "2025-12-13T02:37:19")
    private LocalDateTime createTime;

    /**
     * 设备型号
     */
    @Schema(description = "设备型号", example = "DS-2000X")
    private String model;

    /**
     * 软件版本
     */
    @Schema(description = "软件版本", example = "v4.6")
    private String version;

    /**
     * 亮度
     */
    @Schema(description = "亮度（0-100）", example = "64")
    private Integer brightness;

    /**
     * 网络类型
     */
    @Schema(description = "网络类型", example = "FOUR_G")
    private DeviceNetworkType networkType;

    /**
     * 网络强度(只有4g才有，其他类型网络为null)
     */
    @Schema(description = "网络强度（仅 4G 有值，其它网络类型为 null）", example = "87")
    private Integer networkStrength;

    /**
     * 当前播放的节目名称
     */
    @Schema(description = "当前播放节目名称", example = "Summer Campaign")
    private String playingProgram;

    /**
     * 分辨率
     */
    @Schema(description = "分辨率", example = "1920x1080")
    private String resolution;

    /**
     * 总存储空间
     */
    @Schema(description = "总存储空间（bytes）", example = "128000000000")
    private Long totalStorage;

    /**
     * 剩余存储空间
     */
    @Schema(description = "剩余存储空间（bytes）", example = "64000000000")
    private Long freeStorage;

    /**
     * 设备截图 - s3预览地址
     */
    @Schema(description = "设备截图预览地址（TODO: 截图业务未实现，暂返回 null）")
    private String lastScreenshotUrl;

    /**
     * 该设备绑定的标签
     */
    @Schema(description = "设备绑定的标签列表")
    private List<TagVO> tags;


    /**
     * 设备自定义列值（key 为 fieldKey）
     * <p>
     * value 的类型由字段类型决定：
     * TEXT/URL/EMAIL/PHONE/COUNTRY/SELECT -> String；
     * NUMBER -> BigDecimal；
     * BOOLEAN -> Boolean；
     * DATETIME -> ISO 8601 字符串；
     * MULTI_SELECT -> String[]。
     * </p>
     */
    @Schema(description = "设备自定义列值（key 为 fieldKey）")
    private Map<String, Object> customFieldValues;

    @Schema(description = "设备全部属性")
    private DeviceProperties deviceProperties;
}
