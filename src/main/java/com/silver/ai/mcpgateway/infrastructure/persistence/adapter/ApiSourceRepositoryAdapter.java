package com.silver.ai.mcpgateway.infrastructure.persistence.adapter;

import com.silver.ai.mcpgateway.domain.model.ApiSource;
import com.silver.ai.mcpgateway.domain.port.ApiSourceRepository;
import com.silver.ai.mcpgateway.infrastructure.persistence.entity.ApiSourceEntity;
import com.silver.ai.mcpgateway.infrastructure.persistence.r2dbc.R2dbcApiSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Repository
@RequiredArgsConstructor
public class ApiSourceRepositoryAdapter implements ApiSourceRepository {

    private final R2dbcApiSourceRepository r2dbc;

    @Override
    public Mono<ApiSource> save(ApiSource source) {
        return r2dbc.save(toEntity(source)).map(this::toDomain);
    }

    @Override
    public Mono<ApiSource> findById(Long id) {
        return r2dbc.findById(id).map(this::toDomain);
    }

    @Override
    public Flux<ApiSource> findAll() {
        return r2dbc.findAll().map(this::toDomain);
    }

    @Override
    public Flux<ApiSource> findByActive(boolean active) {
        return r2dbc.findByActive(active).map(this::toDomain);
    }

    @Override
    public Mono<Void> deleteById(Long id) {
        return r2dbc.deleteById(id);
    }

    private ApiSourceEntity toEntity(ApiSource d) {
        LocalDateTime now = LocalDateTime.now();
        return ApiSourceEntity.builder()
                .id(d.getId())
                .name(d.getName())
                .description(d.getDescription())
                .protocolType(d.getProtocolType())
                .baseUrl(d.getBaseUrl())
                .openApiSpec(d.getOpenApiSpec())
                .active(d.isActive())
                .healthStatus(d.getHealthStatus())
                .lastHealthCheckAt(d.getLastHealthCheckAt())
                .lastHealthyAt(d.getLastHealthyAt())
                .consecutiveFailures(d.getConsecutiveFailures())
                .lastErrorMessage(d.getLastErrorMessage())
                .createdAt(d.getCreatedAt() != null ? d.getCreatedAt() : now)
                .updatedAt(now)
                .build();
    }

    private ApiSource toDomain(ApiSourceEntity e) {
        return ApiSource.builder()
                .id(e.getId())
                .name(e.getName())
                .description(e.getDescription())
                .protocolType(e.getProtocolType())
                .baseUrl(e.getBaseUrl())
                .openApiSpec(e.getOpenApiSpec())
                .active(e.isActive())
                .toolMappings(new ArrayList<>())
                .healthStatus(e.getHealthStatus())
                .lastHealthCheckAt(e.getLastHealthCheckAt())
                .lastHealthyAt(e.getLastHealthyAt())
                .consecutiveFailures(e.getConsecutiveFailures())
                .lastErrorMessage(e.getLastErrorMessage())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
