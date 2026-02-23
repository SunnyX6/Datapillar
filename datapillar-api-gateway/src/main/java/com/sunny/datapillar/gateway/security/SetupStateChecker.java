package com.sunny.datapillar.gateway.security;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * 初始化状态Checker组件
 * 负责初始化状态Checker核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Slf4j
@Component
public class SetupStateChecker {

    private static final String STUDIO_SERVICE_ID = "datapillar-studio-service";
    private static final String STATUS_ENDPOINT = "/api/studio/setup/status";
    private static final long CACHE_TTL_MILLIS = 3000L;
    private static final long FAILURE_CACHE_TTL_MILLIS = 1000L;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final ReactiveDiscoveryClient discoveryClient;
    private final WebClient webClient;

    private volatile CachedState cachedState = new CachedState(SetupState.unavailable(), 0L);
    private volatile Mono<SetupState> inFlight;

    public SetupStateChecker(ReactiveDiscoveryClient discoveryClient, WebClient.Builder webClientBuilder) {
        this.discoveryClient = discoveryClient;
        this.webClient = webClientBuilder.build();
    }

    public Mono<SetupState> currentState() {
        long now = System.currentTimeMillis();
        CachedState snapshot = cachedState;
        if (now < snapshot.expiresAtMillis()) {
            return Mono.just(snapshot.state());
        }

        Mono<SetupState> running = inFlight;
        if (running != null) {
            return running;
        }

        synchronized (this) {
            long current = System.currentTimeMillis();
            CachedState latest = cachedState;
            if (current < latest.expiresAtMillis()) {
                return Mono.just(latest.state());
            }

            if (inFlight == null) {
                inFlight = fetchStateFromStudio()
                        .onErrorResume(ex -> {
                            log.error("setup 状态探针请求失败", ex);
                            return Mono.just(SetupState.unavailable());
                        })
                        .doOnNext(state -> {
                            long ttl = state.available() ? CACHE_TTL_MILLIS : FAILURE_CACHE_TTL_MILLIS;
                            cachedState = new CachedState(state, System.currentTimeMillis() + ttl);
                        })
                        .doFinally(signalType -> inFlight = null)
                        .cache();
            }

            return inFlight;
        }
    }

    private Mono<SetupState> fetchStateFromStudio() {
        return discoveryClient.getInstances(STUDIO_SERVICE_ID)
                .next()
                .switchIfEmpty(Mono.error(new IllegalStateException("未发现 studio-service 实例")))
                .flatMap(this::requestStatus)
                .timeout(REQUEST_TIMEOUT);
    }

    private Mono<SetupState> requestStatus(ServiceInstance instance) {
        URI uri = UriComponentsBuilder.fromUri(instance.getUri())
                .path(STATUS_ENDPOINT)
                .build(true)
                .toUri();

        return webClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(this::parseStatusResponse);
    }

    private SetupState parseStatusResponse(Map<String, Object> response) {
        if (response == null) {
            throw new IllegalStateException("setup 状态响应为空");
        }

        Integer code = parseCode(response.get("code"));
        if (code == null) {
            throw new IllegalStateException("setup 状态响应缺少 code 字段");
        }

        if (code == Code.OK) {
            Object dataObject = response.get("data");
            if (!(dataObject instanceof Map<?, ?> dataMap)) {
                throw new IllegalStateException("setup 状态响应缺少 data 字段");
            }

            boolean schemaReady = parseBoolean(dataMap.get("schemaReady"));
            boolean initialized = parseBoolean(dataMap.get("initialized"));
            return new SetupState(true, schemaReady, initialized);
        }

        String type = response.get("type") instanceof String value ? value : null;
        if (code == Code.SERVICE_UNAVAILABLE && ErrorType.REQUIRED.equals(type)) {
            Map<?, ?> context = response.get("context") instanceof Map<?, ?> map ? map : Map.of();
            String reason = context.get("reason") instanceof String value ? value : null;
            if ("SETUP_SCHEMA_NOT_READY".equals(reason)) {
                return new SetupState(true, false, false);
            }
            if ("SETUP_REQUIRED".equals(reason)) {
                return new SetupState(true, true, false);
            }
            return new SetupState(true, false, false);
        }
        throw new IllegalStateException("setup 状态响应 code 非 OK: " + code);
    }

    private Integer parseCode(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    private boolean parseBoolean(Object value) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        if (value instanceof String stringValue) {
            return "1".equals(stringValue) || "true".equalsIgnoreCase(stringValue);
        }
        return false;
    }

    private record CachedState(SetupState state, long expiresAtMillis) {
    }

    public record SetupState(boolean available, boolean schemaReady, boolean setupCompleted) {

        static SetupState unavailable() {
            return new SetupState(false, false, false);
        }
    }
}
