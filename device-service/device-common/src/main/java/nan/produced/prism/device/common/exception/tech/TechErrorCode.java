package nan.produced.prism.device.common.exception.tech;

import lombok.Getter;
import nan.produced.prism.device.common.exception.ErrorCode;
import nan.produced.prism.device.common.exception.ErrorLevel;
import nan.produced.prism.device.common.exception.HttpStatusCode;

@Getter
public enum TechErrorCode implements ErrorCode {

    /**
     * JSON序列化/反序列化错误
     * <p>Jackson</p>
     */
    JSON_SERIALIZATION_EXCEPTION("DEVICE-0101", "序列化/反序列化错误", ErrorLevel.ERROR, HttpStatusCode.INTERNAL_SERVER_ERROR),

    JSON_MERGE_EXCEPTION("DEVICE-0102", "Json合并错误", ErrorLevel.ERROR, HttpStatusCode.INTERNAL_SERVER_ERROR),

    TIME_FORMAT_TRANSLATE_FAILED("DEVICE-0103", "Java.time时间转换失败", ErrorLevel.ERROR, HttpStatusCode.INTERNAL_SERVER_ERROR),

    INSTANTIATION_IS_PROHIBITED("DEVICE-0104", "禁止实例化", ErrorLevel.WARN, HttpStatusCode.INTERNAL_SERVER_ERROR),

    MONGO_DB_ERROR("DEVICE-0105", "MongoDB操作失败", ErrorLevel.CRITICAL, HttpStatusCode.INTERNAL_SERVER_ERROR),

    MYSQL_ERROR("DEVICE-0106", "MySQL操作失败", ErrorLevel.WARN, HttpStatusCode.INTERNAL_SERVER_ERROR),

    REDIS_ERROR("DEVICE-0107", "Redis操作失败", ErrorLevel.WARN, HttpStatusCode.INTERNAL_SERVER_ERROR),

    THREAD_POOL_REJECTED_ERROR("DEVICE-0108", "线程池耗尽拒绝任务", ErrorLevel.FATAL, HttpStatusCode.INTERNAL_SERVER_ERROR),

    RPC_EXCEPTION("DEVICE-0109", "RPC调用错误", ErrorLevel.CRITICAL, HttpStatusCode.INTERNAL_SERVER_ERROR),

    MINIO_ERROR("DEVICE-0110", "MinIO上传错误", ErrorLevel.CRITICAL, HttpStatusCode.INTERNAL_SERVER_ERROR),

    MINIO_SECURITY_ERROR("DEVICE-0111", "MinIO签名异常", ErrorLevel.CRITICAL, HttpStatusCode.INTERNAL_SERVER_ERROR),

    IO_EXCEPTION("DEVICE-0112", "I/O流处理异常", ErrorLevel.ERROR, HttpStatusCode.INTERNAL_SERVER_ERROR),

    NETTY_START_ERROR("DEVICE-0113", "Netty服务器启动错误", ErrorLevel.FATAL, HttpStatusCode.INTERNAL_SERVER_ERROR),

    METRICS_ERROR("DEVICE-0114", "Metrics错误", ErrorLevel.ERROR, HttpStatusCode.INTERNAL_SERVER_ERROR),

    ALGORITHM_ERROR("DEVICE-0115", "算法错误", ErrorLevel.CRITICAL, HttpStatusCode.INTERNAL_SERVER_ERROR),

    REDIS_TRANSACTION_FAILED("DEVICE-0116", "Redis事务执行失败", ErrorLevel.CRITICAL, HttpStatusCode.INTERNAL_SERVER_ERROR);

    ;
    /**
     * 错误码
     */
    private final String code;

    /**
     *
     * 错误消息
     */
    private final String message;

    /**
     * 错误级别
     */
    private final ErrorLevel level;

    /**
     * HTTP状态码
     */
    private final HttpStatusCode httpStatus;

    TechErrorCode(String code, String message, ErrorLevel level, HttpStatusCode httpStatus) {
        this.code = code;
        this.message = message;
        this.level = level;
        this.httpStatus = httpStatus;
    }
}