package com.example.product.search;

import java.util.List;

/**
 * Lightweight repository interface used when Elasticsearch is enabled.
 *
 * When Elasticsearch is unavailable or intentionally disabled for local
 * development, the application will run without an implementation of this
 * interface and `ProductSearchService` will fall back to DB queries.
 */
public interface ProductSearchRepository {
    List<ProductDocument> search(String query);
    void save(ProductDocument doc);
}
