package com.silver.ai.fluxmcp.interfaces.rest;

import com.silver.ai.fluxmcp.application.FluxMcpAppService;
import com.silver.ai.fluxmcp.common.result.ApiResponse;
import com.silver.ai.fluxmcp.domain.model.ApiSource;
import com.silver.ai.fluxmcp.infrastructure.health.McpHealthCheckScheduler;
import com.silver.ai.fluxmcp.infrastructure.mcp.SourceScopedMcpServerRegistry;
import com.silver.ai.fluxmcp.interfaces.dto.ApiSourceResponse;
import com.silver.ai.fluxmcp.interfaces.dto.CreateApiSourceRequest;
import com.silver.ai.fluxmcp.interfaces.dto.ParseOpenApiRequest;
import com.silver.ai.fluxmcp.interfaces.dto.SourceHealthDto;
import com.silver.ai.fluxmcp.interfaces.dto.ToolInvokeRequest;
import com.silver.ai.fluxmcp.interfaces.dto.ToolMappingResponse;
import com.silver.ai.fluxmcp.interfaces.dto.ToolMappingUpdateRequest;
import com.silver.ai.fluxmcp.interfaces.dto.UpdateApiSourceRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/mcp")
@RequiredArgsConstructor
public class FluxMcpController {

    private final FluxMcpAppService mcpService;
    private final SourceScopedMcpServerRegistry sourceScopedMcpServerRegistry;
    private final McpHealthCheckScheduler healthCheckScheduler;

    @Value("${spring.ai.mcp.server.name:flux-mcp}")
    private String serverName;

    @Value("${spring.ai.mcp.server.version:unknown}")
    private String serverVersion;

    // ===== API Sources =====

    @GetMapping("/sources")
    public Mono<ApiResponse<List<ApiSourceResponse>>> listSources() {
        return mcpService.listApiSources()
                .collectList()
                .map(sources -> ApiResponse.ok(ApiSourceResponse.fromList(sources)));
    }

    @PostMapping("/sources")
    public Mono<ApiResponse<ApiSourceResponse>> createSource(@Valid @RequestBody CreateApiSourceRequest req) {
        return mcpService.createApiSource(
                        req.getName(), req.getDescription(), req.getBaseUrl(),
                        req.getAuthType(), req.getAuthConfig(), req.getOpenApiSpec())
                .flatMap(source -> refreshRegistrySafely(source).thenReturn(ApiResponse.ok(ApiSourceResponse.from(source))));
    }

    @GetMapping("/sources/{id}")
    public Mono<ApiResponse<ApiSourceResponse>> getSource(@PathVariable Long id) {
        return mcpService.getApiSource(id)
                .map(source -> ApiResponse.ok(ApiSourceResponse.from(source)));
    }

    @GetMapping("/connection-info")
    public Mono<ApiResponse<Map<String, String>>> getConnectionInfo(ServerHttpRequest request) {
        return Mono.just(ApiResponse.ok(Map.of(
                "serverName", serverName,
                "version", serverVersion,
                "streamableHttpUrl", buildBaseUrl(request) + sourceScopedMcpServerRegistry.sourceMessagePathTemplate()
        )));
    }

    @GetMapping("/sources/{id}/connection-info")
    public Mono<ApiResponse<Map<String, String>>> getSourceConnectionInfo(@PathVariable Long id, ServerHttpRequest request) {
        return mcpService.getApiSource(id)
                .map(source -> {
                    String baseUrl = buildBaseUrl(request);
                    return ApiResponse.ok(Map.of(
                            "serverName", sourceScopedMcpServerRegistry.serverNameFor(source),
                            "version", serverVersion,
                            "streamableHttpUrl", baseUrl + sourceScopedMcpServerRegistry.sourceMessagePath(id)
                    ));
                });
    }

    @PutMapping("/sources/{id}")
    public Mono<ApiResponse<ApiSourceResponse>> updateSource(@PathVariable Long id, @Valid @RequestBody UpdateApiSourceRequest req) {
        return mcpService.updateApiSource(id,
                        req.getName(), req.getDescription(), req.getBaseUrl(),
                        req.getAuthType(), req.getAuthConfig())
                .flatMap(source -> refreshRegistrySafely(source).thenReturn(ApiResponse.ok(ApiSourceResponse.from(source))));
    }

    @DeleteMapping("/sources/{id}")
    public Mono<ApiResponse<Void>> deleteSource(@PathVariable Long id) {
        return mcpService.deleteApiSource(id)
                .then(removeFromRegistrySafely(id))
                .then(Mono.fromCallable(ApiResponse::ok));
    }

    // ===== Active Toggle =====

    @PatchMapping("/sources/{id}/toggle-active")
    public Mono<ApiResponse<ApiSourceResponse>> toggleActive(@PathVariable Long id) {
        return mcpService.toggleApiSourceActive(id)
                .flatMap(source -> {
                    Mono<Void> refreshTask = source.isActive()
                            ? refreshRegistrySafely(source)
                            : removeFromRegistrySafely(id);
                    return refreshTask.thenReturn(ApiResponse.ok(ApiSourceResponse.from(source)));
                });
    }

    // ===== Health =====

    @GetMapping("/sources/health")
    public Mono<ApiResponse<List<SourceHealthDto>>> listSourcesHealth() {
        return mcpService.listApiSources()
                .map(SourceHealthDto::from)
                .collectList()
                .map(ApiResponse::ok);
    }

    @PostMapping("/sources/{id}/health-check")
    public Mono<ApiResponse<SourceHealthDto>> triggerHealthCheck(@PathVariable Long id) {
        return healthCheckScheduler.probeOnce(id)
                .map(SourceHealthDto::from)
                .map(ApiResponse::ok);
    }

    // ===== Parse =====

    @PostMapping("/sources/{id}/parse")
    public Mono<ApiResponse<List<ToolMappingResponse>>> parseSpec(@PathVariable Long id, @RequestBody ParseOpenApiRequest req) {
        Mono<List<ToolMappingResponse>> tools;
        String content = req.requireContent();
        if (req.isUrlSource()) {
            tools = mcpService.parseFromUrl(id, content)
                    .map(ToolMappingResponse::fromList);
        } else {
            tools = mcpService.parseOpenApiSpec(id, content)
                    .map(ToolMappingResponse::fromList);
        }
        return tools
                .flatMap(result ->
                        mcpService.getApiSource(id)
                                .flatMap(source -> refreshRegistrySafely(source,
                                        "Failed to refresh MCP server registry for source {} after parse", id))
                                .thenReturn(ApiResponse.ok(result))
                );
    }

    // ===== Tool Mappings =====

    @GetMapping("/sources/{id}/tools")
    public Mono<ApiResponse<List<ToolMappingResponse>>> getTools(@PathVariable Long id) {
        return mcpService.getToolMappings(id)
                .collectList()
                .map(mappings -> ApiResponse.ok(ToolMappingResponse.fromList(mappings)));
    }

    @PutMapping("/tools/{id}")
    public Mono<ApiResponse<ToolMappingResponse>> updateTool(@PathVariable Long id, @RequestBody ToolMappingUpdateRequest req) {
        return mcpService.updateToolMapping(id,
                        req.getToolName(), req.getToolDescription(),
                        req.getHttpMethod(), req.getPath(),
                        req.getParameterSchema(), req.getResponseSchema(), req.getExamplePayload(),
                        req.getEnabled())
                .flatMap(mapping ->
                        mcpService.getApiSource(mapping.getApiSourceId())
                    .flatMap(this::refreshRegistrySafely)
                                .thenReturn(ApiResponse.ok(ToolMappingResponse.from(mapping)))
                );
    }

    @PostMapping("/tools/{id}/test")
    public Mono<ApiResponse<String>> testTool(@PathVariable Long id, @RequestBody ToolInvokeRequest req) {
        return mcpService.invokeTool(id, req.getArguments())
                .map(ApiResponse::ok);
    }

    private Mono<Void> refreshRegistrySafely(ApiSource source) {
        return refreshRegistrySafely(source,
                "Failed to refresh MCP server registry for source {}, will self-heal on next request",
                source.getId());
    }

    private Mono<Void> refreshRegistrySafely(ApiSource source, String message, Long sourceId) {
        return sourceScopedMcpServerRegistry.refreshSourceAsync(source)
                .onErrorResume(ex -> {
                    log.warn(message, sourceId, ex);
                    return Mono.empty();
                });
    }

    private Mono<Void> removeFromRegistrySafely(Long sourceId) {
        return sourceScopedMcpServerRegistry.removeSourceAsync(sourceId)
                .onErrorResume(ex -> {
                    log.warn("Failed to remove source {} from MCP server registry", sourceId, ex);
                    return Mono.empty();
                });
    }

    private String buildBaseUrl(ServerHttpRequest request) {
        String scheme = request.getURI().getScheme();
        String host = request.getURI().getHost();
        int port = request.getURI().getPort();
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        return scheme + "://" + host + (defaultPort || port < 0 ? "" : ":" + port);
    }
}
