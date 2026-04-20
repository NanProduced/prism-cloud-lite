package nan.produced.prism.core.assistant.application.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.application.tools.AssistantToolCall;
import nan.produced.prism.core.assistant.application.tools.AssistantToolExecutor;
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
public class AssistantToolCallAuditAspect {

    private final AssistantAuditEventPublisher auditEventPublisher;

    @Pointcut("execution(* nan.produced.prism.core.assistant.application.tools.AssistantToolExecutor.execute(..))")
    public void toolExecutePointcut() {
    }

    @Around("toolExecutePointcut()")
    public Object aroundToolExecute(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        UUID userId = null;
        AssistantToolCall call = null;

        for (Object arg : args) {
            if (arg instanceof UUID) {
                userId = (UUID) arg;
            } else if (arg instanceof AssistantToolCall) {
                call = (AssistantToolCall) arg;
            }
        }

        long startedAt = System.currentTimeMillis();
        Object result = null;
        Throwable thrownException = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            thrownException = e;
            throw e;
        } finally {
            try {
                publishAuditEvent(userId, call, result, thrownException, startedAt);
            } catch (Exception e) {
                log.debug("Failed to publish tool call audit event", e);
            }
        }
    }

    private void publishAuditEvent(UUID userId,
                                    AssistantToolCall call,
                                    Object result,
                                    Throwable thrownException,
                                    long startedAt) {
        if (userId == null || call == null) {
            return;
        }

        boolean success = thrownException == null;
        long elapsedMs = System.currentTimeMillis() - startedAt;
        String errorText = null;
        com.fasterxml.jackson.databind.JsonNode output = null;

        if (result instanceof AssistantToolExecutor.ToolExecutionResult execResult) {
            success = execResult.success();
            elapsedMs = execResult.elapsedMs();
            errorText = execResult.errorText();
            output = execResult.output();
        }

        if (thrownException != null && errorText == null) {
            errorText = thrownException.getMessage() != null
                    ? thrownException.getMessage()
                    : thrownException.getClass().getSimpleName();
        }

        String traceId = TraceUtils.getTraceId();
        if ("unknown".equalsIgnoreCase(traceId)) {
            traceId = null;
        }

        AssistantToolCallAuditEvent event = AssistantToolCallAuditEvent.create(
                userId,
                call.toolCallId(),
                call.toolName(),
                call.input(),
                output,
                success,
                elapsedMs,
                errorText,
                traceId
        );

        auditEventPublisher.publishToolCallEvent(event);

        log.debug("Tool call audit event published via AOP: toolName={}, toolCallId={}, success={}",
                call.toolName(), call.toolCallId(), success);
    }
}
