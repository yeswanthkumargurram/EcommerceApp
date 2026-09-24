package com.example.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {
    /** Anonymous product browsing has no JWT to key on, so rate-limit by client IP instead. */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
                .map(address -> address.getAddress().getHostAddress())
                .defaultIfEmpty("unknown");
    }
}
