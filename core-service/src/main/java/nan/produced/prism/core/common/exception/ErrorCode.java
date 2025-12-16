package nan.produced.prism.core.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 统一错误码定义
 * 格式：&lt;领域&gt;-&lt;4位数字&gt;
 *
 * 错误码分类：
 * - CORE-2xxx: 业务核心相关
 * - SYS-5xxx: 平台或未分类异常
 *
 * 符合 service-standards.md 规范：
 * - 用于RPC/Feign调用（ApiResponse）
 * - 用于BFF对前端返回（BffResponse）
 */
@Getter
public enum ErrorCode {

    // ============ 成功 ============
    SUCCESS("CORE-0000", "OK", "操作成功", HttpStatus.OK, false),

    // ============ 业务核心相关 (CORE-2xxx) ============
    USER_ALREADY_EXISTS("CORE-2000", "用户已存在", "该邮箱已被注册，请使用其他邮箱或直接登录", HttpStatus.BAD_REQUEST, false),
    USER_NOT_FOUND_IN_CORE("CORE-2001", "用户在核心服务中不存在", "用户资料不存在", HttpStatus.NOT_FOUND, false),
    USER_PROFILE_CREATION_FAILED("CORE-2002", "用户资料创建失败", "用户资料创建失败，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR, true),
    DEVICE_NOT_FOUND_IN_CORE("CORE-2003", "设备在核心服务中不存在", "设备不存在", HttpStatus.NOT_FOUND, false),
    DEVICE_CREATED_FAILED("CORE-2004", "设备创建失败", "设备创建失败，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR, true),
    DEVICE_UPDATED_FAILED("CORE-2005", "设备更新失败", "设备更新失败，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR, true),
    DEVICE_DELETED_FAILED("CORE-2006", "设备删除失败", "设备删除失败，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR, true),
    DEVICE_REPORT_EVENT_HANDLE_FAILED("CORE-2007", "设备上报事件处理失败", "设备上报事件处理失败，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR, true),

    // ============ 设备标签相关 (CORE-21xx) ============
    DEVICE_TAG_NOT_FOUND("CORE-2100", "设备标签不存在", "指定的标签不存在或无权访问", HttpStatus.NOT_FOUND, false),
    DEVICE_TAG_SLUG_ALREADY_EXISTS("CORE-2101", "标签标识已存在", "该标签名称已被使用，请使用其他名称", HttpStatus.BAD_REQUEST, false),
    DEVICE_TAG_CREATE_FAILED("CORE-2102", "标签创建失败", "标签创建失败，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR, true),
    DEVICE_TAG_UPDATE_FAILED("CORE-2103", "标签更新失败", "标签更新失败，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR, true),
    DEVICE_TAG_DELETE_FAILED("CORE-2104", "标签删除失败", "标签删除失败，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR, true),

    // ============ 设备自定义字段相关 (CORE-22xx) ============
    DEVICE_CUSTOM_FIELD_DEF_NOT_FOUND("CORE-2200", "自定义字段不存在", "指定的自定义字段不存在或无权访问", HttpStatus.NOT_FOUND, false),
    DEVICE_CUSTOM_FIELD_KEY_ALREADY_EXISTS("CORE-2201", "自定义字段标识已存在", "该字段标识已被使用，请更换名称或标识", HttpStatus.BAD_REQUEST, false),
    DEVICE_CUSTOM_FIELD_QUOTA_EXCEEDED("CORE-2202", "自定义字段超出配额", "已达到当前套餐的自定义列上限，请升级套餐", HttpStatus.BAD_REQUEST, false),
    DEVICE_CUSTOM_FIELD_PRO_REQUIRED("CORE-2203", "需要 Pro 订阅", "该功能需要 Pro 订阅，请升级套餐后使用", HttpStatus.FORBIDDEN, false),
    DEVICE_CUSTOM_FIELD_VALUE_INVALID("CORE-2204", "自定义字段值不合法", "自定义字段值不合法，请检查输入", HttpStatus.BAD_REQUEST, false),
    DEVICE_CUSTOM_FIELD_OPTION_INVALID("CORE-2205", "自定义字段选项不合法", "自定义字段选项不合法，请检查输入", HttpStatus.BAD_REQUEST, false),

    // ============ 认证相关 (CORE-24xx) ============
    UNAUTHORIZED("CORE-2401", "未授权访问", "请提供有效的认证信息", HttpStatus.UNAUTHORIZED, false),
    INVALID_CLOUD_AUTH_HEADER("CORE-2402", "CLOUD_AUTH头格式错误", "认证信息格式错误，请重新登录", HttpStatus.UNAUTHORIZED, false),
    NO_AUTHENTICATED_USER("CORE-2403", "当前上下文中没有认证用户", "请先完成登录", HttpStatus.UNAUTHORIZED, false),

    // ============ 内部代码异常 (CORE-25xx) ============
    INSTANTIATION_IS_PROHIBITED("CORE-2501", "实例化被禁止", "请勿实例化该类", HttpStatus.INTERNAL_SERVER_ERROR, true),
    JSON_SERIALIZATION_EXCEPTION("CORE-2502", "JSON序列化异常", "JSON序列化异常，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR, true),
    JSON_MERGE_EXCEPTION("CORE-2503", "JSON合并异常", "JSON合并异常，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR, true),

    // ============ MQ相关 (CORE-26xx) ============
    MQ_MESSAGE_CONSUMING_FAILED("CORE-2600", "MQ消息消费失败", "MQ消息消费失败，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR, true),

    // ============ 媒体库相关 (CORE-27xx) ============
    MEDIA_FOLDER_NOT_FOUND("CORE-2700", "媒体文件夹不存在", "指定的文件夹不存在或无权访问", HttpStatus.NOT_FOUND, false),
    MEDIA_FILE_ENTITY_NOT_FOUND("CORE-2701", "文件实体不存在", "引用的文件不存在", HttpStatus.NOT_FOUND, false),
    MEDIA_ASSET_NOT_FOUND("CORE-2702", "媒体素材不存在", "指定的素材不存在或无权访问", HttpStatus.NOT_FOUND, false),
    MEDIA_INVALID_FILE_REFERENCE("CORE-2703", "文件引用无效", "请提供有效的 s3Key 或 fileEntityId", HttpStatus.BAD_REQUEST, false),
    MEDIA_MISSING_ORIGINAL_FILE("CORE-2704", "缺少原始文件", "素材必须包含原始文件", HttpStatus.BAD_REQUEST, false),
    MEDIA_BATCH_FINALIZE_FAILED("CORE-2705", "批量落库失败", "素材创建失败，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR, true),

    // ============ 系统异常 (SYS-5xxx) ============
    INTERNAL_SERVER_ERROR("SYS-5000", "系统内部错误", "服务器内部错误，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR, true),
    EXTERNAL_SERVICE_ERROR("SYS-5001", "外部服务调用失败", "依赖服务暂时不可用，请稍后重试", HttpStatus.BAD_GATEWAY, true);

    private final String code;
    private final String message;                // 技术向信息（开发调试用）
    private final String displayMessage;         // 用户友好的提示
    private final HttpStatus httpStatus;
    private final boolean retryable;             // 是否可重试

    ErrorCode(String code, String message, String displayMessage, HttpStatus httpStatus, boolean retryable) {
        this.code = code;
        this.message = message;
        this.displayMessage = displayMessage;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }
}
