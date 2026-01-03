package nan.produced.prism.auth.internal.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.domain.user.EndUserEntity;
import nan.produced.prism.auth.domain.user.UserStatus;
import nan.produced.prism.auth.domain.user.repository.EndUserRepository;
import nan.produced.prism.auth.internal.dto.InternalUserResponse;
import nan.produced.prism.auth.internal.dto.InternalUserSearchItem;
import nan.produced.prism.auth.internal.dto.InternalUserSearchPageView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public InternalUserSearchPageView search(String q, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Page<EndUserEntity> result = endUserRepository.search(q, PageRequest.of(safePage, safeSize));

        return InternalUserSearchPageView.builder()
            .items(result.getContent().stream().map(it -> InternalUserSearchItem.builder()
                .publicId(it.getPublicId())
                .userId(it.getId() != null ? it.getId().toString() : null)
                .email(it.getEmail())
                .phone(it.getPhone())
                .status(it.getStatus() != null ? it.getStatus().name() : null)
                .createdAt(it.getCreatedAt())
                .build()).toList())
            .page(safePage)
            .size(safeSize)
            .total(result.getTotalElements())
            .build();
    }

    @Transactional
    public void lockUser(UUID userId) {
        EndUserEntity user = endUserRepository.findById(userId)
            .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        user.setStatus(UserStatus.LOCKED);
        endUserRepository.save(user);
    }

    @Transactional
    public void unlockUser(UUID userId) {
        EndUserEntity user = endUserRepository.findById(userId)
            .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        user.setStatus(UserStatus.ACTIVE);
        endUserRepository.save(user);
    }
}
