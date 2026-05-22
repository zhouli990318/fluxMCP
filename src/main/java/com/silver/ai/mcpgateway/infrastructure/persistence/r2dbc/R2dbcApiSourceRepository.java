package com.silver.ai.mcpgateway.infrastructure.persistence.r2dbc;

import com.silver.ai.mcpgateway.infrastructure.persistence.entity.ApiSourceEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface R2dbcApiSourceRepository extends ReactiveCrudRepository<ApiSourceEntity, Long> {
    Flux<ApiSourceEntity> findByActive(boolean active);
}
