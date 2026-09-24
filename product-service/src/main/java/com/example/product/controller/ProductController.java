package com.example.product.controller;

import com.example.product.model.Product;
import com.example.product.model.Category;
import com.example.product.repository.ProductRepository;
import com.example.product.repository.CategoryRepository;
import com.example.product.search.ProductSearchService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductRepository repo;
    private final ProductSearchService searchService;
    private final CategoryRepository categoryRepo;

    @GetMapping
    public List<Product> list(@RequestParam(value = "category", required = false) String category,
                              @RequestParam(value = "query", required = false) String query) {
        if (query != null && !query.isBlank()) {
            return repo.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query);
        }
        if (category != null && !category.isBlank()) {
            return repo.findByCategory_Name(category);
        }
        return repo.findAll();
    }

    /** Elasticsearch-backed relevance search (PRD: "search for products using keywords"); falls back to DB on ES outage. */
    @GetMapping("/search")
    public List<Product> search(@RequestParam("query") String query) {
        return searchService.search(query);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable("id") Long id) {
        return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Product> create(@RequestBody Product p) {
        // Ensure category is managed: try to resolve by id or name, otherwise persist new
        if (p.getCategory() != null) {
            Category cat = p.getCategory();
            if (cat.getId() != null) {
                cat = categoryRepo.findById(cat.getId()).orElse(cat);
            } else if (cat.getName() != null && !cat.getName().isBlank()) {
                cat = categoryRepo.findByName(cat.getName()).orElse(cat);
            }
            // if still transient (no id), save it first so Product points to managed instance
            if (cat.getId() == null) {
                cat = categoryRepo.save(cat);
            }
            p.setCategory(cat);
        }

        Product saved = repo.save(p);
        searchService.index(saved);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/health")
    public String health() {
        return "product-service:ok";
    }
}
