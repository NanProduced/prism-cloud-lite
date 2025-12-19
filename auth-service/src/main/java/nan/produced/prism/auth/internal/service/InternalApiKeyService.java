package nan.produced.prism.auth.internal.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.domain.apikey.ApiKeyEntity;
import nan.produced.prism.auth.domain.apikey.repository.ApiKeyRepository;
import nan.produced.prism.auth.domain.user.EndUserEntity;
import nan.produced.prism.auth.domain.user.repository.EndUserRepository;
import nan.produced.prism.auth.internal.dto.InternalApiKeyCreateRequest;
import nan.produced.prism.auth.internal.dto.InternalApiKeyView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InternalApiKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String CLIENT_ID_PREFIX = "prism_";

    private static final String CLIENT_SETTING_OWNER_USER_UUID = "prism.ownerUserUuid";
    private static final String CLIENT_SETTING_OWNER_PUBLIC_ID = "prism.ownerPublicId";

    private static final Duration DEFAULT_ACCESS_TOKEN_TTL = Duration.ofMinutes(60);

    private final EndUserRepository endUserRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final RegisteredClientRepository registeredClientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<InternalApiKeyView> listUserApiKeys(UUID userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "userId is required");
        }
        return apiKeyRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(it -> new InternalApiKeyView(
                it.getId(),
                it.getName(),
                it.getClientId(),
                null,
                it.getCreatedAt(),
                it.getLastUsedAt()
            ))
            .toList();
    }

    @Transactional
    public InternalApiKeyView createApiKey(UUID userId, InternalApiKeyCreateRequest request) {
        if (userId == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "userId is required");
        }
        if (request == null || !StringUtils.hasText(request.name())) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "name is required");
        }

        EndUserEntity user = endUserRepository.findById(userId)
            .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));

        String id = UUID.randomUUID().toString();
        String clientId = generateClientId();
        String rawSecret = randomToken(48);

        RegisteredClient registeredClient = RegisteredClient.withId(id)
            .clientId(clientId)
            .clientSecret(passwordEncoder.encode(rawSecret))
            .clientName(request.name().trim())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope("read")
            .scope("write")
            .clientSettings(ClientSettings.builder()
                .requireAuthorizationConsent(false)
                .setting(CLIENT_SETTING_OWNER_USER_UUID, user.getId().toString())
                .setting(CLIENT_SETTING_OWNER_PUBLIC_ID, user.getPublicId())
                .build())
            .tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(DEFAULT_ACCESS_TOKEN_TTL)
                .build())
            .build();

        registeredClientRepository.save(registeredClient);

        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setName(request.name().trim());
        entity.setClientId(clientId);
        apiKeyRepository.save(entity);

        return new InternalApiKeyView(
            entity.getId(),
            entity.getName(),
            entity.getClientId(),
            rawSecret,
            entity.getCreatedAt(),
            entity.getLastUsedAt()
        );
    }

    @Transactional
    public InternalApiKeyView regenerateSecret(UUID userId, String apiKeyId) {
        if (userId == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "userId is required");
        }
        if (!StringUtils.hasText(apiKeyId)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "apiKeyId is required");
        }

        ApiKeyEntity apiKey = apiKeyRepository.findByIdAndUserId(apiKeyId, userId)
            .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, "api key not found"));

        RegisteredClient existing = registeredClientRepository.findById(apiKeyId);
        if (existing == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "registered client not found");
        }

        String rawSecret = randomToken(48);
        RegisteredClient updated = RegisteredClient.from(existing)
            .clientSecret(passwordEncoder.encode(rawSecret))
            .build();
        registeredClientRepository.save(updated);

        return new InternalApiKeyView(
            apiKey.getId(),
            apiKey.getName(),
            apiKey.getClientId(),
            rawSecret,
            apiKey.getCreatedAt(),
            apiKey.getLastUsedAt()
        );
    }

    @Transactional
    public void revokeApiKey(UUID userId, String apiKeyId) {
        if (userId == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "userId is required");
        }
        if (!StringUtils.hasText(apiKeyId)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "apiKeyId is required");
        }

        ApiKeyEntity apiKey = apiKeyRepository.findByIdAndUserId(apiKeyId, userId)
            .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, "api key not found"));

        apiKeyRepository.delete(apiKey);
        // Delete from oauth2_registered_client to fully disable client authentication.
        jdbcTemplate.update("delete from oauth2_registered_client where id = ?", apiKeyId);
    }

    private String generateClientId() {
        return CLIENT_ID_PREFIX + randomToken(18);
    }

    private String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }
}

