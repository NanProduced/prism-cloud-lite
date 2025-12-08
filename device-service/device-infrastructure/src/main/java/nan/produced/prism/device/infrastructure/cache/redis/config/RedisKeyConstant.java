package nan.produced.prism.device.infrastructure.cache.redis.config;

import nan.produced.prism.device.common.exception.tech.TechErrorCode;
import nan.produced.prism.device.common.exception.tech.TechException;

public class RedisKeyConstant {

    private RedisKeyConstant() {
        throw new TechException(TechErrorCode.INSTANTIATION_IS_PROHIBITED);
    }

    /*===================  终端指令模板 ====================== */

    /**
     * 指令队列 - List
     */
    public static final String COMMAND_QUEUE_KEY = "terminal:commands:%d";

    /**
     * 指令去重索引 - Hash
     */
    public static final String COMMAND_INDEX_KEY = "terminal:command:index:%d";      // 去重索引

    /**
     * 指令详情 - String
     */
    public static final String COMMAND_DETAIL_KEY = "terminal:command:detail:%d:%d";   // 指令详情 deviceId:commandId

    /**
     * 全局指令ID序列 - String
     * 通过Redis自增维护，重启后可继续递增
     */
    public static final String COMMAND_ID_SEQ_KEY = "terminal:command:id:seq";

    /*===================  设备在线状态模板 ====================== */

    /**
     * 设备在线状态 - Hash
     * 存储设备的当前在线状态信息
     */
    public static final String DEVICE_STATUS_KEY = "device:status:%d";

    /**
     * 设备状态索引 - Set
     * 存储所有设备ID，用于快速遍历
     */
    public static final String DEVICE_STATUS_INDEX_KEY = "device:status:index";

    /**
     * 在线设备计数 - String
     * 存储当前在线设备总数
     */
    public static final String ONLINE_DEVICE_COUNT_KEY = "device:online:count";

    /*===================  分布式锁 ====================== */

    /**
     * 设备缓存维护锁（离线检测和校准任务共用）
     */
    public static final String DEVICE_CACHE_MAINTENANCE_LOCK_KEY = "device:cache:maintenance:lock";
}
