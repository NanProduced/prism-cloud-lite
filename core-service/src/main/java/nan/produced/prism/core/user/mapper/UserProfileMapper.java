package nan.produced.prism.core.user.mapper;

import nan.produced.prism.core.user.domain.UserProfileEntity;
import nan.produced.prism.core.user.dto.UserProfileView;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserProfileMapper {

    UserProfileView toView(UserProfileEntity entity);
}
