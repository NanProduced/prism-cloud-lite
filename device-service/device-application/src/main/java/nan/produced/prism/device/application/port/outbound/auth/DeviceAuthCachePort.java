package nan.produced.prism.device.application.port.outbound.auth;

import nan.produced.prism.device.application.dto.cache.DeviceAuthCache;

import java.util.Optional;

/**
 * 终端认证缓存端口接口
 * 定义终端认证信息缓存的标准操作
 *
 * @author Nan
 */
public interface DeviceAuthCachePort {

    /**
     * 缓存终端认证信息
     *
     * @param accountName 账户名称
     * @param deviceAuthCache 认证缓存对象
     */
    void cache(String accountName, DeviceAuthCache deviceAuthCache);

    /**
     * 安全获取终端认证缓存信息
     *
     * @param accountName 账户名称
     * @return Optional包装的认证缓存对象
     */
    Optional<DeviceAuthCache> get(String accountName);

    /**
     * 移除终端认证缓存
     *
     * @param accountName 账户名称
     */
    void remove(String accountName);


    /**
     * 清空所有认证缓存
     */
    void clearAll();
}
