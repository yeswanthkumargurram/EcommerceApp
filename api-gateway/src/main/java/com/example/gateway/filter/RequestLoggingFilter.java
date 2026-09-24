package com.example.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Illustrates the gateway cross-cutting-concern pattern: one place to log/observe every routed request. */
@Component
@Slf4j
public class RequestLoggingFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        String path = exchange.getRequest().getURI().getPath();
        return chain.filter(exchange).doFinally(signal -> {
            long tookMs = System.currentTimeMillis() - start;
            log.info("{} {} -> {} ({} ms)", exchange.getRequest().getMethod(), path,
                    exchange.getResponse().getStatusCode(), tookMs);
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
