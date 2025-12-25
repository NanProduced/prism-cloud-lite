package nan.produced.prism.device.infrastructure.internal.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 为了符合项目架构不造成依赖问题
 * <P>和DeviceApiProgram一致</P>
 *
 * @author Nan
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeviceProgramDTO {

    private Integer id;

    private String date;

    @JsonProperty("date_gmt")
    private String dateGmt;

    private String modified;

    @JsonProperty("modified_gmt")
    private String modifiedGmt;

    private String type;

    private Title title;

    @JsonProperty("_links")
    private Links links;

    @Data
    public static class Title {

        private String rendered;
    }

    @Data
    public static class Links {

        @JsonProperty("wp:attachment")
        private List<AttachmentUrl> attachmentUrls;
    }

    @Data
    public static class AttachmentUrl {

        private String href;
    }
}
