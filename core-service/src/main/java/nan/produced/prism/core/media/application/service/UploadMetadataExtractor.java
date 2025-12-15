package nan.produced.prism.core.media.application.service;

import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static nan.produced.prism.core.media.application.service.BetterUploadConstant.*;

/**
 * 上传元数据提取器
 * <p>
 * 封装从请求元数据中提取文件相关信息的逻辑
 *
 * @author Nan
 */
public class UploadMetadataExtractor {

    private final Map<String, Object> metadata;

    public UploadMetadataExtractor(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * 提取文件夹 ID
     */
    public String extractFolderId() {
        return extractTopLevelString();
    }

    /**
     * 提取指定文件的 groupId
     */
    public String extractGroupId(String fileName) {
        return extractFromItem(fileName, ITEM_FIELD_GROUP_ID, UUID.randomUUID().toString());
    }

    /**
     * 提取指定文件的 role
     */
    public String extractRole(String fileName) {
        return extractFromItem(fileName, ITEM_FIELD_ROLE, DEFAULT_ROLE);
    }

    /**
     * 提取指定文件的 MD5
     */
    public String extractMd5(String fileName) {
        return extractFromItem(fileName, ITEM_FIELD_MD5, null);
    }

    /**
     * 构建指定文件的 S3 对象元数据
     */
    public Map<String, String> buildObjectMetadata(String fileName) {
        var objectMetadata = new HashMap<String, String>();
        objectMetadata.put(METADATA_ORIGINAL_NAME, fileName);

        var groupId = extractFromItem(fileName, ITEM_FIELD_GROUP_ID, null);
        var role = extractFromItem(fileName, ITEM_FIELD_ROLE, null);
        var md5 = extractFromItem(fileName, ITEM_FIELD_MD5, null);

        if (StringUtils.hasText(groupId)) {
            objectMetadata.put(METADATA_ASSET_GROUP_ID, groupId);
        }
        if (StringUtils.hasText(role)) {
            objectMetadata.put(METADATA_ROLE, role);
        }
        if (StringUtils.hasText(md5)) {
            objectMetadata.put(METADATA_CONTENT_MD5, md5);
        }

        return objectMetadata;
    }

    /**
     * 从顶层元数据中提取字符串值
     */
    private String extractTopLevelString() {
        if (metadata == null) {
            return BetterUploadConstant.DEFAULT_FOLDER_ID;
        }
        var value = metadata.get(BetterUploadConstant.REQUEST_METADATA_FOLDER_ID);
        if (value instanceof String str && StringUtils.hasText(str)) {
            return str;
        }
        return BetterUploadConstant.DEFAULT_FOLDER_ID;
    }

    /**
     * 从 items 列表中提取指定文件的字段值
     */
    private String extractFromItem(String fileName, String fieldName, String defaultValue) {
        if (metadata == null) {
            return defaultValue;
        }

        return extractFromItemList(fileName, fieldName, defaultValue);
    }

    /**
     * 从 items 列表中提取指定文件的字段值
     */
    @SuppressWarnings("unchecked")
    private String extractFromItemList(String fileName, String fieldName, String defaultValue) {
        var items = metadata.get(REQUEST_METADATA_ITEMS);
        if (!(items instanceof List<?> list)) {
            return defaultValue;
        }

        for (var item : list) {
            if (item instanceof Map<?, ?> map) {
                var name = (String) map.get(ITEM_FIELD_NAME);
                if (fileName.equals(name)) {
                    var value = (String) map.get(fieldName);
                    if (StringUtils.hasText(value)) {
                        return value;
                    }
                    // 找到匹配的文件名但没有有效的值时，停止继续查找
                    return defaultValue;
                }
            }
        }

        return defaultValue;
    }
}
