package com.silver.ai.fluxmcp.infrastructure.persistence.adapter;

import com.silver.ai.fluxmcp.domain.model.ToolMapping;
import com.silver.ai.fluxmcp.domain.port.ToolMappingRepository;
import com.silver.ai.fluxmcp.infrastructure.persistence.entity.ToolMappingEntity;
import com.silver.ai.fluxmcp.infrastructure.persistence.r2dbc.R2dbcToolMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class ToolMappingRepositoryAdapter implements ToolMappingRepository {

    private final R2dbcToolMappingRepository r2dbc;

    @Override
    public Mono<ToolMapping> save(ToolMapping m) {
        return r2dbc.save(toEntity(m)).map(this::toDomain);
    }

    @Override
    public Mono<ToolMapping> findById(Long id) {
        return r2dbc.findById(id).map(this::toDomain);
    }

    @Override
    public Flux<ToolMapping> findByApiSourceId(Long apiSourceId) {
        return r2dbc.findByApiSourceId(apiSourceId).map(this::toDomain);
    }

    @Override
    public Flux<ToolMapping> findByEnabled(boolean enabled) {
        return r2dbc.findByEnabled(enabled).map(this::toDomain);
    }

    @Override
    public Mono<Void> deleteByApiSourceId(Long apiSourceId) {
        return r2dbc.deleteByApiSourceId(apiSourceId);
    }

    @Override
    public Mono<Void> deleteById(Long id) {
        return r2dbc.deleteById(id);
    }

    private ToolMappingEntity toEntity(ToolMapping d) {
        return ToolMappingEntity.builder()
                .id(d.getId())
                .apiSourceId(d.getApiSourceId())
                .operationId(d.getOperationId())
                .toolName(d.getToolName())
                .toolDescription(d.getToolDescription())
                .httpMethod(d.getHttpMethod())
                .path(d.getPath())
                .parameterSchema(d.getParameterSchema())
                .responseSchema(d.getResponseSchema())
                .examplePayload(d.getExamplePayload())
                .enabled(d.isEnabled())
                .createdAt(d.getCreatedAt() != null ? d.getCreatedAt() : LocalDateTime.now())
                .build();
    }

    private ToolMapping toDomain(ToolMappingEntity e) {
        return ToolMapping.builder()
                .id(e.getId())
                .apiSourceId(e.getApiSourceId())
                .operationId(e.getOperationId())
                .toolName(e.getToolName())
                .toolDescription(e.getToolDescription())
                .httpMethod(e.getHttpMethod())
                .path(e.getPath())
                .parameterSchema(e.getParameterSchema())
                .responseSchema(e.getResponseSchema())
                .examplePayload(e.getExamplePayload())
                .enabled(e.isEnabled())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
