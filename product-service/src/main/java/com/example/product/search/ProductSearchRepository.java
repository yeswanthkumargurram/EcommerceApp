package com.example.product.search;

import java.util.List;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {
    @Query("""
        {"multi_match": {"query": "?0", "fields": ["name^2", "description", "category"], "fuzziness": "AUTO"}}
        """)
    List<ProductDocument> search(String query);
}
