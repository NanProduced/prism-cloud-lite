package nan.produced.prism.core.device.domain.customfield;

import lombok.Getter;

@Getter
public enum CustomFieldType {

    /**
     * 文本
     */
    TEXT,

    /**
     * 数字
     */
    NUMBER,

    /**
     * 时间
     */
    DATETIME,

    /**
     * 布尔
     */
    BOOLEAN,

    /**
     * 选择
     */
    SELECT,

    /**
     * 多选
     */
    MULTI_SELECT,

    /**
     * 链接
     */
    URL,

    /**
     * 邮箱
     */
    EMAIL,

    /**
     *  电话
     */
    PHONE,

    /**
     * 国家
     */
    COUNTRY;

}
