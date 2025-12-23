package nan.produced.prism.core.device.domain.report.media;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import nan.produced.prism.core.program.api.dto.ProgramPublishReq;
import nan.produced.prism.core.program.domain.ProgramEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 素材播放时长/次数数据上报
 * <p>筛选查询使用UTC时间，设备本地时间可能不准确</p>
 *
 * @author Nan
 */
@Data
public class MediaPlayTimesReport {

    /**
     * 素材所在的节目的VSN文件名
     * <p>格式：节目名称_md5_size.vsn</p>
     * <P>例如：program2026-V8_aa07cdcb7468c6f2221ef88bdc8a23bf_11638.vsn</P>
     * <p>我们做了处理，在节目名后使用‘-Vx’制定了版本号</p>
     */
    private String programName;

    /**
     * 原始资源名称
     * <p>例如：testVideo.mp4</p>
     * <p>对应vsn中item的originName字段,设备不对此字段做处理，原样下发，原样上报，所以可以直接传素材ID（参考构建VSN的逻辑）</p>
     */
    private String resOriginName;

    /**
     * 资源md5名称
     * <P>例如：F_2EADDA07770BF31C7DE78642F4C3C8CC_11669035.mp4</P>
     */
    private String resMd5Name;

    /**
     * 素材所在节目页名称
     */
    private String pageName;

    /**
     * 素材所在节目页索引
     */
    private Integer pageIndex;

    /**
     * 素材所在区域名称
     */
    private String regionName;

    /**
     * 素材所在区域索引
     */
    private Integer regionIndex;

    /**
     * 播放开始时间（UTC）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private OffsetDateTime startUtcTime;

    /**
     * 播放开始时间（本地）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private OffsetDateTime startLocalTime;

    /**
     * 播放结束时间（UTC）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private OffsetDateTime endUtcTime;

    /**
     * 播放结束时间（本地）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private OffsetDateTime endLocalTime;

    /**
     * 素材播放时长（注意是实际播放时长，不一定等于素材时长）
     */
    private Long duration;

    /**
     * 素材类型
     */
    private String itemType;
}