package nan.produced.prism.core.program.application.service;

/**
 * Program/VSN 相关常量。
 * <p>
 * 用于收敛 VSN JSON/XML 字段名、约定值等“魔法值”，避免散落在业务代码里。
 */
final class ProgramVsnConstants {

    static final String KEY_PROGRAMS = "Programs";
    static final String KEY_RESOURCE_ID = "Resource_ID";
    static final String KEY_IS_RELATIVE = "IsRelative";
    static final String KEY_ORIGIN_NAME = "OriginName";
    static final String KEY_FILE_PATH = "FilePath";

    static final String INTERNAL_FIELD_PREFIX = "__";

    static final String RESOURCE_ID_EMPTY = "0";
    static final String FLAG_TRUE = "1";

    static final String DEFAULT_DEVICE_TITLE = "Program";

    static final String VSN_XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n\n";

    private ProgramVsnConstants() {
    }
}

