package com.silver.ai.mcpgateway.domain.port;

import com.silver.ai.mcpgateway.domain.model.ApiSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ApiSourceRepository {

    Mono<ApiSource> save(ApiSource source);

    Mono<ApiSource> findById(Long id);

    Flux<ApiSource> findAll();

    Flux<ApiSource> findByActive(boolean active);

    Mono<Void> deleteById(Long id);
}
