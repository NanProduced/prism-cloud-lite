package nan.produced.prism.core.device.domain.report.log;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 设备上报的日志数据结构
 *
 * @author Nan
 */
@Data
public class DeviceLog {

    /**
     * 设备ID
     */
    @JsonProperty("led_id")
    private int deviceId;

    /**
     * 设备名称
     */
    @JsonProperty("led_name")
    private String deviceName;

    /**
     * 日志类型
     */
    @JsonProperty("log_type")
    private String logType;

    /**
     * 一级分类
     */
    @JsonProperty("log_subtype1")
    private String subtype1;

    /**
     * 二级分类
     */
    @JsonProperty("log_subtype2")
    private String subtype2;

    /**
     * 三级分类
     */
    @JsonProperty("log_subtype3")
    private String subtype3;

    /**
     * 日志描述
     */
    private String description;

    /**
     * 日志等级 （0-7）
     */
    private int level;

    /**
     * 日志分类
     */
    private String categories;

    /**
     * 设备上的时间
     */
    @JsonProperty("device_time")
    private String deviceTime;

    /**
     * 处理状态
     */
    @JsonProperty("hand_status")
    private int handleStatus ;

    /**
     * 处理时间
     */
    @JsonProperty("hand_time")
    private String handleTime = "0000-10-10 00:00:00";

    /**
     * 日志参数-1
     */
    @JsonProperty("log_arg1")
    private String arg1;

    /**
     * 日志参数-2
     */
    @JsonProperty("log_arg2")
    private String arg2;

    /**
     * 日志参数-3
     */
    @JsonProperty("log_arg3")
    private String arg3;

    /**
     * 日志参数-4
     *
     *
     */
    @JsonProperty("log_arg4")
    private String arg4;

    /**
     * 日志参数-5
     */
    @JsonProperty("log_arg5")
    private String arg5;

    /**
     * 日志参数-6
     */
    @JsonProperty("log_arg6")
    private String arg6;

    /**
     * 其他信息
     */
    private String others;
}
