package com.silver.ai.fluxmcp.infrastructure.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silver.ai.fluxmcp.domain.model.McpAgentSession;
import com.silver.ai.fluxmcp.infrastructure.config.McpSessionProperties;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mcp.session", name = "store-type", havingValue = "redis")
public class RedisMcpSessionStore implements McpSessionStore {

    private static final String SESSION_MAP_PREFIX = "flux:mcp:sessions:";

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final McpSessionProperties sessionProperties;

    public RedisMcpSessionStore(RedissonClient redissonClient,
                                ObjectMapper objectMapper,
                                McpSessionProperties sessionProperties) {
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.sessionProperties = sessionProperties;
    }

    @Override
    public Optional<McpAgentSession> find(Long sourceId, String sessionId) {
        if (sourceId == null || sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }

        String value = sessionMap(sourceId).get(sessionId);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(value, McpAgentSession.class));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to read MCP session {}", sessionId, ex);
            return Optional.empty();
        }
    }

    @Override
    public void save(McpAgentSession session) {
        if (session == null || session.getSourceId() == null || session.getSessionId() == null || session.getSessionId().isBlank()) {
            return;
        }

        sessionMap(session.getSourceId()).put(
                session.getSessionId(),
                writeValue(session),
                sessionProperties.getTtl().toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    public void removeSession(Long sourceId, String sessionId) {
        if (sourceId != null && sessionId != null) {
            sessionMap(sourceId).remove(sessionId);
        }
    }

    @Override
    public void evictSource(Long sourceId) {
        if (sourceId != null) {
            sessionMap(sourceId).delete();
        }
    }

    private RMapCache<String, String> sessionMap(Long sourceId) {
        return redissonClient.getMapCache(SESSION_MAP_PREFIX + sourceId);
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize MCP session payload", ex);
        }
    }
}