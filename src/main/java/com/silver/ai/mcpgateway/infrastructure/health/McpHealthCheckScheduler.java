package com.silver.ai.mcpgateway.infrastructure.health;

import com.silver.ai.mcpgateway.domain.model.ApiSource;
import com.silver.ai.mcpgateway.domain.model.HealthStatus;
import com.silver.ai.mcpgateway.domain.port.ApiSourceRepository;
import com.silver.ai.mcpgateway.infrastructure.config.McpHealthProperties;
import com.silver.ai.mcpgateway.infrastructure.mcp.SourceScopedMcpServerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 源心跳检测 + 自动重连调度器。
 *
 * <p>定时对所有 active 源发起 HEAD 请求，根据结果更新 {@link HealthStatus}：
 * <ul>
 *   <li>2xx/3xx 且响应快 → HEALTHY（若先前 UNREACHABLE，触发 registry 重建）</li>
 *   <li>2xx/3xx 但响应慢于阈值 → DEGRADED</li>
 *   <li>超时/4xx/5xx/网络异常 → UNREACHABLE（失败次数累加，达阈值可自动 deactivate）</li>
 * </ul>
 *
 * <p>不可达的源使用指数退避跳过探测，避免频繁无效请求。
 */
@Slf4j
@Component
public class McpHealthCheckScheduler {

    private final ApiSourceRepository apiSourceRepository;
    private final SourceScopedMcpServerRegistry registry;
    private final McpHealthProperties properties;

    private final WebClient webClient;

    /** 跟踪下次允许探测的时间（指数退避） */
    private final ConcurrentHashMap<Long, Instant> nextProbeAt = new ConcurrentHashMap<>();

    public McpHealthCheckScheduler(ApiSourceRepository apiSourceRepository,
                                    SourceScopedMcpServerRegistry registry,
                                    McpHealthProperties properties,
                                    WebClient.Builder webClientBuilder) {
        this.apiSourceRepository = apiSourceRepository;
        this.registry = registry;
        this.properties = properties;
        this.webClient = webClientBuilder.clone().build();
    }

    @Scheduled(fixedDelayString = "#{@mcpHealthProperties.checkInterval.toMillis()}",
               initialDelayString = "#{@mcpHealthProperties.checkInterval.toMillis()}")
    public void runHealthCheck() {
        if (!properties.isEnabled()) {
            return;
        }

        apiSourceRepository.findByActive(true)
                .filter(source -> source.getBaseUrl() != null && !source.getBaseUrl().isBlank())
                .filter(source -> canProbe(source.getId()))
                .flatMap(this::probeAndPersist, Math.max(1, properties.getProbeConcurrency()))
                .doOnError(e -> log.warn("Health check run failed: {}", e.getMessage()))
                .subscribe();
    }

    /**
     * 手动触发单个源的健康检查。
     */
    public Mono<ApiSource> probeOnce(Long sourceId) {
        return apiSourceRepository.findById(sourceId)
                .flatMap(this::probeAndPersist);
    }

    private boolean canProbe(Long sourceId) {
        Instant next = nextProbeAt.get(sourceId);
        return next == null || !Instant.now().isBefore(next);
    }

    private Mono<ApiSource> probeAndPersist(ApiSource source) {
        HealthStatus previous = source.getHealthStatus();
        long startMs = System.currentTimeMillis();

        return probe(source)
                .map(durationMs -> {
                    if (durationMs > properties.getSlowThreshold().toMillis()) {
                        source.markDegraded("slow response: " + durationMs + "ms");
                    } else {
                        source.markHealthy();
                    }
                    nextProbeAt.remove(source.getId());
                    return source;
                })
                .onErrorResume(err -> {
                    String msg = err.getMessage() == null ? err.getClass().getSimpleName() : err.getMessage();
                    source.markUnreachable(msg);
                    // 指数退避：失败 N 次后，跳过 N 个周期的探测
                    long backoff = Math.min(
                            properties.getCheckInterval().toMillis() * (1L << Math.min(source.getConsecutiveFailures(), 5)),
                            Duration.ofMinutes(5).toMillis());
                    nextProbeAt.put(source.getId(), Instant.now().plusMillis(backoff));
                    log.warn("MCP source {} [{}] unreachable: {} (failure #{}), next probe in {}ms",
                            source.getId(), source.getName(), msg,
                            source.getConsecutiveFailures(), backoff);
                    return Mono.just(source);
                })
                .flatMap(updated -> apiSourceRepository.save(updated)
                        .doOnSuccess(saved -> onHealthTransition(previous, saved)))
                .doOnSuccess(s -> log.debug("Health probed source {} in {}ms -> {}",
                        s.getId(), System.currentTimeMillis() - startMs, s.getHealthStatus()));
    }

    /**
     * 发起 HEAD 请求探测可达性，返回响应耗时（毫秒）。
     * 失败（超时/5xx/网络错误）通过 Mono.error 抛出。
     */
    private Mono<Long> probe(ApiSource source) {
        URI uri;
        try {
            uri = URI.create(source.getBaseUrl());
        } catch (IllegalArgumentException e) {
            return Mono.error(new IllegalStateException("invalid baseUrl: " + source.getBaseUrl()));
        }
        long start = System.currentTimeMillis();
        return webClient.method(HttpMethod.HEAD)
                .uri(uri)
                .retrieve()
                // 允许 4xx 作为可达（外部服务存在但拒绝 HEAD 属于可达，不算 UNREACHABLE）
                .onStatus(HttpStatusCode::is5xxServerError,
                        resp -> Mono.error(new IllegalStateException("server error: " + resp.statusCode().value())))
                .toBodilessEntity()
                .timeout(properties.getProbeTimeout())
                .map(_ -> System.currentTimeMillis() - start)
                // 4xx 会走到这里，算可达但可能 DEGRADED
                .onErrorResume(org.springframework.web.reactive.function.client.WebClientResponseException.class,
                        e -> e.getStatusCode().is4xxClientError()
                                ? Mono.just(System.currentTimeMillis() - start)
                                : Mono.error(e));
    }

    private void onHealthTransition(HealthStatus previous, ApiSource saved) {
        HealthStatus now = saved.getHealthStatus();
        if (previous == HealthStatus.UNREACHABLE && now == HealthStatus.HEALTHY) {
            // 恢复：重建 MCP 服务器
            registry.refreshSourceAsync(saved)
                    .doOnSuccess(ignored -> log.info("MCP source {} [{}] recovered, registry refreshed",
                            saved.getId(), saved.getName()))
                    .doOnError(e -> log.warn("Failed to refresh registry for recovered source {}",
                            saved.getId(), e))
                    .subscribe();
        }

        // 连续失败达阈值：自动 deactivate
        if (properties.isAutoDeactivateOnMaxFailures()
                && now == HealthStatus.UNREACHABLE
                && saved.getConsecutiveFailures() >= properties.getMaxConsecutiveFailures()
                && saved.isActive()) {
            log.warn("MCP source {} [{}] reached {} consecutive failures, auto-deactivating",
                    saved.getId(), saved.getName(), saved.getConsecutiveFailures());
            saved.deactivate();
            apiSourceRepository.save(saved)
                    .doOnSuccess(s -> registry.removeSourceAsync(s.getId())
                            .doOnError(e -> log.warn("Failed to remove deactivated source {} from registry",
                                    s.getId(), e))
                            .subscribe())
                    .subscribe();
        }
    }
}
