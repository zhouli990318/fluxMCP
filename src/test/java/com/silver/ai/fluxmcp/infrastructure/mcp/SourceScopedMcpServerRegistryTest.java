package com.silver.ai.fluxmcp.infrastructure.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silver.ai.fluxmcp.domain.model.ApiSource;
import com.silver.ai.fluxmcp.domain.port.ApiSourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.HttpMethod;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceScopedMcpServerRegistryTest {

    @Test
    void routeShouldMatchSourceScopedStreamablePath() {
    ApiSourceRepository apiSourceRepository = mock(ApiSourceRepository.class);
    DynamicApiToolCallbackProvider toolCallbackProvider = mock(DynamicApiToolCallbackProvider.class);
    when(toolCallbackProvider.getToolCallbacksForSource(1L)).thenReturn(new ToolCallback[0]);

    SourceScopedMcpServerRegistry registry = new SourceScopedMcpServerRegistry(
        apiSourceRepository,
        toolCallbackProvider,
        new ObjectMapper(),
        mock(McpSessionService.class)
    );
    setField(registry, "serverName", "flux-mcp");
    setField(registry, "serverVersion", "2.0.0");
    setField(registry, "requestTimeout", Duration.ofSeconds(30));
    setField(registry, "messageEndpoint", "/mcp/message");
    setField(registry, "keepAliveInterval", Duration.ofSeconds(15));

    registry.refreshSource(ApiSource.builder().id(1L).name("demo").active(true).build());

    ServerRequest request = ServerRequest.create(
        MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.POST, "/api/v1/mcp/sources/1/mcp/message")
                .header("Accept", "application/json, text/event-stream")
                .header("Content-Type", "application/json")
                .build()),
        List.<HttpMessageReader<?>>of()
    );

    HandlerFunction<ServerResponse> handler = registry.route(request).block();

    assertNotNull(handler);
    assertEquals("/api/v1/mcp/sources/1/mcp/message", registry.sourceMessagePath(1L));
    }

    @Test
    void extractSourceIdShouldAcceptPathWithLeadingSlash() {
        SourceScopedMcpServerRegistry registry = new SourceScopedMcpServerRegistry(
                mock(ApiSourceRepository.class),
                mock(DynamicApiToolCallbackProvider.class),
                new ObjectMapper(),
                mock(McpSessionService.class)
        );

        assertEquals(1L, registry.extractSourceId("/api/v1/mcp/sources/1/mcp/message"));
    }

    @Test
    void extractSourceIdShouldAcceptPathWithoutLeadingSlash() {
        SourceScopedMcpServerRegistry registry = new SourceScopedMcpServerRegistry(
                mock(ApiSourceRepository.class),
                mock(DynamicApiToolCallbackProvider.class),
                new ObjectMapper(),
                mock(McpSessionService.class)
        );

        assertEquals(1L, registry.extractSourceId("api/v1/mcp/sources/1/mcp/message"));
    }

    @Test
    void extractSourceIdShouldRejectNonSourcePath() {
        SourceScopedMcpServerRegistry registry = new SourceScopedMcpServerRegistry(
                mock(ApiSourceRepository.class),
                mock(DynamicApiToolCallbackProvider.class),
                new ObjectMapper(),
                mock(McpSessionService.class)
        );

        assertNull(registry.extractSourceId("/favicon.ico"));
    }

    @Test
    void refreshSourceShouldKeepPreviousServerWhenReplacementCreationFails() {
        ApiSourceRepository apiSourceRepository = mock(ApiSourceRepository.class);
        DynamicApiToolCallbackProvider toolCallbackProvider = mock(DynamicApiToolCallbackProvider.class);
        when(toolCallbackProvider.getToolCallbacksForSource(1L))
                .thenReturn(new ToolCallback[0])
                .thenThrow(new IllegalStateException("broken tool config"));

        SourceScopedMcpServerRegistry registry = new SourceScopedMcpServerRegistry(
                apiSourceRepository,
                toolCallbackProvider,
                new ObjectMapper(),
                mock(McpSessionService.class)
        );
        configureRegistry(registry);
        ApiSource source = ApiSource.builder().id(1L).name("demo").active(true).build();

        registry.refreshSource(source);
        Object previousRegistration = registeredServers(registry).get(1L);

        try {
            registry.refreshSource(source);
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("Failed to create MCP server for source: 1"));
        }

        assertSame(previousRegistration, registeredServers(registry).get(1L));
    }

    @Test
    void refreshAllShouldKeepHealthyExistingServerWhenOneSourceFailsRefresh() {
        ApiSourceRepository apiSourceRepository = mock(ApiSourceRepository.class);
        DynamicApiToolCallbackProvider toolCallbackProvider = mock(DynamicApiToolCallbackProvider.class);
        McpSessionService sessionService = mock(McpSessionService.class);
        ApiSource healthySource = ApiSource.builder().id(1L).name("healthy").active(true).build();
        ApiSource brokenSource = ApiSource.builder().id(2L).name("broken").active(true).build();
        when(toolCallbackProvider.getToolCallbacksForSource(1L)).thenReturn(new ToolCallback[0]);
        when(toolCallbackProvider.getToolCallbacksForSource(2L)).thenThrow(new IllegalStateException("broken tool config"));
        when(apiSourceRepository.findByActive(true)).thenReturn(Flux.just(healthySource, brokenSource));
        when(apiSourceRepository.findById(2L)).thenReturn(Mono.just(brokenSource));

        SourceScopedMcpServerRegistry registry = new SourceScopedMcpServerRegistry(
                apiSourceRepository,
                toolCallbackProvider,
                new ObjectMapper(),
                sessionService
        );
        configureRegistry(registry);

        registry.refreshSource(healthySource);
        registry.refreshAll();

        assertNotNull(registeredServers(registry).get(1L));
        assertNull(registeredServers(registry).get(2L));
        verify(sessionService, never()).evictSource(1L);
    }

    private static void configureRegistry(SourceScopedMcpServerRegistry registry) {
        setField(registry, "serverName", "flux-mcp");
        setField(registry, "serverVersion", "2.0.0");
        setField(registry, "requestTimeout", Duration.ofSeconds(30));
        setField(registry, "messageEndpoint", "/mcp/message");
        setField(registry, "keepAliveInterval", Duration.ofSeconds(15));
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentMap<Long, Object> registeredServers(SourceScopedMcpServerRegistry registry) {
        try {
            Field field = SourceScopedMcpServerRegistry.class.getDeclaredField("servers");
            field.setAccessible(true);
            return (ConcurrentMap<Long, Object>) field.get(registry);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to access servers field", ex);
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = SourceScopedMcpServerRegistry.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to set field: " + fieldName, ex);
        }
    }
}