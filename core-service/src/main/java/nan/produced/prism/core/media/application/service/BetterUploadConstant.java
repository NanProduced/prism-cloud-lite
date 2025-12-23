package nan.produced.prism.core.media.application.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Better Upload 常量定义
 *
 * @author Nan
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BetterUploadConstant {

    // ==================== 文件夹相关 ====================

    /**
     * 默认文件夹 ID
     */
    public static final String DEFAULT_FOLDER_ID = "default";

    // ==================== 角色相关 ====================

    /**
     * 默认文件角色
     */
    public static final String DEFAULT_ROLE = "original";

    // ==================== 缓存控制 ====================

    /**
     * 静态资源缓存控制头（1年）
     */
    public static final String CACHE_CONTROL_IMMUTABLE = "public, max-age=31536000";

    // ==================== S3 请求头 ====================

    /**
     * S3 存储类型头
     */
    public static final String HEADER_STORAGE_CLASS = "x-amz-storage-class";

    /**
     * S3 ACL 头
     */
    public static final String HEADER_ACL = "x-amz-acl";

    /**
     * ACL: 公开读
     */
    public static final String ACL_PUBLIC_READ = "public-read";

    /**
     * ACL: 私有
     */
    public static final String ACL_PRIVATE = "private";

    // ==================== 对象元数据 Key ====================

    /**
     * 元数据: 原始文件名
     */
    public static final String METADATA_ORIGINAL_NAME = "original-name";

    /**
     * 元数据: 资源组 ID
     */
    public static final String METADATA_ASSET_GROUP_ID = "asset-group-id";

    /**
     * 元数据: 角色
     */
    public static final String METADATA_ROLE = "role";

    /**
     * 元数据: MD5
     */
    public static final String METADATA_CONTENT_MD5 = "content-md5";

    // ==================== 请求元数据字段 ====================

    /**
     * 请求元数据: items 字段
     */
    public static final String REQUEST_METADATA_ITEMS = "items";

    /**
     * 请求元数据: folderId 字段
     */
    public static final String REQUEST_METADATA_FOLDER_ID = "folderId";

    /**
     * 请求元数据 item: name 字段
     */
    public static final String ITEM_FIELD_NAME = "name";

    /**
     * 请求元数据 item: groupId 字段
     */
    public static final String ITEM_FIELD_GROUP_ID = "groupId";

    /**
     * 请求元数据 item: role 字段
     */
    public static final String ITEM_FIELD_ROLE = "role";

    /**
     * 请求元数据 item: md5 字段
     */
    public static final String ITEM_FIELD_MD5 = "md5";

    // ==================== Slug 生成相关 ====================

    /**
     * Slug 最大长度
     */
    public static final int SLUG_MAX_LENGTH = 50;

    // ==================== 通配符 ====================

    /**
     * MIME 类型通配符后缀
     */
    public static final String MIME_WILDCARD_SUFFIX = "/*";

}
