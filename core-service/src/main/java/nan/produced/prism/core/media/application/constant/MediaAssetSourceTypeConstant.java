package nan.produced.prism.core.media.application.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MediaAssetSourceTypeConstant {

    /**
     * 素材来源类型：上传
     */
    public static final int UPLOAD = 1;

    /**
     * 素材来源类型：秒传
     */
    public static final int INSTANT = 2;

    /**
     * 素材来源类型：转码任务产物
     */
    public static final int TRANSCODE = 3;
}

