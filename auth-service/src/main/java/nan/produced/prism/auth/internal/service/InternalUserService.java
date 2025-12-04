package nan.produced.prism.auth.internal.service;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.domain.user.EndUserEntity;
import nan.produced.prism.auth.domain.user.repository.EndUserRepository;
import nan.produced.prism.auth.internal.dto.InternalUserResponse;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InternalUserService {

    private final EndUserRepository endUserRepository;

    public InternalUserResponse findByPublicId(String publicId) {
        EndUserEntity user = endUserRepository.findByPublicId(publicId)
            .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));

        return InternalUserResponse.builder()
            .publicId(user.getPublicId())
            .userId(user.getId() != null ? user.getId().toString() : null)
            .email(user.getEmail())
            .phone(user.getPhone())
            .build();
    }
}
