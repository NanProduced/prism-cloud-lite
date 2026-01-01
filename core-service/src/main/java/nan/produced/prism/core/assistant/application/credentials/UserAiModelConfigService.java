package nan.produced.prism.core.assistant.application.credentials;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.infrastructure.crypto.AesGcmCryptoService;
import nan.produced.prism.core.assistant.infrastructure.persistence.UserAiModelConfigRepository;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAiModelConfigService {

    private final UserAiModelConfigRepository repository;
    private final AesGcmCryptoService cryptoService;

    public record UserAiModelConfigView(
            String provider,
            String model,
            boolean enabled,
            boolean isDefault,
            boolean hasApiKey,
            String apiKeyLast4
    ) {
    }

    public record ResolvedDefaultModel(
            String provider,
            String model,
            String apiKey
    ) {
    }

    public List<UserAiModelConfigView> listForUser(UUID userId) {
        return repository.listByUser(userId).stream().map(r -> new UserAiModelConfigView(
                r.provider(),
                r.model(),
                r.enabled(),
                r.isDefault(),
                StringUtils.hasText(r.apiKeyCiphertext()),
                r.apiKeyLast4()
        )).toList();
    }

    public void upsert(UUID userId,
                       String provider,
                       String model,
                       boolean enabled,
                       boolean makeDefault,
                       String apiKeyPlaintextOrNull) {
        String normalizedProvider = normalizeProvider(provider);

        String ciphertext = null;
        String last4 = null;
        var existing = repository.getByUserAndProvider(userId, normalizedProvider);
        if (StringUtils.hasText(apiKeyPlaintextOrNull)) {
            try {
                ciphertext = cryptoService.encryptToString(apiKeyPlaintextOrNull.trim());
                last4 = last4(apiKeyPlaintextOrNull.trim());
            } catch (IllegalStateException e) {
                throw new InfraException(ErrorCode.AI_CREDENTIALS_NOT_CONFIGURED, e.getMessage(), e);
            }
        } else if (existing != null) {
            ciphertext = existing.apiKeyCiphertext();
            last4 = existing.apiKeyLast4();
        }

        boolean isDefault = makeDefault;
        if (makeDefault) {
            repository.clearDefaultForUser(userId);
        } else if (existing != null && existing.isDefault()) {
            isDefault = true;
        }

        repository.upsert(
                existing != null ? existing.id() : UUID.randomUUID(),
                userId,
                normalizedProvider,
                model,
                enabled,
                isDefault,
                ciphertext,
                last4
        );
    }

    public void setDefault(UUID userId, String provider) {
        String normalizedProvider = normalizeProvider(provider);
        var existing = repository.getByUserAndProvider(userId, normalizedProvider);
        repository.clearDefaultForUser(userId);
        if (existing == null) {
            repository.upsert(UUID.randomUUID(), userId, normalizedProvider, null, true, true, null, null);
            return;
        }
        repository.upsert(existing.id(), userId, existing.provider(), existing.model(), existing.enabled(), true, existing.apiKeyCiphertext(), existing.apiKeyLast4());
    }

    public void delete(UUID userId, String provider) {
        repository.deleteByUserAndProvider(userId, normalizeProvider(provider));
    }

    public String resolveApiKeyForUser(UUID userId, String provider) {
        var row = repository.getByUserAndProvider(userId, normalizeProvider(provider));
        if (row == null || !StringUtils.hasText(row.apiKeyCiphertext()) || !row.enabled()) {
            return null;
        }
        try {
            return cryptoService.decryptFromString(row.apiKeyCiphertext());
        } catch (IllegalStateException e) {
            throw new InfraException(ErrorCode.AI_CREDENTIALS_NOT_CONFIGURED, e.getMessage(), e);
        }
    }

    public ResolvedDefaultModel resolveDefaultModelForUser(UUID userId) {
        var row = repository.getDefaultByUser(userId);
        if (row == null || !row.enabled()) {
            return null;
        }
        String apiKey = null;
        if (StringUtils.hasText(row.apiKeyCiphertext())) {
            try {
                apiKey = cryptoService.decryptFromString(row.apiKeyCiphertext());
            } catch (IllegalStateException e) {
                throw new InfraException(ErrorCode.AI_CREDENTIALS_NOT_CONFIGURED, e.getMessage(), e);
            }
        }
        return new ResolvedDefaultModel(row.provider(), row.model(), apiKey);
    }

    private static String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            throw new BizException(ErrorCode.AI_PROVIDER_INVALID, "provider is required");
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("local-vllm") && !normalized.equals("openai") && !normalized.equals("gemini")) {
            throw new BizException(ErrorCode.AI_PROVIDER_INVALID, "Unsupported provider: " + normalized);
        }
        return normalized;
    }

    private static String last4(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }
        String trimmed = apiKey.trim();
        int n = trimmed.length();
        return n <= 4 ? trimmed : trimmed.substring(n - 4);
    }
}
