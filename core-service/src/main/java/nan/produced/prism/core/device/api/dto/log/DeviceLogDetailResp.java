package nan.produced.prism.core.device.api.dto.log;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "设备日志详情")
public record DeviceLogDetailResp(
    @Schema(description = "日志ID")
    Long id,
    @Schema(description = "设备ID")
    Long deviceId,
    @Schema(description = "设备名称（冗余字段，便于前端展示）")
    String deviceName,
    @Schema(description = "操作类型ID（operation_id）")
    Integer operationId,
    @Schema(description = "日志等级（0-7）")
    Integer level,
    @Schema(description = "日志类型（log_type）")
    String logType,
    @Schema(description = "一级分类（log_subtype1）")
    String subtype1,
    @Schema(description = "二级分类（log_subtype2）")
    String subtype2,
    @Schema(description = "三级分类（log_subtype3）")
    String subtype3,
    @Schema(description = "日志分类（categories）")
    String categories,
    @Schema(description = "描述")
    String description,
    @Schema(description = "设备时间原始值（device_time）")
    String deviceTimeRaw,
    @Schema(description = "处理状态（hand_status）")
    Integer handleStatus,
    @Schema(description = "处理时间原始值（hand_time）")
    String handleTimeRaw,
    @Schema(description = "日志参数1")
    String logArg1,
    @Schema(description = "日志参数2")
    String logArg2,
    @Schema(description = "日志参数3")
    String logArg3,
    @Schema(description = "日志参数4")
    String logArg4,
    @Schema(description = "日志参数5")
    String logArg5,
    @Schema(description = "日志参数6")
    String logArg6,
    @Schema(description = "其他信息")
    String others,
    @Schema(description = "设备上报时间（尽力解析 device_time，UTC）")
    OffsetDateTime reportTime,
    @Schema(description = "服务端接收时间（UTC）")
    OffsetDateTime createTime
) {
}
