package nan.produced.prism.core.assistant.application.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatLlmClient;
import nan.produced.prism.core.common.util.TraceUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Aspect
@Component
@Order(100)
@RequiredArgsConstructor
public class AssistantChatTokenAuditAspect {

    private final AssistantAuditEventPublisher auditEventPublisher;

    @Pointcut("execution(* nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatLlmClient.stream(..))")
    public void llmStreamPointcut() {
    }

    @Around("llmStreamPointcut()")
    public Object aroundLlmStream(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        UUID userId = null;

        for (Object arg : args) {
            if (arg instanceof UUID) {
                userId = (UUID) arg;
                break;
            }
        }

        Object result = null;

        try {
            result = joinPoint.proceed();
            return result;
        } finally {
            try {
                publishTokenAuditEvent(userId, result);
            } catch (Exception e) {
                log.debug("Failed to publish token audit event", e);
            }
        }
    }

    private void publishTokenAuditEvent(UUID userId, Object result) {
        if (userId == null || !(result instanceof AssistantChatLlmClient.StreamResult streamResult)) {
            return;
        }

        boolean hasTokenData = streamResult.promptTokens() != null
                || streamResult.completionTokens() != null
                || streamResult.totalTokens() != null;

        if (!hasTokenData) {
            return;
        }

        String traceId = TraceUtils.getTraceId();
        if ("unknown".equalsIgnoreCase(traceId)) {
            traceId = null;
        }

        AssistantChatTokenAuditEvent event = AssistantChatTokenAuditEvent.create(
                userId,
                traceId,
                streamResult.promptTokens(),
                streamResult.completionTokens(),
                streamResult.totalTokens(),
                streamResult.model(),
                streamResult.provider()
        );

        auditEventPublisher.publishTokenUsageEvent(event);

        log.debug("Token usage audit event published via AOP: userId={}, totalTokens={}, provider={}",
                userId, streamResult.totalTokens(), streamResult.provider());
    }
}
