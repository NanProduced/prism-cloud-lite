package nan.produced.prism.core.user.converter;

import java.util.Map;
import nan.produced.prism.core.user.domain.UserProfileEntity;
import nan.produced.prism.core.user.dto.UserProfileView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface UserProfileConverter {

    @Mapping(target = "avatarId", expression = "java(extractAvatarId(entity.getConfigs()))")
    UserProfileView toView(UserProfileEntity entity);

    default String extractAvatarId(Map<String, Object> configs) {
        if (configs == null || configs.isEmpty()) {
            return null;
        }
        Object settingsRaw = configs.get("settings");
        if (!(settingsRaw instanceof Map<?, ?> settings)) {
            return null;
        }
        Object profileRaw = settings.get("profile");
        if (!(profileRaw instanceof Map<?, ?> profile)) {
            return null;
        }
        Object avatarId = profile.get("avatarId");
        return avatarId == null ? null : avatarId.toString();
    }
}
