package nan.produced.prism.core.device.domain.report.log;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 设备日志类型
 *
 * @author Nan
 */
@Getter
public enum DeviceLogType {

    /* ----- account ----- */

    /**
     * 账号变更
     */
    ACCOUNT_CHANGED(1100, "account changed", "account", "account_changed"),

    /* ----- runtime ----- */

    /**
     * 存储空间上报
     */
    MEMORY_REPORT(2100, "memory report", "runtime", "memory"),

    /**
     * 存储空间上报
     */
    STORAGE_REPORT(2200, "storage report", "runtime", "storage"),

    /**
     * 空间不足
     */
    STORAGE_LACKING(2210, "storage is lacking", "runtime", "storage.low"),

    /* ----- connectivity ----- */

    /**
     * 4G重拨
     */
    log(3110, "4g redial", "connectivity", "4g.redial"),

    /**
     * 重置无线界面层
     */
    RESET_RIL(3120, "reset ril", "connectivity", "4g.reset_ril"),

    /**
     * 4g信号切换
     */
    SIGNAL_CHANGED_4G(3130, "signal changed (4g)", "connectivity", "4g.signal_changed"),

    /**
     * 4g连接成功
     */
    ATTACHED_4G(3140, "4g attached", "connectivity", "4g.attached"),

    /**
     * 配置以太网
     */
    LAN_CONFIGURED(3210, "lan configured", "connectivity", "lan.ethernet_configured"),

    /**
     * 配置Wi-Fi
     */
    WIFI_CONFIGURED(3310, "wifi configured", "connectivity", "wifi.wifi_configured"),

    /**
     * Wi-Fi切换
     */
    WIFI_SIGNAL_CHANGED(3320, "signal changed (wifi)", "connectivity", "wifi.signal_changed"),

    /**
     * Wi-Fi模块连接
     */
    WIFI_MODULE_ATTACHED(3311, "wifi module attached", "connectivity", "wifi.module.attached"),

    /**
     * WiFi模块断开
     */
    WIFI_MODULE_DETACHED(3312, "wifi module detached", "connectivity", "wifi.module.detached"),

    /* ----- device ----- */

    /**
     * 当前亮度
     */
    CURRENT_BRIGHTNESS(4110, "current brightness report", "device", "current.brightness"),

    /**
     * 调整亮度
     */
    ADJUST_BRIGHTNESS(4210, "adjust brightness report", "device", "adjust.brightness"),

    /**
     * 屏幕状态变换
     */
    SCREEN_STATUS_CHANGED(4300, "screen status changed", "device", "status"),


    /* ----- program ----- */

    /**
     * 节目删除
     */
    PROGRAM_DELETED(5100, "program deleted", "program", "deleted"),

    /**
     * 放弃下载节目
     */
    INVALID_PROGRAM(5200, "abandon downloading programs", "program", "invalid_program"),

    /**
     * 文件/资源不存在
     */
    RESOURCE_NOT_EXIST(5300, "resource not exist", "program", "missing_resource"),

    /**
     * 节目被清除
     */
    PROGRAM_CLEARED(5400, "program cleared", "program", "program_cleared"),

    /**
     * 开始播放节目
     */
    START_PLAYING(5500, "start playing program", "program", "start_playing"),

    /* ----- operation ----- */

    /**
     * 错误操作
     */
    BAD_OPERATION(6100, "operation error", "operation", "bad_operation"),

    /* ----- updating ----- */

    /**
     * 升级失败
     */
    UPDATING_FAILED(7100, "updating failed", "updating", "failed_updating"),

    /**
     * 未知操作
     */
    UNKNOWN(9999, "unknow operation", null, null);

    private final Integer id;

    private final String operation;

    private final String type;

    private final String subType;

    DeviceLogType(Integer id, String operation, String type, String subType) {
        this.id = id;
        this.operation = operation;
        this.type = type;
        this.subType = subType;
    }

    public static DeviceLogType analysisOperation(DeviceLog log) {
        if (StringUtils.isBlank(log.getLogType())) {
            return UNKNOWN;
        }

        StringBuilder subTypeBuilder = new StringBuilder();
        if (StringUtils.isNotBlank(log.getSubtype1())) {
            subTypeBuilder.append(log.getSubtype1());
            if (StringUtils.isNotBlank(log.getSubtype2())) {
                subTypeBuilder.append('.').append(log.getSubtype2());
                if (StringUtils.isNotBlank(log.getSubtype3())) {
                    subTypeBuilder.append('.').append(log.getSubtype3());
                }
            }
        }
        String subType = subTypeBuilder.toString();

        for (DeviceLogType operation : DeviceLogType.values()) {
            if (log.getLogType().equals(operation.getType()) && subType.equals(operation.getSubType())) {
                return operation;
            }
        }
        return UNKNOWN;
    }

    /**
     * 获取类型Id
     * @param deviceLog 设备日志
     * @return 类型Id
     */
    public static Integer getTypeId(DeviceLog deviceLog) {
        return analysisOperation(deviceLog).getId();
    }

    /**
     * 判断操作类型是否正确
     * @param types 类型
     * @return 是否正确
     */
    public static boolean isValidaType(Set<Integer> types) {
        final List<Integer> collect = Arrays.stream(DeviceLogType.values())
                .map(DeviceLogType::getId).toList();
        for (Integer type : types) {
            if (!collect.contains(type)) {
                return false;
            }
        }
        return true;
    }

}
