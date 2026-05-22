package com.silver.ai.mcpgateway.infrastructure.mcp;

import com.silver.ai.mcpgateway.domain.model.McpAgentSession;
import com.silver.ai.mcpgateway.infrastructure.config.McpSessionProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mcp.session", name = "store-type", havingValue = "memory", matchIfMissing = true)
public class InMemoryMcpSessionStore implements McpSessionStore {

    private final Map<Long, Map<String, SessionEntry>> sessionsBySource = new ConcurrentHashMap<>();
    private final McpSessionProperties sessionProperties;

    public InMemoryMcpSessionStore(McpSessionProperties sessionProperties) {
        this.sessionProperties = sessionProperties;
    }

    @PostConstruct
    void warnSingleInstance() {
        log.warn("Using in-memory session store — sessions will NOT be shared across instances. "
                + "Set app.mcp.session.store-type=redis for multi-instance deployments.");
    }

    @Override
    public Optional<McpAgentSession> find(Long sourceId, String sessionId) {
        if (sourceId == null || sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }

        Map<String, SessionEntry> sourceSessions = sessionsBySource.get(sourceId);
        if (sourceSessions == null) {
            return Optional.empty();
        }

        SessionEntry entry = sourceSessions.get(sessionId);
        if (entry == null) {
            return Optional.empty();
        }

        if (entry.isExpired()) {
            sourceSessions.remove(sessionId);
            if (sourceSessions.isEmpty()) {
                sessionsBySource.remove(sourceId);
            }
            return Optional.empty();
        }

        return Optional.of(entry.session());
    }

    @Override
    public void save(McpAgentSession session) {
        if (session == null || session.getSourceId() == null || session.getSessionId() == null || session.getSessionId().isBlank()) {
            return;
        }

        sessionsBySource
                .computeIfAbsent(session.getSourceId(), ignored -> new ConcurrentHashMap<>())
                .put(session.getSessionId(), new SessionEntry(session, Instant.now().plus(sessionProperties.getTtl())));
    }

    @Override
    public void removeSession(Long sourceId, String sessionId) {
        if (sourceId == null || sessionId == null) {
            return;
        }
        Map<String, SessionEntry> sourceSessions = sessionsBySource.get(sourceId);
        if (sourceSessions == null) {
            return;
        }
        sourceSessions.remove(sessionId);
        if (sourceSessions.isEmpty()) {
            sessionsBySource.remove(sourceId);
        }
    }

    @Override
    public void evictSource(Long sourceId) {
        if (sourceId != null) {
            sessionsBySource.remove(sourceId);
        }
    }

    @Scheduled(fixedDelayString = "#{@mcpSessionProperties.cleanupInterval.toMillis()}")
    public void cleanupExpiredSessions() {
        AtomicInteger removed = new AtomicInteger();
        sessionsBySource.forEach((sourceId, sourceSessions) -> {
            sourceSessions.entrySet().removeIf(entry -> {
                if (entry.getValue().isExpired()) {
                    removed.incrementAndGet();
                    return true;
                }
                return false;
            });
            if (sourceSessions.isEmpty()) {
                sessionsBySource.remove(sourceId);
            }
        });
        if (removed.get() > 0) {
            log.debug("Cleaned up {} expired in-memory MCP sessions", removed.get());
        }
    }

    private record SessionEntry(McpAgentSession session, Instant expiresAt) {

        private boolean isExpired() {
            return expiresAt != null && Instant.now().isAfter(expiresAt);
        }
    }
}