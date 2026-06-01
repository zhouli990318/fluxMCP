package com.silver.ai.fluxmcp.infrastructure.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silver.ai.fluxmcp.domain.model.ApiSource;
import com.silver.ai.fluxmcp.domain.model.ToolMapping;
import com.silver.ai.fluxmcp.domain.port.ApiSourceRepository;
import com.silver.ai.fluxmcp.domain.port.ToolMappingRepository;
import com.silver.ai.fluxmcp.domain.service.ToolInvocationDomainService;
import com.silver.ai.fluxmcp.common.exception.BusinessException;
import com.silver.ai.fluxmcp.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@DependsOnDatabaseInitialization
@RequiredArgsConstructor
public class DynamicApiToolCallbackProvider implements ToolCallbackProvider {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

        private final ApiSourceRepository apiSourceRepository;
        private final ToolMappingRepository toolMappingRepository;
        private final ToolInvocationDomainService toolInvocationDomainService;
    private final ObjectMapper objectMapper;

    @Override
    public ToolCallback[] getToolCallbacks() {
        warnIfOnReactorThread("getToolCallbacks");
        Set<String> usedNames = new java.util.HashSet<>();

        List<ToolCallback> callbacks = apiSourceRepository.findByActive(true).collectList().blockOptional().orElse(java.util.List.of()).stream()
            .flatMap(source -> getEnabledToolMappings(source.getId()).stream()
                .map(mapping -> createCallback(source, mapping, usedNames, true)))
                .collect(Collectors.toList());

        return callbacks.toArray(ToolCallback[]::new);
    }

        public ToolCallback[] getToolCallbacksForSource(Long sourceId) {
        warnIfOnReactorThread("getToolCallbacksForSource");
        ApiSource source = apiSourceRepository.findById(sourceId)
            .filter(ApiSource::isActive)
            .blockOptional()
            .orElseThrow(() -> new BusinessException(ErrorCode.MCP_SOURCE_NOT_FOUND));

        Set<String> usedNames = new java.util.HashSet<>();
        return getEnabledToolMappings(sourceId).stream()
            .map(mapping -> createCallback(source, mapping, usedNames, false))
            .toArray(ToolCallback[]::new);
        }

        private ToolCallback createCallback(ApiSource source, ToolMapping mapping, Set<String> usedNames,
                        boolean includeSourcePrefixOnConflict) {
        String toolName = resolveToolName(source, mapping, usedNames, includeSourcePrefixOnConflict);

        return FunctionToolCallback.<Map<String, Object>, String>builder(
                        toolName,
                        (arguments, toolContext) -> invokeTool(mapping.getId(), arguments, toolContext))
                .description(resolveDescription(source, mapping))
                .inputSchema(normalizeSchema(mapping.getParameterSchema()))
                .inputType(MAP_TYPE)
                .build();
    }

    private String invokeTool(Long toolId, Map<String, Object> arguments, ToolContext toolContext) {
        warnIfOnReactorThread("invokeTool");
        ToolMapping mapping = toolMappingRepository.findById(toolId)
            .blockOptional()
            .orElseThrow(() -> new BusinessException(ErrorCode.MCP_TOOL_NOT_FOUND));
        ApiSource source = apiSourceRepository.findById(mapping.getApiSourceId())
            .blockOptional()
            .orElseThrow(() -> new BusinessException(ErrorCode.MCP_SOURCE_NOT_FOUND));

        Map<String, Object> payload = new LinkedHashMap<>();
        if (arguments != null) {
            payload.putAll(arguments);
        }
        if (toolContext != null && toolContext.getContext() != null && !toolContext.getContext().isEmpty()) {
            payload.put("_toolContext", toolContext.getContext());
        }

        try {
            return toolInvocationDomainService.invoke(source, mapping, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("MCP 工具参数序列化失败", ex);
        }
    }

    private List<ToolMapping> getEnabledToolMappings(Long sourceId) {
        return toolMappingRepository.findByApiSourceId(sourceId)
                .filter(ToolMapping::isEnabled)
                .collectList()
                .blockOptional()
                .orElse(java.util.List.of());
    }

    private String resolveToolName(ApiSource source, ToolMapping mapping, Set<String> usedNames,
                                   boolean includeSourcePrefixOnConflict) {
        String baseName = sanitizeName(mapping.getToolName());
        if (usedNames.add(baseName)) {
            return baseName;
        }

        if (includeSourcePrefixOnConflict) {
            String sourceScopedName = sanitizeName(source.getName()) + "__" + baseName;
            if (usedNames.add(sourceScopedName)) {
                return sourceScopedName;
            }

            String idScopedName = sourceScopedName + "_" + mapping.getId();
            usedNames.add(idScopedName);
            return idScopedName;
        }

        String idScopedName = baseName + "_" + mapping.getId();
        usedNames.add(idScopedName);
        return idScopedName;
    }

    private String resolveDescription(ApiSource source, ToolMapping mapping) {
        String description = mapping.getToolDescription();
        if (description == null || description.isBlank()) {
            description = "Invoke " + mapping.getToolName();
        }
        return "[" + source.getName() + "] " + description;
    }

    private String normalizeSchema(String parameterSchema) {
        if (parameterSchema == null || parameterSchema.isBlank()) {
            return "{\"type\":\"object\",\"properties\":{}}";
        }
        return parameterSchema;
    }

    private String sanitizeName(String value) {
        String normalized = value == null ? "tool" : value.trim();
        if (normalized.isEmpty()) {
            normalized = "tool";
        }
        return normalized.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private void warnIfOnReactorThread(String methodName) {
        if (Schedulers.isInNonBlockingThread()) {
            log.warn("Blocking call in {} detected on Reactor non-blocking thread [{}]. " +
                    "This may degrade performance.", methodName, Thread.currentThread().getName());
        }
    }
}