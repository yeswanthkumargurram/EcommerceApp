package com.example.product.security;

import com.example.common.security.JwtProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public JwtProvider jwtProvider(@Value("${jwt.secret:0123456789abcdef0123456789abcdef}") String secret) {
        return new JwtProvider(secret);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtProvider jwtProvider, Environment env) throws Exception {
        boolean isDev = env.acceptsProfiles(Profiles.of("dev"));

        http.csrf().disable()
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/api/products/**").permitAll();
                    if (isDev) {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                        auth.requestMatchers("/h2-console/**", "/h2-console").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(new JwtFilter(jwtProvider), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        if (isDev) {
            http.headers().frameOptions().disable();
        }
        return http.build();
    }
}
