package com.silver.ai.mcpgateway.domain.port;

import com.silver.ai.mcpgateway.domain.model.ToolMapping;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ToolMappingRepository {

    Mono<ToolMapping> save(ToolMapping mapping);

    Mono<ToolMapping> findById(Long id);

    Flux<ToolMapping> findByApiSourceId(Long apiSourceId);

    Flux<ToolMapping> findByEnabled(boolean enabled);

    Mono<Void> deleteByApiSourceId(Long apiSourceId);

    Mono<Void> deleteById(Long id);
}
