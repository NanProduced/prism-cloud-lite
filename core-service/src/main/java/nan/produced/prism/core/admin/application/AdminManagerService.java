package nan.produced.prism.core.admin.application;

import java.util.List;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.admin.api.dto.AdminManagerCreateRequest;
import nan.produced.prism.core.admin.api.dto.AdminManagerCreatedView;
import nan.produced.prism.core.admin.api.dto.AdminManagerItemView;
import nan.produced.prism.core.admin.api.dto.AdminManagerPageView;
import nan.produced.prism.core.admin.api.dto.AdminManagerPasswordResetView;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.integration.auth.client.AuthAdminUserInternalClient;
import nan.produced.prism.core.integration.auth.dto.AuthAdminUserCreateRequest;
import nan.produced.prism.core.integration.auth.dto.AuthAdminUserCreatedView;
import nan.produced.prism.core.integration.auth.dto.AuthAdminUserItem;
import nan.produced.prism.core.integration.auth.dto.AuthAdminUserPageView;
import nan.produced.prism.core.integration.auth.dto.AuthAdminUserPasswordResetView;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminManagerService {

    private static final String AUTH_SUCCESS_CODE = "AUTH-0000";

    private final AuthAdminUserInternalClient authAdminUserInternalClient;

    public AdminManagerPageView listManagers(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));

        ApiResponse<AuthAdminUserPageView> response = authAdminUserInternalClient.list("MANAGER", safePage, safeSize);
        AuthAdminUserPageView remote = requireAuthSuccess(response, "查询 managers 失败");
        if (remote == null || remote.items() == null) {
            return new AdminManagerPageView(List.of(), safePage, safeSize, 0);
        }
        List<AdminManagerItemView> items = remote.items().stream().map(this::toItem).toList();
        return new AdminManagerPageView(items, remote.page(), remote.size(), remote.total());
    }

    public AdminManagerCreatedView createManager(AdminManagerCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.username())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "username is required");
        }
        ApiResponse<AuthAdminUserCreatedView> response = authAdminUserInternalClient.create(
            new AuthAdminUserCreateRequest(request.username(), request.password())
        );
        AuthAdminUserCreatedView remote = requireAuthSuccess(response, "创建 manager 失败");
        return new AdminManagerCreatedView(remote.publicId(), remote.userId(), remote.username(), remote.initialPassword());
    }

    public void lockManager(String publicId) {
        if (!StringUtils.hasText(publicId)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "publicId is required");
        }
        ApiResponse<Object> response = authAdminUserInternalClient.lock(publicId);
        requireAuthSuccess(response, "锁定 manager 失败");
    }

    public void unlockManager(String publicId) {
        if (!StringUtils.hasText(publicId)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "publicId is required");
        }
        ApiResponse<Object> response = authAdminUserInternalClient.unlock(publicId);
        requireAuthSuccess(response, "解锁 manager 失败");
    }

    public AdminManagerPasswordResetView resetPassword(String publicId) {
        if (!StringUtils.hasText(publicId)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "publicId is required");
        }
        ApiResponse<AuthAdminUserPasswordResetView> response = authAdminUserInternalClient.resetPassword(publicId);
        AuthAdminUserPasswordResetView remote = requireAuthSuccess(response, "重置 manager 密码失败");
        return new AdminManagerPasswordResetView(remote.publicId(), remote.userId(), remote.newPassword());
    }

    private AdminManagerItemView toItem(AuthAdminUserItem item) {
        if (item == null) {
            return null;
        }
        return new AdminManagerItemView(item.publicId(), item.userId(), item.username(), item.status(), item.createdAt());
    }

    private <T> T requireAuthSuccess(ApiResponse<T> response, String message) {
        if (response == null) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, message + ": null response");
        }
        if (!AUTH_SUCCESS_CODE.equals(response.getCode())) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, message + ": " + response.getMessage());
        }
        return response.getData();
    }
}

