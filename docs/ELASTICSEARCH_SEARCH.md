# Elasticsearch Product Search — Concepts and Repo Mapping

Goal: understand why full-text search engines exist alongside a relational DB, the dual-write consistency tradeoff, and how this repo degrades gracefully when the search cluster is unavailable.

## 1) Why not just `LIKE '%query%'` in MySQL?

The original `ProductController.list(q=...)` used `findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase` — a SQL `LIKE '%...%'` query. That works for tiny datasets but has real limits:

- **No relevance ranking** — every match is equally "good"; there's no way to rank a title match above a match buried in a long description.
- **No typo tolerance** — searching "keybord" finds nothing.
- **`LIKE '%x%'` can't use a B-tree index** (leading wildcard), so it's a full table scan that gets slower as the catalog grows.
- **No language-aware tokenization** (stemming "running" to match "run", stopword removal, synonyms).

Elasticsearch (built on Lucene) is a purpose-built inverted-index search engine that solves all of the above — exactly why the PRD calls it out for the Product Catalog Service specifically.

## 2) Design: MySQL is still the source of truth

```
ProductController.create()
        │
        ├──> ProductRepository.save()        (MySQL/H2 - source of truth)
        └──> ProductSearchService.index()     (Elasticsearch - read model)
```

See [`ProductDocument`](../product-service/src/main/java/com/example/product/search/ProductDocument.java) and [`ProductSearchService`](../product-service/src/main/java/com/example/product/search/ProductSearchService.java). This is a **dual write**: every create writes to both MySQL and Elasticsearch. `GET /api/products/{id}` and ownership/authorization checks still go through MySQL; only the new `GET /api/products/search?q=` endpoint hits Elasticsearch (via `multi_match` with `fuzziness: AUTO` for typo tolerance, boosting the `name` field over `description`).

### The dual-write consistency problem (and why it's called out, not fixed)

If the MySQL write succeeds but the Elasticsearch write fails (network blip, ES down), the product exists in the DB but won't show up in search until the next write. This sample **does not** solve that with a fully consistent pipeline (e.g. Debezium/CDC streaming DB changes into Elasticsearch, or an outbox pattern) — it just logs a warning and moves on, because search is a secondary/"nice to have" read path, not the system of record. A production system with a hard requirement that "every product must be searchable" would use CDC instead of an inline dual write.

## 3) Graceful degradation

`ProductSearchService.search()` catches any Elasticsearch exception and falls back to the original DB `LIKE` query:

```java
try {
    return elasticsearchHits(...);
} catch (Exception e) {
    log.warn(...);
    return productRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(query, query);
}
```

This means product-service (and its existing tests, which never touch Elasticsearch) keeps working even if the ES container isn't running — search quality degrades, but the feature doesn't 500. This "degrade, don't fail" approach is a good default for any non-critical dependency.

## 4) Try it yourself

1. `docker-compose up elasticsearch`.
2. `POST /api/products` a couple of products with distinct names/descriptions.
3. `GET /api/products/search?q=<partial-or-misspelled-word>` — should return relevance-ranked, typo-tolerant results.
4. Stop the `elasticsearch` container and repeat the search — same endpoint still returns results, now via the DB fallback (check the product-service logs for the "falling back to DB LIKE query" warning).

## 5) Interview talking points

- Inverted index basics: Elasticsearch tokenizes text into terms and maps each term to the documents containing it — this is what makes `LIKE`-style scans unnecessary.
- CQRS (Command Query Responsibility Segregation): this is a small example — writes go through one model (JPA/MySQL), a subset of reads go through a different, denormalized model (Elasticsearch) optimized for that read pattern.
- What's missing for production: reindexing strategy (bulk rebuild the ES index from MySQL if they drift), handling product updates/deletes (only `create` indexes today — see `docs/SCOPE_DEVIATIONS.md`), index aliasing for zero-downtime mapping changes.
