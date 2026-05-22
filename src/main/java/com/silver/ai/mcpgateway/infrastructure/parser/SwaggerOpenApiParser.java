package com.silver.ai.mcpgateway.infrastructure.parser;

import com.silver.ai.mcpgateway.domain.model.ToolMapping;
import com.silver.ai.mcpgateway.domain.port.OpenApiParserPort;
import com.silver.ai.mcpgateway.common.exception.BusinessException;
import com.silver.ai.mcpgateway.common.result.ErrorCode;
import com.silver.ai.mcpgateway.domain.service.UrlNormalizer;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.net.URI;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.converter.SwaggerConverter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class SwaggerOpenApiParser implements OpenApiParserPort {

    private static final Pattern SWAGGER_2_PATTERN = Pattern.compile("(?is)\\bswagger\\s*[:=]\\s*[\"']?2\\.0[\"']?");
    private static final Pattern OPENAPI_3_PATTERN = Pattern.compile("(?is)\\bopenapi\\s*[:=]\\s*[\"']?3(?:\\.[0-9]+)*[\"']?");

    private final ObjectMapper objectMapper;

    @Override
    public List<ToolMapping> parse(String openApiSpec, Long apiSourceId) {
        try {
            SwaggerParseResult result = readContents(openApiSpec);
            return extractToolMappings(requireOpenApi(result, openApiSpec), apiSourceId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse OpenAPI spec", e);
            throw new BusinessException(ErrorCode.MCP_PARSE_FAILED, e.getMessage(), e);
        }
    }

    @Override
    public List<ToolMapping> parseFromUrl(String url, Long apiSourceId) {
        try {
            UrlNormalizer.validateNotInternal(URI.create(url), ErrorCode.MCP_PARSE_FAILED);
            SwaggerParseResult result = readLocation(url);
            return extractToolMappings(requireOpenApi(result, url), apiSourceId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.MCP_PARSE_FAILED, e.getMessage(), e);
        }
    }

    private List<ToolMapping> extractToolMappings(OpenAPI openAPI, Long apiSourceId) {
        List<ToolMapping> mappings = new ArrayList<>();

        if (openAPI.getPaths() == null) return mappings;

        for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
            String path = pathEntry.getKey();
            PathItem pathItem = pathEntry.getValue();

            extractOperation(apiSourceId, path, "GET", pathItem.getGet(), mappings);
            extractOperation(apiSourceId, path, "POST", pathItem.getPost(), mappings);
            extractOperation(apiSourceId, path, "PUT", pathItem.getPut(), mappings);
            extractOperation(apiSourceId, path, "DELETE", pathItem.getDelete(), mappings);
            extractOperation(apiSourceId, path, "PATCH", pathItem.getPatch(), mappings);
        }

        log.info("Parsed {} tool mappings from OpenAPI spec", mappings.size());
        return mappings;
    }

    private void extractOperation(Long apiSourceId, String path, String method,
                                   Operation operation, List<ToolMapping> mappings) {
        if (operation == null) return;

        String operationId = operation.getOperationId();
        if (operationId == null || operationId.isBlank()) {
            operationId = method.toLowerCase() + path.replaceAll("[^a-zA-Z0-9]", "_");
        }

        String description = operation.getSummary();
        if (description == null) description = operation.getDescription();
        if (description == null) description = operationId;

        String paramSchema = buildParameterSchema(operation);

        ToolMapping mapping = ToolMapping.builder()
                .apiSourceId(apiSourceId)
                .operationId(operationId)
                .toolName(sanitizeToolName(operationId))
                .toolDescription(description)
                .httpMethod(method)
                .path(path)
                .parameterSchema(paramSchema)
                .enabled(true)
                .build();

        mappings.add(mapping);
    }

    private String buildParameterSchema(Operation operation) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        Set<String> required = new LinkedHashSet<>();

        // Path / Query / Header 参数
        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("type", getSchemaType(param.getSchema()));
                prop.put("description", param.getDescription() != null ? param.getDescription() : param.getName());
                prop.put("in", param.getIn());
                properties.put(param.getName(), prop);
                if (Boolean.TRUE.equals(param.getRequired())) {
                    required.add(param.getName());
                }
            }
        }

        appendRequestBodySchema(operation, properties, required);

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", new ArrayList<>(required));
        }

        try {
            return objectMapper.writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private ParseOptions buildParseOptions() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);
        // Note: resolveFully also follows remote $ref URLs inside specs.
        // SSRF for the top-level URL is mitigated by UrlNormalizer.validateNotInternal().
        // Remote $ref inside inline specs requires a custom resolver for full mitigation.
        return options;
    }

    private SwaggerParseResult readContents(String openApiSpec) {
        ParseOptions options = buildParseOptions();
        if (SWAGGER_2_PATTERN.matcher(openApiSpec).find()) {
            return new SwaggerConverter().readContents(openApiSpec, null, options);
        }

        SwaggerParseResult result = new OpenAPIV3Parser().readContents(openApiSpec, null, options);
        if (shouldFallbackToSwagger2(result)) {
            return new SwaggerConverter().readContents(openApiSpec, null, options);
        }
        return result;
    }

    private SwaggerParseResult readLocation(String url) {
        ParseOptions options = buildParseOptions();
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(url, null, options);
        if (shouldFallbackToSwagger2(result)) {
            return new SwaggerConverter().readLocation(url, null, options);
        }
        return result;
    }

    private boolean shouldFallbackToSwagger2(SwaggerParseResult result) {
        if (result.getOpenAPI() != null) {
            return false;
        }
        if (result.getMessages() == null || result.getMessages().isEmpty()) {
            return false;
        }
        return result.getMessages().stream()
                .anyMatch(message -> message != null && message.toLowerCase(Locale.ROOT).contains("attribute openapi is missing"));
    }

    private OpenAPI requireOpenApi(SwaggerParseResult result, String rawSource) {
        if (result.getOpenAPI() != null) {
            return result.getOpenAPI();
        }

        String errors = result.getMessages() != null && !result.getMessages().isEmpty()
                ? String.join("; ", result.getMessages())
                : "Unknown error";
        String specType = detectSpecType(rawSource);
        if (!"unknown".equals(specType)) {
            errors = specType + " 规范解析失败: " + errors;
        }
        throw new BusinessException(ErrorCode.MCP_PARSE_FAILED, errors);
    }

    private String detectSpecType(String rawSource) {
        if (rawSource == null || rawSource.isBlank()) {
            return "unknown";
        }
        if (SWAGGER_2_PATTERN.matcher(rawSource).find()) {
            return "Swagger 2.0";
        }
        if (OPENAPI_3_PATTERN.matcher(rawSource).find()) {
            return "OpenAPI 3.x";
        }
        return "unknown";
    }

    private void appendRequestBodySchema(Operation operation, Map<String, Object> properties, Set<String> required) {
        if (operation.getRequestBody() == null || operation.getRequestBody().getContent() == null) {
            return;
        }

        Schema<?> bodySchema = selectRequestBodySchema(operation.getRequestBody().getContent());
        if (bodySchema == null) {
            return;
        }

        if (bodySchema.getProperties() != null && !bodySchema.getProperties().isEmpty()) {
            appendSchemaProperties(bodySchema, properties, required);
            return;
        }

        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", getSchemaType(bodySchema));
        prop.put("description", operation.getRequestBody().getDescription() != null
                ? operation.getRequestBody().getDescription() : "requestBody");
        prop.put("in", "body");
        properties.put("body", prop);
        if (Boolean.TRUE.equals(operation.getRequestBody().getRequired())) {
            required.add("body");
        }
    }

    private Schema<?> selectRequestBodySchema(Map<String, MediaType> content) {
        List<String> preferredContentTypes = List.of(
                "application/json",
                "application/x-www-form-urlencoded",
                "multipart/form-data"
        );

        for (String contentType : preferredContentTypes) {
            MediaType mediaType = content.get(contentType);
            if (mediaType != null && mediaType.getSchema() != null) {
                return mediaType.getSchema();
            }
        }

        return content.values().stream()
                .map(MediaType::getSchema)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private void appendSchemaProperties(Schema<?> bodySchema, Map<String, Object> properties, Set<String> required) {
        for (Map.Entry<String, ?> entry : bodySchema.getProperties().entrySet()) {
            if (!(entry.getValue() instanceof Schema<?> propertySchema)) {
                continue;
            }
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", getSchemaType(propertySchema));
            prop.put("description", propertySchema.getDescription() != null
                    ? propertySchema.getDescription() : entry.getKey());
            prop.put("in", "body");
            properties.put(entry.getKey(), prop);
        }
        if (bodySchema.getRequired() != null) {
            required.addAll(bodySchema.getRequired());
        }
    }

    private String getSchemaType(Schema<?> schema) {
        if (schema == null) return "string";
        if (schema.getType() != null) {
            return schema.getType();
        }
        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            return "object";
        }
        if (schema.getItems() != null) {
            return "array";
        }
        return "string";
    }

    private String sanitizeToolName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }
}
