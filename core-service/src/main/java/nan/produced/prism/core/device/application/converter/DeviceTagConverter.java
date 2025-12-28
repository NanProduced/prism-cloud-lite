package nan.produced.prism.core.device.application.converter;

import java.time.OffsetDateTime;
import java.util.UUID;
import nan.produced.prism.core.device.domain.dto.TagVO;
import nan.produced.prism.core.device.domain.tags.DeviceTagEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;

/**
 * 设备标签 Mapper（MapStruct）
 *
 * @author Nan
 */
@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface DeviceTagConverter {

    @Mapping(target = "tagSlug", source = "slug")
    TagVO toVO(DeviceTagEntity entity);

    @Mapping(target = "tagId", source = "tagId")
    @Mapping(target = "tagName", source = "tagName")
    @Mapping(target = "slug", source = "slug")
    @Mapping(target = "color", source = "color")
    @Mapping(target = "icon", source = "icon")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "createTime", source = "now")
    @Mapping(target = "updateTime", source = "now")
    DeviceTagEntity toNewEntity(
            UUID userId,
            Long tagId,
            String tagName,
            String slug,
            String color,
            String icon,
            String description,
            OffsetDateTime now);

    @Mapping(target = "tagName", source = "tagName")
    @Mapping(target = "color", source = "color")
    @Mapping(target = "icon", source = "icon")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "updateTime", source = "updateTime")
    @Mapping(target = "tagId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    void applyUpdate(
            @MappingTarget DeviceTagEntity entity,
            String tagName,
            String color,
            String icon,
            String description,
            OffsetDateTime updateTime);
}
