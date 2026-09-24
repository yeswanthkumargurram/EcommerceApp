package com.example.order.client;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/** Looks up authoritative product prices instead of trusting a client-supplied amount. */
@Component
public class ProductClient {
    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ProductClient(RestTemplate restTemplate, @Value("${services.product.base-url:http://localhost:8081}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public ProductView getProduct(Long productId) {
        try {
            ProductView product = restTemplate.getForObject(baseUrl + "/api/products/{id}", ProductView.class, productId);
            if (product == null) {
                throw new ResponseStatusException(BAD_REQUEST, "product not found: " + productId);
            }
            return product;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(BAD_REQUEST, "product not found: " + productId);
        } catch (RestClientException e) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "product-service unavailable: " + e.getMessage());
        }
    }

    @Data
    public static class ProductView {
        private Long id;
        private String name;
        private double price;
    }
}
