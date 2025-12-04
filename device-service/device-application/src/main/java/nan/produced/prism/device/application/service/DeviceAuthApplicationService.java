package nan.produced.prism.device.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.domain.device.DeviceAccount;
import nan.produced.prism.device.application.domain.device.DeviceAccountStatus;
import nan.produced.prism.device.application.dto.cache.DeviceAuthCache;
import nan.produced.prism.device.application.dto.request.AuthRequest;
import nan.produced.prism.device.application.dto.result.AuthResult;
import nan.produced.prism.device.application.port.inbound.auth.DeviceAuthUseCase;
import nan.produced.prism.device.application.port.outbound.auth.DeviceAuthCachePort;
import nan.produced.prism.device.application.port.outbound.auth.EncodePort;
import nan.produced.prism.device.application.port.outbound.repository.DeviceAccountRepository;
import nan.produced.prism.device.common.exception.DeviceResponseException;
import nan.produced.prism.device.common.exception.business.BusinessErrorCode;
import nan.produced.prism.device.common.exception.tech.TechErrorCode;
import nan.produced.prism.device.common.exception.tech.TechException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAuthApplicationService implements DeviceAuthUseCase {

    private final DeviceAccountRepository deviceAccountRepository;

    private final DeviceAuthCachePort deviceAuthCachePort;

    private final EncodePort encodePort;

    @Override
    public AuthResult authenticate(AuthRequest authRequest) {

        Optional<DeviceAuthCache> deviceAuthCache = deviceAuthCachePort.get(authRequest.getAccountName());
        if (deviceAuthCache.isPresent()) {
            AuthResult cacheResult = checkAuthenticationCache(authRequest.getAccountName(), authRequest.getRawPassword(), deviceAuthCache.get());
            if (cacheResult.isSuccess()) {
                return cacheResult;
            }
            // 验证失败，清除缓存并继续进行完整认证
            deviceAuthCachePort.remove(authRequest.getAccountName());
        }

        DeviceAccount deviceAccount = deviceAccountRepository.findDeviceAccountByName(authRequest.getAccountName());

        if (deviceAccount == null) {
            throw new DeviceResponseException(BusinessErrorCode.ACCOUNT_NOT_FOUND);
        }

        if (!encodePort.matchesByPasswordEncoder(authRequest.getRawPassword(), deviceAccount.getPasswordHash())) {
            throw new DeviceResponseException(BusinessErrorCode.INVALID_CREDENTIALS);
        }

        if (DeviceAccountStatus.DISABLE.equals(deviceAccount.getStatus())) {
            throw new DeviceResponseException(BusinessErrorCode.ACCOUNT_DISABLED);
        }

        String credentialsHash = calculateCredentialsHash(authRequest.getAccountName(), authRequest.getRawPassword());
        deviceAuthCachePort.cache(deviceAccount.getAccountName(), DeviceAuthCache.builder()
                        .deviceId(deviceAccount.getDeviceId())
                        .accountName(deviceAccount.getAccountName())
                        .credentialsHash(credentialsHash)
                        .accountStatus(deviceAccount.getStatus())
                        .build());
        return AuthResult.success(deviceAccount.getDeviceId());
    }

    private AuthResult checkAuthenticationCache(String accountName, String rawPassword, DeviceAuthCache authCache) {
        String credentialsHash = calculateCredentialsHash(accountName, rawPassword);
        if (credentialsHash.equals(authCache.getCredentialsHash())) {
            log.debug("DeviceAuthCache - 快速认证成功: accountName={}, deviceId={}", accountName, authCache.getDeviceId());
            return AuthResult.success(authCache.getDeviceId());
        }
        else {
            log.debug("DeviceAuthCache - 快速认证失败: accountName={}", accountName);
            return AuthResult.failed();
        }
    }

    /**
     * 计算凭据哈希值 - SHA-256(username:password)
     * 用于快速认证缓存验证，避免昂贵的BCrypt操作
     *
     * @param accountName 账户名
     * @param rawPassword 原始密码
     * @return SHA-256哈希值的十六进制字符串
     */
    private String calculateCredentialsHash(String accountName, String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String credentials = accountName + ":" + rawPassword;
            byte[] hashBytes = digest.digest(credentials.getBytes(StandardCharsets.UTF_8));

            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256算法不可用", e);
            throw new TechException(TechErrorCode.ALGORITHM_ERROR, "SHA-256算法不可用", e);
        }
    }
}
