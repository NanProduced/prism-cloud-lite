package nan.produced.prism.core.admin.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.admin.config.AdminConsoleProps;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class GatewaySessionRevoker {

    private final StringRedisTemplate redisTemplate;
    private final AdminConsoleProps props;

    /**
     * Delete all gateway Spring Session entries for the given principal name.
     *
     * @return number of session IDs found for principal
     */
    public int revokeAllByPrincipalName(String principalName) {
        if (!StringUtils.hasText(principalName)) {
            return 0;
        }

        String namespace = StringUtils.hasText(props.getGatewaySessionRedisNamespace())
            ? props.getGatewaySessionRedisNamespace().trim()
            : "prism:gateway:session";

        String indexKey = namespace + ":sessions:index:principalName:" + principalName;

        try {
            Set<String> sessionIds = redisTemplate.opsForSet().members(indexKey);
            if (sessionIds == null || sessionIds.isEmpty()) {
                return 0;
            }

            List<String> keys = new ArrayList<>(sessionIds.size() * 2 + 1);
            for (String sessionId : sessionIds) {
                if (!StringUtils.hasText(sessionId)) {
                    continue;
                }
                keys.add(namespace + ":sessions:" + sessionId);
                keys.add(namespace + ":sessions:expires:" + sessionId);
            }
            keys.add(indexKey);

            redisTemplate.delete(keys);
            log.info("Gateway sessions revoked: principalName={}, sessions={}", principalName, sessionIds.size());
            return sessionIds.size();
        } catch (Exception ex) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "删除 gateway 会话失败: " + ex.getMessage(), ex);
        }
    }
}

