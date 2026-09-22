Re-enabling Elasticsearch locally (product-service)

This note shows the minimal steps to restore Elasticsearch support for `product-service` after it was temporarily disabled for local development.

Steps

1) Re-enable the Elasticsearch starter dependency
- Open `product-service/pom.xml` and restore the dependency block for the starter (remove the `provided` scope if present):

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
</dependency>
```

2) Remove the auto-configuration exclusion
- Open `product-service/src/main/resources/application.yml` and remove the `spring.autoconfigure.exclude` entries added earlier. After removal, `spring.elasticsearch.uris` should remain (or update if needed):

```yaml
spring:
  elasticsearch:
    uris: http://localhost:9200
```

3) Restore repository and service classes
- If you changed `ProductSearchRepository` and `ProductSearchService` to make the search layer optional, restore their original implementations. Example repository (restore the Elasticsearch interface and `@Query`):

```java
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
```

- Restore `ProductSearchService` constructor to require `ProductSearchRepository` (or revert to your VCS copy). If you used Lombok constructor injection originally, restore `@RequiredArgsConstructor`.

4) Start Elasticsearch locally
- Login to Elastic registry if required (some images require auth):

```powershell
docker login docker.elastic.co
```

- From the repository root, start Elasticsearch via docker compose:

```powershell
cd <repo-root>
docker compose up -d --build elasticsearch
```

- If the registry requires credentials or your environment has an HTTP proxy/TLS interception, configure Docker to use the proxy and/or trust your proxy CA. Consult your IT or Docker Desktop settings for proxy/CA configuration.

5) Verify Elasticsearch is reachable

```powershell
# Windows PowerShell
Invoke-RestMethod -Uri http://localhost:9200

# or using curl
curl http://localhost:9200
```

6) Run product-service

```powershell
cd product-service
mvn -DskipTests spring-boot:run
```

Notes / Troubleshooting

- If you see HTTP 401 when pulling `docker.elastic.co` images, you must `docker login` with Elastic credentials or pull an alternative image that you host.
- If you used version control (Git), easiest way to restore code is to discard local changes in the two modified files and checkout the originals:

```bash
git checkout -- product-service/src/main/java/com/example/product/search/ProductSearchRepository.java
git checkout -- product-service/src/main/java/com/example/product/search/ProductSearchService.java
git checkout -- product-service/pom.xml
git checkout -- product-service/src/main/resources/application.yml
```

- After restoring code and starting Elasticsearch, re-run `mvn clean package` to ensure compilation and integration with ES works.

Want me to: (a) apply the restore edits automatically, or (b) open the files for you to review/restore manually?