package com.silver.ai.mcpgateway.infrastructure.persistence.r2dbc;

import com.silver.ai.mcpgateway.infrastructure.persistence.entity.ToolMappingEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface R2dbcToolMappingRepository extends ReactiveCrudRepository<ToolMappingEntity, Long> {
    Flux<ToolMappingEntity> findByApiSourceId(Long apiSourceId);
    Flux<ToolMappingEntity> findByEnabled(boolean enabled);
    Mono<Void> deleteByApiSourceId(Long apiSourceId);
}
