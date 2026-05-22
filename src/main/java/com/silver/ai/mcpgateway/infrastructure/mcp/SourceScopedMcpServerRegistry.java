package com.silver.ai.mcpgateway.infrastructure.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silver.ai.mcpgateway.domain.model.ApiSource;
import com.silver.ai.mcpgateway.domain.model.McpAgentSession;
import com.silver.ai.mcpgateway.domain.port.ApiSourceRepository;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebFluxStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.event.EventListener;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.StampedLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@DependsOnDatabaseInitialization
@RequiredArgsConstructor
public class SourceScopedMcpServerRegistry {

    private static final Pattern SOURCE_PATH_PATTERN = Pattern.compile("^/?api/v1/mcp/sources/(\\d+)(?:/.*)?$");
    private static final String SOURCE_ID_PLACEHOLDER = "{sourceId}";
    private static final String DEFAULT_INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

    private final ApiSourceRepository apiSourceRepository;
    private final DynamicApiToolCallbackProvider toolCallbackProvider;
    private final ObjectMapper objectMapper;
    private final McpSessionService sessionService;

    @Value("${spring.ai.mcp.server.name:mcp-gateway}")
    private String serverName;

    @Value("${spring.ai.mcp.server.version:unknown}")
    private String serverVersion;

    @Value("${spring.ai.mcp.server.request-timeout:PT30S}")
    private Duration requestTimeout;

    @Value("${app.mcp.streamable-http-path:/mcp/message}")
    private String messageEndpoint;

    @Value("${spring.ai.mcp.server.sse.keep-alive-interval:PT15S}")
    private Duration keepAliveInterval;

    private final ConcurrentMap<Long, RegisteredSourceServer> servers = new ConcurrentHashMap<>();
    private final StampedLock stampedLock = new StampedLock();

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        log.info("Application ready, refreshing source-scoped MCP servers");
        refreshAll();
    }

    public void refreshAll() {
        warnIfOnReactorThread("refreshAll");

        // Query DB outside the lock to reduce lock holding time
        List<ApiSource> activeSources = apiSourceRepository.findByActive(true)
                .collectList().blockOptional().orElse(java.util.List.of());

        long stamp = stampedLock.writeLock();
        try {
            java.util.Set<Long> activeSourceIds = new java.util.HashSet<>();
            for (ApiSource source : activeSources) {
                if (source == null || source.getId() == null) {
                    continue;
                }
                activeSourceIds.add(source.getId());
                try {
                    refreshSourceInternal(source);
                } catch (RuntimeException ex) {
                    log.warn("Failed to refresh source-scoped MCP server for source {} during full refresh", source.getId(), ex);
                }
            }

            new java.util.ArrayList<>(servers.keySet()).stream()
                    .filter(sourceId -> !activeSourceIds.contains(sourceId))
                    .forEach(this::removeSourceInternal);
        } finally {
            stampedLock.unlockWrite(stamp);
        }
    }

    public void refreshSource(ApiSource source) {
        warnIfOnReactorThread("refreshSource");
        long stamp = stampedLock.writeLock();
        try {
            refreshSourceInternal(source);
        } finally {
            stampedLock.unlockWrite(stamp);
        }
    }

    public Mono<Void> refreshSourceAsync(ApiSource source) {
        return Mono.fromRunnable(() -> refreshSource(source))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public void removeSource(Long sourceId) {
        long stamp = stampedLock.writeLock();
        try {
            removeSourceInternal(sourceId);
        } finally {
            stampedLock.unlockWrite(stamp);
        }
    }

    public Mono<Void> removeSourceAsync(Long sourceId) {
        return Mono.fromRunnable(() -> removeSource(sourceId))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void refreshSourceInternal(ApiSource source) {
        if (source == null || source.getId() == null) {
            return;
        }

        if (!source.isActive()) {
            removeSourceInternal(source.getId());
            return;
        }

        RegisteredSourceServer replacement = createServer(source);
        RegisteredSourceServer previous = servers.put(source.getId(), replacement);
        if (previous != null) {
            closeServerQuietly(source.getId(), previous);
        }
        log.info("Registered source-scoped MCP connection for source {}", source.getId());
    }

    private void removeSourceInternal(Long sourceId) {
        if (sourceId == null) {
            return;
        }

        RegisteredSourceServer removed = servers.remove(sourceId);
        if (removed != null) {
            closeServerQuietly(sourceId, removed);
        }
        sessionService.evictSource(sourceId);
    }

    public Mono<HandlerFunction<ServerResponse>> route(ServerRequest request) {
        Long sourceId = extractSourceId(request.path());
        if (sourceId == null) {
            return Mono.empty();
        }

        // Optimistic read — never blocks the Reactor NIO thread
        long stamp = stampedLock.tryOptimisticRead();
        RegisteredSourceServer server = servers.get(sourceId);
        if (stampedLock.validate(stamp) && server != null) {
            maybeProvisionSession(sourceId, request);
            return server.routerFunction().route(request);
        }

        // Fallback: pessimistic read
        stamp = stampedLock.readLock();
        try {
            server = servers.get(sourceId);
            if (server != null) {
                maybeProvisionSession(sourceId, request);
                return server.routerFunction().route(request);
            }
        } finally {
            stampedLock.unlockRead(stamp);
        }

        // Lazy-init: acquire write lock on boundedElastic (blocking I/O)
        return Mono.defer(() -> {
            long ws = stampedLock.writeLock();
            try {
                // Double-check after acquiring write lock
                RegisteredSourceServer srv = servers.get(sourceId);
                if (srv == null) {
                    try {
                        apiSourceRepository.findById(sourceId)
                                .filter(ApiSource::isActive)
                                .blockOptional()
                                .ifPresent(this::refreshSourceInternal);
                    } catch (RuntimeException ex) {
                        log.warn("Failed to lazily refresh source-scoped MCP server for source {}", sourceId, ex);
                    }
                    srv = servers.get(sourceId);
                }
                if (srv == null) {
                    return Mono.empty();
                }

                maybeProvisionSession(sourceId, request);
                return srv.routerFunction().route(request);
            } finally {
                stampedLock.unlockWrite(ws);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public String serverNameFor(ApiSource source) {
        return serverName + "-source-" + source.getId();
    }

    public String sourceMessagePath(Long sourceId) {
        return buildSourcePath(String.valueOf(sourceId), normalizeEndpoint(messageEndpoint));
    }

    public String sourceMessagePathTemplate() {
        return buildSourcePath(SOURCE_ID_PLACEHOLDER, normalizeEndpoint(messageEndpoint));
    }

    private RegisteredSourceServer createServer(ApiSource source) {
        McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper.copy());
        String sourceMessagePath = buildSourcePath(String.valueOf(source.getId()), normalizeEndpoint(messageEndpoint));

        WebFluxStreamableServerTransportProvider transportProvider = WebFluxStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .messageEndpoint(sourceMessagePath)
                .keepAliveInterval(keepAliveInterval)
                .contextExtractor(request -> buildTransportContext(source.getId(), request))
                .build();

        try {
            List<McpServerFeatures.SyncToolSpecification> tools = Arrays.stream(
                            toolCallbackProvider.getToolCallbacksForSource(source.getId()))
                    .map(callback -> toSyncToolSpecification(source.getId(), callback, jsonMapper))
                    .toList();

            McpSyncServer server = McpServer.sync(transportProvider)
                    .serverInfo(serverNameFor(source), serverVersion)
                    .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                    .tools(tools)
                    .instructions(source.getDescription())
                    .requestTimeout(requestTimeout)
                    .build();

            @SuppressWarnings("unchecked")
            RouterFunction<ServerResponse> routerFunction = (RouterFunction<ServerResponse>) transportProvider.getRouterFunction();
            return new RegisteredSourceServer(routerFunction, server, transportProvider);
        } catch (Exception ex) {
            try {
                transportProvider.closeGracefully();
            } catch (Exception cleanupEx) {
                log.warn("Failed to cleanup transport provider for source {} after server creation failure",
                        source.getId(), cleanupEx);
            }
            throw new IllegalStateException("Failed to create MCP server for source: " + source.getId(), ex);
        }
    }

    private McpServerFeatures.SyncToolSpecification toSyncToolSpecification(Long sourceId, ToolCallback callback,
                                                                            McpJsonMapper jsonMapper) {
        ToolDefinition definition = callback.getToolDefinition();
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(definition.name())
                .description(definition.description())
                .inputSchema(jsonMapper, normalizeSchema(definition.inputSchema()))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
            .callHandler((exchange, request) -> invokeCallback(sourceId, definition.name(), callback, exchange, request.arguments()))
                .build();
    }

    private McpSchema.CallToolResult invokeCallback(Long sourceId, String toolName, ToolCallback callback,
                                                    io.modelcontextprotocol.server.McpSyncServerExchange exchange,
                                                    Map<String, Object> arguments) {
        try {
            McpAgentSession session = sessionService.recordToolCall(sourceId, exchange.sessionId(), toolName, arguments, exchange);
            Map<String, Object> payloadMap = new java.util.LinkedHashMap<>();
            if (arguments != null) {
                payloadMap.putAll(arguments);
            }
            payloadMap.put("_mcp", sessionService.buildInvocationMetadata(session));
            String payload = objectMapper.writeValueAsString(payloadMap);
                return McpSchema.CallToolResult.builder()
                    .content(java.util.List.of(new McpSchema.TextContent(callback.call(payload))))
                    .isError(false)
                    .build();
        } catch (JsonProcessingException ex) {
                return McpSchema.CallToolResult.builder()
                    .content(java.util.List.of(new McpSchema.TextContent("MCP 工具参数序列化失败")))
                    .isError(true)
                    .build();
        } catch (Exception ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank() ? "MCP 工具执行失败" : ex.getMessage();
                return McpSchema.CallToolResult.builder()
                    .content(java.util.List.of(new McpSchema.TextContent(message)))
                    .isError(true)
                    .build();
        }
    }

    Long extractSourceId(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Matcher matcher = SOURCE_PATH_PATTERN.matcher(path.trim());
        if (!matcher.matches()) {
            return null;
        }
        return Long.valueOf(matcher.group(1));
    }

    private String normalizeSchema(String inputSchema) {
        if (inputSchema == null || inputSchema.isBlank()) {
            return DEFAULT_INPUT_SCHEMA;
        }
        return inputSchema;
    }

    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "/";
        }
        return endpoint.startsWith("/") ? endpoint : "/" + endpoint;
    }

    private String buildSourceBasePath(String sourceKey) {
        return "/api/v1/mcp/sources/" + sourceKey;
    }

    private String buildSourcePath(String sourceKey, String endpoint) {
        return buildSourceBasePath(sourceKey) + endpoint;
    }

    private void maybeProvisionSession(Long sourceId, ServerRequest request) {
        List<String> sessionHeaders = request.headers().header(io.modelcontextprotocol.spec.HttpHeaders.MCP_SESSION_ID);
        if (!sessionHeaders.isEmpty()) {
            String sessionId = sessionHeaders.getFirst();
            if (sessionId != null && !sessionId.isBlank()) {
                sessionService.provisionSession(sourceId, sessionId, request);
            }
        }
    }

    private McpTransportContext buildTransportContext(Long sourceId, ServerRequest request) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put(McpTransportMetadataKeys.SOURCE_ID, sourceId);
        metadata.put(McpTransportMetadataKeys.REQUEST_METHOD, request.method().name());
        metadata.put(McpTransportMetadataKeys.REQUEST_PATH, request.path());
        List<String> sessionHeaders = request.headers().header(io.modelcontextprotocol.spec.HttpHeaders.MCP_SESSION_ID);
        if (!sessionHeaders.isEmpty()) {
            metadata.put(McpTransportMetadataKeys.SESSION_ID, sessionHeaders.getFirst());
        }
        metadata.put(McpTransportMetadataKeys.REQUEST_HEADERS, normalizeHeaders(request.headers().asHttpHeaders()));
        return McpTransportContext.create(metadata);
    }

    private Map<String, String> normalizeHeaders(HttpHeaders headers) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        headers.forEach((name, values) -> {
            if (values != null && !values.isEmpty()) {
                result.put(name, String.join(",", values));
            }
        });
        return result;
    }

    private void closeServerQuietly(Long sourceId, RegisteredSourceServer registeredSourceServer) {
        try {
            registeredSourceServer.server().closeGracefully();
        } catch (Exception ex) {
            log.warn("Failed to close MCP server for source {}", sourceId, ex);
        }
        if (registeredSourceServer.transportProvider() != null) {
            try {
                registeredSourceServer.transportProvider().closeGracefully();
            } catch (Exception ex) {
                log.warn("Failed to close transport provider for source {}", sourceId, ex);
            }
        }
    }

    private void warnIfOnReactorThread(String methodName) {
        if (Schedulers.isInNonBlockingThread()) {
            log.warn("Blocking call in {} detected on Reactor non-blocking thread [{}]. " +
                    "This may degrade performance.", methodName, Thread.currentThread().getName());
        }
    }

    private record RegisteredSourceServer(RouterFunction<ServerResponse> routerFunction, McpSyncServer server,
                                             WebFluxStreamableServerTransportProvider transportProvider) {
    }
}