package nan.produced.prism.core.security.api;

import nan.produced.prism.core.common.exception.AuthException;
import nan.produced.prism.core.common.exception.ErrorCode;

/**
 * ThreadLocal 存储当前请求的用户信息
 * <p>
 * CloudAuthFilter 解析 CLOUD_AUTH 头后，将用户信息存储到此 ThreadLocal 中
 * 业务代码可通过 getCurrentUser() 或 getCurrentPublicId() 方法获取当前用户信息
 * <p>
 * 重要: Filter 必须在请求结束后调用 clear() 方法清理 ThreadLocal，防止内存泄漏
 *
 * @author Nan
 */
public class CloudAuthContext {

    private CloudAuthContext() {
        throw new IllegalStateException("CloudAuthContext is a utility class and cannot be instantiated");
    }

    private static final ThreadLocal<CloudAuthUser> currentUser = new ThreadLocal<>();

    /**
     * 设置当前请求的用户信息
     * 仅供 CloudAuthFilter 调用
     *
     * @param user 用户信息
     */
    public static void setCurrentUser(CloudAuthUser user) {
        currentUser.set(user);
    }

    /**
     * 获取当前请求的用户信息
     *
     * @return 用户信息
     * @throws AuthException 如果没有认证用户（通常说明没有经过 CloudAuthFilter）
     */
    public static CloudAuthUser getCurrentUser() {
        CloudAuthUser user = currentUser.get();
        if (user == null) {
            throw new AuthException(ErrorCode.NO_AUTHENTICATED_USER, "No authenticated user in current context");
        }
        return user;
    }

    /**
     * 获取当前用户的 publicId（快捷方法）
     *
     * @return publicId
     * @throws AuthException 如果没有认证用户
     */
    public static String getCurrentPublicId() {
        return getCurrentUser().publicId();
    }

    /**
     * 清理 ThreadLocal
     * 必须在请求结束后调用，防止内存泄漏
     */
    public static void clear() {
        currentUser.remove();
    }

    /**
     * 检查当前上下文是否有认证用户
     *
     * @return true 如果有认证用户
     */
    public static boolean hasAuthenticatedUser() {
        return currentUser.get() != null;
    }
}
