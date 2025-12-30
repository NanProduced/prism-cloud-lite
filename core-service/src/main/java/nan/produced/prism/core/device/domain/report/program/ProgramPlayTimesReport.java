package nan.produced.prism.core.device.domain.report.program;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import nan.produced.prism.core.device.domain.report.jackson.UtcOffsetDateTimeDeserializer;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedList;

/**
 * 节目播放时长/次数数据上报
 * <p>筛选查询使用UTC时间，设备本地时间可能不准确</p>
 *
 * @author Nan
 */
@Data
public class ProgramPlayTimesReport {

    /**
     * 节目VSN名称
     */
    private String programVsn;

    /**
     * 节目名称
     */
    private String programName;

    /**
     * LAN节目的ID
     * <li>LAN节目：局域网下发的，这个值是设备生成的programId(String UUID)</li>
     * <li>我们平台下发的节目没有这个Id</li>
     */
    @JsonProperty("programId")
    private String idStr;

    /**
     * 节目播放开始时间（本地）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LinkedList<LocalDateTime> startLocalTime;

    /**
     * 节目播放开始时间（UTC）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(contentUsing = UtcOffsetDateTimeDeserializer.class)
    private LinkedList<OffsetDateTime> startUtcTime;

    /**
     * 节目播放结束时间（本地）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LinkedList<LocalDateTime> endLocalTime;

    /**
     * 节目播放结束时间（UTC）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(contentUsing = UtcOffsetDateTimeDeserializer.class)
    private LinkedList<OffsetDateTime> endUtcTime;

    /**
     * 播放次数
     */
    @JsonProperty("times")
    private Integer playTimes;

    /**
     * 节目时长
     */
    private Long singleDuration;

    /**
     * 播放时长 - 实际播放时长，不一定等于（节目时长 x 播放次数）
     */
    @JsonProperty("duration")
    private Long playDuration;
}
