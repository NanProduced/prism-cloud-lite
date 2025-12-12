package nan.produced.prism.device.common.exception.business;

import lombok.Getter;
import nan.produced.prism.device.common.exception.ErrorCode;
import nan.produced.prism.device.common.exception.ErrorLevel;
import nan.produced.prism.device.common.exception.HttpStatusCode;

@Getter
public enum BusinessErrorCode implements ErrorCode {

    /*======================= 通用系统错误 TM00XX =======================*/

    /**
     * 参数验证失败
     */
    INVALID_PARAMETER("TM0001", "参数验证失败", ErrorLevel.WARN, HttpStatusCode.BAD_REQUEST),

    /**
     * 请求参数为空
     */
    PARAMETER_MISSING("TM0002", "请求参数不能为空", ErrorLevel.WARN, HttpStatusCode.BAD_REQUEST),

    /**
     * 请求参数格式错误
     */
    PARAMETER_FORMAT_ERROR("TM0003", "请求参数格式错误", ErrorLevel.WARN, HttpStatusCode.BAD_REQUEST),

    /**
     * 操作失败
     */
    OPERATION_FAILED("TM0004", "操作失败", ErrorLevel.ERROR, HttpStatusCode.INTERNAL_SERVER_ERROR),

    /**
     * 系统繁忙
     */
    SYSTEM_BUSY("TM0005", "系统繁忙，请稍后重试", ErrorLevel.WARN, HttpStatusCode.SERVICE_UNAVAILABLE),

    /**
     * 系统内部错误
     */
    SYSTEM_ERROR("TM0006", "系统内部错误", ErrorLevel.ERROR, HttpStatusCode.INTERNAL_SERVER_ERROR),

    /**
     * 网络超时
     */
    NETWORK_TIMEOUT("TM0007", "网络请求超时", ErrorLevel.WARN, HttpStatusCode.INTERNAL_SERVER_ERROR),

    /**
     * 数据格式错误
     */
    DATA_FORMAT_ERROR("TM008", "数据格式错误", ErrorLevel.WARN, HttpStatusCode.BAD_REQUEST),

    /*======================= 认证模块错误 TM10XX =======================*/

    /**
     * 用户名或密码错误
     */
    INVALID_CREDENTIALS("TM1001", "用户名或密码错误", ErrorLevel.WARN, HttpStatusCode.UNAUTHORIZED),

    /**
     * 账户不存在
     */
    ACCOUNT_NOT_FOUND("TM1002", "账户不存在", ErrorLevel.WARN, HttpStatusCode.UNAUTHORIZED),

    /**
     * 账户已被禁用
     */
    ACCOUNT_DISABLED("TM1003", "账户已被禁用", ErrorLevel.WARN, HttpStatusCode.UNAUTHORIZED),

    /**
     * 认证失败
     */
    AUTHENTICATION_FAILED("TM1004", "认证失败",ErrorLevel.WARN, HttpStatusCode.UNAUTHORIZED),

    /**
     * IP地址不在白名单中
     */
    IP_NOT_WHITELISTED("TM1005", "IP地址不在白名单中", ErrorLevel.WARN, HttpStatusCode.UNAUTHORIZED),

    /**
     * 服务签名验证失败
     */
    INVALID_SERVICE_TOKEN("TM1006", "服务签名验证失败", ErrorLevel.WARN, HttpStatusCode.UNAUTHORIZED),


    /*======================= 终端/设备管理 TM20XX =======================*/

    /**
     * 账号名已存在
     */
    TERMINAL_ACCOUNT_EXIST("TM2001", "账号名称已存在", ErrorLevel.WARN, HttpStatusCode.BAD_REQUEST),

    /*======================= V1.1-websocket协议 TM30XX =======================*/

    WS_INVALID_MESSAGE_TYPE("TM3001", "消息类型不存在", ErrorLevel.WARN, HttpStatusCode.BAD_REQUEST),

    WS_INVALID_MESSAGE_DATA("TM3002", "消息内容存在问题", ErrorLevel.WARN, HttpStatusCode.BAD_REQUEST);

    /*======================= 枚举属性 =======================*/

    /**
     * 错误码
     */
    private final String code;

    /**
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

    /*======================= 构造函数 =======================*/

    BusinessErrorCode(String code, String message, ErrorLevel level, HttpStatusCode httpStatus) {
        this.code = code;
        this.message = message;
        this.level = level;
        this.httpStatus = httpStatus;
    }
}
