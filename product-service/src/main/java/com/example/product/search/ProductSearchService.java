package com.example.product.search;

import com.example.product.model.Product;
import com.example.product.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Wraps Elasticsearch calls so a down/misconfigured cluster degrades to the
 * existing JPA LIKE-query search instead of taking Product Catalog offline -
 * search is a "nice to have" feature, not a critical path.
 */
@Service
@Slf4j
public class ProductSearchService {
    private ProductSearchRepository searchRepository;
    private final ProductRepository productRepository;

    public ProductSearchService(ProductRepository productRepository, @Nullable ProductSearchRepository searchRepository) {
        this.productRepository = productRepository;
        this.searchRepository = searchRepository;
    }

    public void index(Product product) {
        if (searchRepository == null) {
            log.debug("Search repository not available; skipping indexing for product {}", product.getId());
            return;
        }
        try {
            searchRepository.save(toDocument(product));
        } catch (Exception e) {
            log.warn("Failed to index product {} in Elasticsearch: {}", product.getId(), e.getMessage());
        }
    }

    public List<Product> search(String query) {
        if (searchRepository == null) {
            log.debug("Search repository not available; falling back to DB for query={}", query);
            return productRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query);
        }
        try {
            List<ProductDocument> hits = searchRepository.search(query);
            return hits.stream()
                    .map(doc -> productRepository.findById(Long.valueOf(doc.getId())))
                    .flatMap(java.util.Optional::stream)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Elasticsearch search unavailable, falling back to DB LIKE query: {}", e.getMessage());
            return productRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query);
        }
    }

    private ProductDocument toDocument(Product product) {
        return new ProductDocument(
                String.valueOf(product.getId()),
                product.getName(),
                product.getDescription(),
                product.getCategory() != null ? product.getCategory().getName() : null,
                product.getPrice());
    }
}
