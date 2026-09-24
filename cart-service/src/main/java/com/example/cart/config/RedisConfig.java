package com.example.cart.config;

import com.example.cart.model.Cart;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Cart> cartRedisTemplate(RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

        // Typed (not Generic) serializer: since this template only ever holds Cart values, we don't need
        // polymorphic "@class" type metadata. Embedding it previously caused Hibernate's runtime collection/proxy
        // types (e.g. PersistentBag) to be recorded, which can't be reconstructed on read without a live Session.
        Jackson2JsonRedisSerializer<Cart> serializer = new Jackson2JsonRedisSerializer<>(mapper, Cart.class);

        RedisTemplate<String, Cart> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        return template;
    }
}
