package com.silver.ai.fluxmcp.application;

import com.silver.ai.fluxmcp.domain.model.ApiSource;
import com.silver.ai.fluxmcp.domain.model.AuthType;
import com.silver.ai.fluxmcp.domain.model.ToolMapping;
import com.silver.ai.fluxmcp.domain.port.ApiSourceRepository;
import com.silver.ai.fluxmcp.domain.port.OpenApiParserPort;
import com.silver.ai.fluxmcp.domain.port.ToolMappingRepository;
import com.silver.ai.fluxmcp.domain.service.ToolInvocationDomainService;
import com.silver.ai.fluxmcp.domain.service.UrlNormalizer;
import com.silver.ai.fluxmcp.common.exception.BusinessException;
import com.silver.ai.fluxmcp.common.result.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FluxMcpAppService {

    private final ApiSourceRepository apiSourceRepository;
    private final ToolMappingRepository toolMappingRepository;
    private final OpenApiParserPort openApiParser;
    private final ToolInvocationDomainService toolInvocationService;
    private final ObjectMapper objectMapper;

    private static final int MAX_SPEC_SIZE = 5_000_000;

    // ===== API Source =====

    @Transactional
    public Mono<ApiSource> createApiSource(String name, String description, String baseUrl,
                                           AuthType authType, String authConfig, String openApiSpec) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        validateAuthConfig(authType, authConfig);
        validateSpecSize(openApiSpec);
        ApiSource source = ApiSource.builder()
                .name(name)
                .description(description)
                .baseUrl(normalizedBaseUrl)
                .authType(authType)
                .authConfig(authConfig)
                .openApiSpec(openApiSpec)
                .build();
        return apiSourceRepository.save(source)
                .flatMap(saved -> {
                    if (openApiSpec != null && !openApiSpec.isBlank()) {
                        return parseAndSaveTools(saved.getId(), openApiSpec)
                                .then(Mono.just(saved));
                    }
                    return Mono.just(saved);
                });
    }

    public Mono<ApiSource> updateApiSource(Long id, String name, String description, String baseUrl,
                                            AuthType authType, String authConfig) {
        validateAuthConfig(authType, authConfig);
        return getApiSource(id)
                .flatMap(source -> {
                    source.updateInfo(name, description, normalizeBaseUrl(baseUrl), authType, authConfig);
                    return apiSourceRepository.save(source);
                });
    }

    public Mono<ApiSource> getApiSource(Long id) {
        return apiSourceRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.MCP_SOURCE_NOT_FOUND)))
                .flatMap(source ->
                        toolMappingRepository.findByApiSourceId(id)
                                .collectList()
                                .map(tools -> {
                                    source.setToolMappings(tools);
                                    return source;
                                })
                );
    }

    public Flux<ApiSource> listApiSources() {
        return apiSourceRepository.findAll();
    }

    /**
     * 切换源的 active 状态。
     */
    public Mono<ApiSource> toggleApiSourceActive(Long id) {
        return apiSourceRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.MCP_SOURCE_NOT_FOUND)))
                .flatMap(source -> {
                    if (source.isActive()) {
                        source.deactivate();
                    } else {
                        source.activate();
                    }
                    return apiSourceRepository.save(source);
                });
    }

    @Transactional
    public Mono<Void> deleteApiSource(Long id) {
        return toolMappingRepository.deleteByApiSourceId(id)
                .then(apiSourceRepository.deleteById(id));
    }

    // ===== OpenAPI Parsing =====

    @Transactional
    public Mono<List<ToolMapping>> parseOpenApiSpec(Long apiSourceId, String openApiSpec) {
        validateSpecSize(openApiSpec);
        return apiSourceRepository.findById(apiSourceId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.MCP_SOURCE_NOT_FOUND)))
                .flatMap(source -> {
                    source.updateSpec(openApiSpec);
                    return apiSourceRepository.save(source);
                })
                .then(toolMappingRepository.deleteByApiSourceId(apiSourceId))
                .then(parseAndSaveTools(apiSourceId, openApiSpec).collectList());
    }

    @Transactional
    public Mono<List<ToolMapping>> parseFromUrl(Long apiSourceId, String url) {
        return apiSourceRepository.findById(apiSourceId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.MCP_SOURCE_NOT_FOUND)))
                .flatMap(source ->
                        Mono.fromCallable(() -> openApiParser.parseFromUrl(url, apiSourceId))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(mappings -> {
                                    source.updateSpec(url);
                                    return apiSourceRepository.save(source)
                                            .then(toolMappingRepository.deleteByApiSourceId(apiSourceId))
                                            .thenMany(Flux.fromIterable(mappings)
                                                    .flatMap(toolMappingRepository::save))
                                            .collectList();
                                })
                );
    }

    // ===== Tool Mappings =====

    public Flux<ToolMapping> getToolMappings(Long apiSourceId) {
        return toolMappingRepository.findByApiSourceId(apiSourceId);
    }

    public Flux<ToolMapping> getAllEnabledTools() {
        return toolMappingRepository.findByEnabled(true);
    }

    public Mono<ToolMapping> updateToolMapping(Long toolId, String toolName, String toolDescription,
                                                String httpMethod, String path,
                                                String parameterSchema, String responseSchema,
                                                String examplePayload,
                                                Boolean enabled) {
        return toolMappingRepository.findById(toolId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.MCP_TOOL_NOT_FOUND)))
                .flatMap(mapping -> {
                    mapping.updateConfig(toolName, toolDescription, httpMethod, path,
                            parameterSchema, responseSchema, examplePayload);
                    if (enabled != null) {
                        if (enabled) mapping.enable(); else mapping.disable();
                    }
                    return toolMappingRepository.save(mapping);
                });
    }

    // ===== Tool Invocation =====

    public Mono<String> invokeTool(Long toolId, String arguments) {
        return toolMappingRepository.findById(toolId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.MCP_TOOL_NOT_FOUND)))
                .flatMap(mapping ->
                        apiSourceRepository.findById(mapping.getApiSourceId())
                                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.MCP_SOURCE_NOT_FOUND)))
                                .flatMap(source ->
                                        Mono.fromCallable(() -> toolInvocationService.invoke(source, mapping, arguments))
                                                .subscribeOn(Schedulers.boundedElastic())
                                )
                );
    }

    private Flux<ToolMapping> parseAndSaveTools(Long apiSourceId, String openApiSpec) {
        List<ToolMapping> mappings = openApiParser.parse(openApiSpec, apiSourceId);
        return Flux.fromIterable(mappings)
                .flatMap(toolMappingRepository::save);
    }

    private String normalizeBaseUrl(String baseUrl) {
        return UrlNormalizer.normalizeBaseUrl(baseUrl, ErrorCode.INVALID_PARAMETER);
    }

    private void validateSpecSize(String openApiSpec) {
        if (openApiSpec != null && openApiSpec.length() > MAX_SPEC_SIZE) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "OpenAPI spec 大小超过限制 (" + MAX_SPEC_SIZE / 1_000_000 + "MB)");
        }
    }

    private void validateAuthConfig(AuthType authType, String authConfig) {
        if (authType == null || authType == AuthType.NONE || authConfig == null || authConfig.isBlank()) {
            return;
        }
        try {
            objectMapper.readTree(authConfig);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "authConfig 不是合法的 JSON");
        }
    }
}
