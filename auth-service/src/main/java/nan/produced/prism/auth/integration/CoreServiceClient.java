package nan.produced.prism.auth.integration;

import nan.produced.prism.auth.common.response.ApiResponse;
import nan.produced.prism.auth.integration.config.CoreServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign 客户端 - 与 Core-Service 的通信
 * 用于初始化用户资料和配额
 * <p>
 * 注意：使用 CoreServiceFeignConfig 配置服务签名拦截器
 * 签名方案：X-Service-From、X-Timestamp、X-Signature
 *
 * @author Nan
 */
@FeignClient(
    name = "core-service",
    configuration = CoreServiceFeignConfig.class
)
public interface CoreServiceClient {

    /**
     * 初始化用户资料
     * @param request 用户初始化请求
     * @return 初始化结果（包装在ApiResponse中）
     */
    @PostMapping("/internal/users/initialize")
    ApiResponse<InitializeUserResponse> initializeUser(@RequestBody InitializeUserRequest request);

    /**
     * 用户初始化请求
     */
    record InitializeUserRequest(
        /* Auth-Service 中的用户 ID */
        String userId,
        /* 公共标识符 */
        String publicId,
        /* 邮箱 */
        String email,
        /* 电话（可选） */
        String phone,
        /* 显示名称（可选） */
        String displayName,
        /* 订阅等级（默认 FREE） */
        String subscriptionTier
    ) {}

    /**
     * 用户初始化响应
     */
    record InitializeUserResponse(
        /* 是否初始化成功 */
        boolean success,
        /* 初始化的用户 ID（Core-Service 中的 ID） */
        String coreUserId,
        /* 错误信息（如果有） */
        String errorMessage
    ) {}
}
