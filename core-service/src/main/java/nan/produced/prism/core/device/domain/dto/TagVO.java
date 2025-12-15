package nan.produced.prism.core.device.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TagVO {

    private String tagName;

    private String tagSlug;

    private String description;

    private String color;

    private String icon;


}
