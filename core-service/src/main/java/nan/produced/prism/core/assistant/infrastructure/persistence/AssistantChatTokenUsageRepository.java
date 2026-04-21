package nan.produced.prism.core.assistant.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.domain.AssistantChatTokenFreezeEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class AssistantChatTokenUsageRepository {

    private final AssistantChatTokenUsageRepositoryJpa usageJpa;
    private final AssistantChatTokenFreezeRepository freezeRepository;

    public long getUsedTokens(UUID userId, LocalDate day) {
        return usageJpa.findByUserIdAndDay(userId, day)
                .map(e -> e.getUsedTokens() != null ? e.getUsedTokens() : 0L)
                .orElse(0L);
    }

    public long getFrozenTokensFromSummary(UUID userId, LocalDate day) {
        return usageJpa.findByUserIdAndDay(userId, day)
                .map(e -> e.getFrozenTokens() != null ? e.getFrozenTokens() : 0L)
                .orElse(0L);
    }

    public long getActiveFrozenTokens(UUID userId, LocalDate day) {
        return freezeRepository.sumActiveFrozenTokensByUserIdAndDay(userId, day);
    }

    public long getTotalCommittedAndFrozen(UUID userId, LocalDate day) {
        return getUsedTokens(userId, day) + getActiveFrozenTokens(userId, day);
    }

    public Optional<AssistantChatTokenFreezeEntity> findFreezeRecordByReqId(UUID reqId) {
        return freezeRepository.findByReqId(reqId);
    }

    @Transactional
    public Optional<UUID> tryFreezeTokens(UUID userId, LocalDate day, long tokensToFreeze, long dailyLimit) {
        if (tokensToFreeze <= 0) {
            return Optional.empty();
        }
        if (dailyLimit > 0 && tokensToFreeze > dailyLimit) {
            log.warn("Cannot freeze {} tokens for user {} on {}: exceeds daily limit {}",
                    tokensToFreeze, userId, day, dailyLimit);
            return Optional.empty();
        }

        UUID reqId = UUID.randomUUID();

        int inserted = usageJpa.tryInsertFreezeRecord(userId, day, reqId, tokensToFreeze, dailyLimit);
        if (inserted <= 0) {
            log.warn("Failed to freeze {} tokens for user {} on {} (dailyLimit={}): concurrent quota check failed",
                    tokensToFreeze, userId, day, dailyLimit);
            return Optional.empty();
        }

        int updated = usageJpa.incrementFrozenTokensNative(userId, day, tokensToFreeze);
        if (updated <= 0) {
            log.error("Failed to update summary table after freezing {} tokens for user {} on {}",
                    tokensToFreeze, userId, day);
        }

        log.debug("Successfully frozen {} tokens for user {} on {}, reqId={}",
                tokensToFreeze, userId, day, reqId);
        return Optional.of(reqId);
    }

    @Transactional
    public void settleAndRelease(UUID reqId, long actualTokensUsed) {
        Optional<AssistantChatTokenFreezeEntity> recordOpt = freezeRepository.findByReqId(reqId);
        if (recordOpt.isEmpty()) {
            log.warn("Cannot settle: freeze record not found for reqId={}", reqId);
            return;
        }

        AssistantChatTokenFreezeEntity record = recordOpt.get();
        if (!AssistantChatTokenFreezeEntity.STATUS_ACTIVE.equals(record.getStatus())) {
            log.warn("Cannot settle: freeze record {} has status '{}', expected 'ACTIVE'",
                    reqId, record.getStatus());
            return;
        }

        int updated = freezeRepository.markAsSettledByReqId(reqId);
        if (updated <= 0) {
            log.warn("Failed to update freeze record status for reqId={}", reqId);
            return;
        }

        if (actualTokensUsed > 0) {
            usageJpa.settleTokensWithPositiveUsed(
                    record.getUserId(),
                    record.getDay(),
                    actualTokensUsed,
                    record.getFrozenTokens());
        } else {
            usageJpa.releaseFrozenTokensFromSummary(
                    record.getUserId(),
                    record.getDay(),
                    record.getFrozenTokens());
        }

        log.debug("Settled freeze record {}: actualUsed={}, frozenReleased={}",
                reqId, actualTokensUsed, record.getFrozenTokens());
    }

    @Transactional
    public void releaseFrozenTokens(UUID reqId) {
        Optional<AssistantChatTokenFreezeEntity> recordOpt = freezeRepository.findByReqId(reqId);
        if (recordOpt.isEmpty()) {
            log.warn("Cannot release: freeze record not found for reqId={}", reqId);
            return;
        }

        AssistantChatTokenFreezeEntity record = recordOpt.get();
        if (!AssistantChatTokenFreezeEntity.STATUS_ACTIVE.equals(record.getStatus())) {
            log.warn("Cannot release: freeze record {} has status '{}', expected 'ACTIVE'",
                    reqId, record.getStatus());
            return;
        }

        int updated = freezeRepository.markAsReleasedByReqId(reqId);
        if (updated <= 0) {
            log.warn("Failed to update freeze record status for reqId={}", reqId);
            return;
        }

        usageJpa.releaseFrozenTokensFromSummary(
                record.getUserId(),
                record.getDay(),
                record.getFrozenTokens());

        log.info("Released frozen tokens for reqId={}, user={}, day={}, tokens={}",
                reqId, record.getUserId(), record.getDay(), record.getFrozenTokens());
    }

    @Transactional
    public int cleanupExpiredFrozenTokens(Duration maxAge) {
        Instant cutoff = Instant.now().minus(maxAge);
        int updatedFreeze = freezeRepository.markAsReleasedForExpired(cutoff);

        if (updatedFreeze > 0) {
            log.warn("Marked {} expired freeze records as RELEASED (age > {})", updatedFreeze, maxAge);

            int updatedSummary = usageJpa.updateSummaryForRecentlyReleased();
            if (updatedSummary > 0) {
                log.debug("Updated frozen_tokens summary for {} users", updatedSummary);
            }
        }

        return updatedFreeze;
    }
}
