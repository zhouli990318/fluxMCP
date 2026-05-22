package com.silver.ai.mcpgateway.infrastructure.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.silver.ai.mcpgateway.domain.model.McpAgentSession;
import com.silver.ai.mcpgateway.infrastructure.config.McpSessionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisMcpSessionStoreTest {

    private RedissonClient redissonClient;
    private RedisMcpSessionStore store;
    private McpSessionProperties properties;

    @SuppressWarnings("unchecked")
    private final RMapCache<Object, Object> mapCache = mock(RMapCache.class);

    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        properties = new McpSessionProperties();
        properties.setTtl(Duration.ofMinutes(30));
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        store = new RedisMcpSessionStore(redissonClient, objectMapper, properties);
    }

    @Test
    void shouldSaveSessionToRedis() {
        when(redissonClient.getMapCache("mcp:gateway:sessions:1")).thenReturn(mapCache);

        McpAgentSession session = McpAgentSession.builder()
                .sourceId(1L)
                .sessionId("s-1")
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .clientName("test-client")
                .build();

        store.save(session);

        verify(mapCache).put(eq("s-1"), anyString(), eq(Duration.ofMinutes(30).toMillis()), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void shouldFindSessionFromRedis() throws Exception {
        when(redissonClient.getMapCache("mcp:gateway:sessions:1")).thenReturn(mapCache);

        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        McpAgentSession original = McpAgentSession.builder()
                .sourceId(1L)
                .sessionId("s-1")
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .clientName("test-client")
                .build();
        when(mapCache.get("s-1")).thenReturn(om.writeValueAsString(original));

        Optional<McpAgentSession> found = store.find(1L, "s-1");

        assertTrue(found.isPresent());
        assertEquals("test-client", found.get().getClientName());
        assertEquals("s-1", found.get().getSessionId());
    }

    @Test
    void shouldReturnEmptyForMissingSession() {
        when(redissonClient.getMapCache("mcp:gateway:sessions:1")).thenReturn(mapCache);
        when(mapCache.get("unknown")).thenReturn(null);

        Optional<McpAgentSession> found = store.find(1L, "unknown");

        assertFalse(found.isPresent());
    }

    @Test
    void shouldRemoveSession() {
        when(redissonClient.getMapCache("mcp:gateway:sessions:1")).thenReturn(mapCache);

        store.removeSession(1L, "s-1");

        verify(mapCache).remove("s-1");
    }

    @Test
    void shouldEvictSource() {
        when(redissonClient.getMapCache("mcp:gateway:sessions:2")).thenReturn(mapCache);

        store.evictSource(2L);

        verify(mapCache).delete();
    }

    @Test
    void shouldIgnoreNullInputsOnSave() {
        store.save(null);
        store.save(McpAgentSession.builder().sourceId(null).sessionId("s-1").build());
        store.save(McpAgentSession.builder().sourceId(1L).sessionId(null).build());
        store.save(McpAgentSession.builder().sourceId(1L).sessionId("  ").build());

        verify(redissonClient, never()).getMapCache(anyString());
    }

    @Test
    void shouldIgnoreNullInputsOnFind() {
        assertFalse(store.find(null, "s-1").isPresent());
        assertFalse(store.find(1L, null).isPresent());
        assertFalse(store.find(1L, "  ").isPresent());

        verify(redissonClient, never()).getMapCache(anyString());
    }

    @Test
    void shouldReturnEmptyForCorruptedJson() {
        when(redissonClient.getMapCache("mcp:gateway:sessions:1")).thenReturn(mapCache);
        when(mapCache.get("s-bad")).thenReturn("{not valid json!!!");

        Optional<McpAgentSession> found = store.find(1L, "s-bad");

        assertFalse(found.isPresent());
    }
}
