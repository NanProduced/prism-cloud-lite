package nan.produced.prism.gateway.realtime.sse;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sse")
public class SseController {

    private final GatewayUserIdentityService gatewayUserIdentityService;

    private final SseSessionRegistry sseSessionRegistry;

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication, HttpServletRequest request) {
        UUID userId = gatewayUserIdentityService.resolveUserId(authentication, request);
        if (userId == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Unauthenticated SSE request");
        }
        return sseSessionRegistry.register(userId);
    }

    @GetMapping(path = "/monitoring/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter monitoringStream(Authentication authentication,
                                       HttpServletRequest request,
                                       @RequestParam(value = "deviceIds", required = false) Set<Long> deviceIds) {
        UUID userId = gatewayUserIdentityService.resolveUserId(authentication, request);
        if (userId == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Unauthenticated SSE request");
        }
        return sseSessionRegistry.registerMonitoring(userId, deviceIds);
    }

    @GetMapping(path = "/map/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter mapStream(Authentication authentication,
                                HttpServletRequest request,
                                @RequestParam("deviceId") Long deviceId) {
        UUID userId = gatewayUserIdentityService.resolveUserId(authentication, request);
        if (userId == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Unauthenticated SSE request");
        }
        return sseSessionRegistry.registerMap(userId, deviceId);
    }
}
