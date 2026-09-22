# Annotations Reference — EcommerceApp

A concise, interview-ready reference of annotations used across the EcommerceApp services (Spring Boot, Spring Data/JPA, Lombok, validation, Jackson, testing, and related). For each annotation: what it means, why it's used, and a short usage note.

## How to read this file
- Categories group related annotations.
- Each annotation shows a short explanation and typical usage.

---

**Spring Boot & Core**

- `@SpringBootApplication`: Meta-annotation that enables component scanning, auto-configuration and configuration properties. Used on the main application class to bootstrap a Spring Boot app.
- `@Configuration`: Marks a class as a source of bean definitions; methods annotated with `@Bean` inside it define Spring-managed beans.
- `@Bean`: Declares a method that returns an object to be registered as a Spring Bean.
- `@Component`: Generic stereotype for any Spring-managed component; picked up by component-scan.
- `@Service`: Specialized `@Component` indicating a service-layer bean (business logic).
- `@Repository`: Specialized `@Component` for persistence/DAO layer; also translates persistence exceptions into Spring's DataAccessException.

**Web / MVC / Controllers**

- `@Controller`: Marks a web controller that typically returns views (MVC controllers).
- `@RestController`: `@Controller` + `@ResponseBody` — used for REST APIs returning JSON.
- `@RequestMapping`: Generic request mapping; can be applied at class or method level to map paths and HTTP methods.
- `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`: Shorthand for `@RequestMapping` with the corresponding HTTP method.
- `@PathVariable`: Binds a method parameter to a URI template variable.
- `@RequestParam`: Binds a method parameter to a query parameter or form value.
- `@RequestBody`: Binds the HTTP request body to a Java object (typically JSON → POJO).
- `@RestControllerAdvice`: Global exception handler for REST controllers; used with `@ExceptionHandler` to centralize error handling.
- `@ExceptionHandler`: Method-level handler for specific exception types inside controllers or controller advice.

**Dependency Injection & Configuration Helpers**

- `@Autowired` (optional in codebases that use constructor injection / Lombok): Field/setter/constructor injection of a bean. Constructor injection with Lombok's `@RequiredArgsConstructor` is preferred.
- `@Value`: Injects property values from configuration into fields.

**Persistence / JPA / Hibernate**

- `@Entity`: Marks a class as a JPA entity mapped to a database table.
- `@Table(name = "...")`: Specifies the database table name for an entity.
- `@Id`: Marks the primary key field of an entity.
- `@GeneratedValue`: Configures automatic id generation strategy.
- `@Column`: Customizes column mapping (name, nullable, length, etc.).
- `@MappedSuperclass`: Marks a class whose mappings are inherited by subclasses (common base fields).
- `@Transient`: Marks a field that should not be persisted.
- `@ManyToOne`, `@OneToMany`, `@OneToOne`, `@ManyToMany`: Relationship mappings between entities.
- `@JoinColumn`: Specifies the foreign-key column used in a relationship.

 - `@ElementCollection`: Maps a collection of basic or embeddable types (for example `List<String>` or `Map<String,String>`) to a separate collection table. Use when the collection elements are not full entities but should be persisted in a dedicated table associated with the owning entity.

 - `@CollectionTable(name = "...", joinColumns = @JoinColumn("..."))`: Configures the table that stores an `@ElementCollection`, including the table name and the foreign-key column(s) that join back to the owning entity. Commonly paired with `@Column` or `@MapKeyColumn` to name value/key columns.

 - `@MapKeyColumn`: Defines the column name used for the map key when persisting a `Map` as an `@ElementCollection`. Use this together with `@Column` to control both key and value column names in the collection table.

**Spring Data / Repositories / Search**

- `@Query`: Defines a custom JPQL or SQL (or Elasticsearch) query on a repository method.
- `@Document(indexName = "...")` (Spring Data Elasticsearch): Marks a class as an Elasticsearch document and configures index name.
- `@DataJpaTest`: Test slice annotation to bootstrap only JPA-related components for repository testing.

**Transactions**

- `@Transactional`: Declares transactional boundaries; can be used at method or class level to start/commit/rollback transactions automatically.

**Validation (JSR 380 / javax.validation)**

- `@Valid`: Triggers validation on an argument (e.g., a `@RequestBody` DTO) so constraint annotations are enforced.
- `@NotNull`, `@Size`, `@Email`, `@Pattern` (and other constraint annotations): Field-level constraints used to validate inputs (nullability, string length, email format, regex patterns).

**Jackson (JSON serialization/deserialization)**

- `@JsonProperty`: Binds a JSON property name to a Java field or getter/setter. Useful for renaming or ordering JSON fields.
- `@JsonIgnore`: Excludes a field from JSON serialization/deserialization.

**Lombok (compile-time code generation)**

- `@Data`: Generates getters, setters, `toString`, `equals`/`hashCode`, and a required-args constructor.
- `@NoArgsConstructor`, `@AllArgsConstructor`: Generate a no-arg or all-arg constructor respectively.
- `@RequiredArgsConstructor`: Generates a constructor for final fields (used for constructor injection).
- `@Builder`: Generates the builder pattern for constructing objects immutably/fluently.
- `@Slf4j`: Injects an `org.slf4j.Logger` named `log` for convenient logging.

**Logging / Utilities**n
- `@Slf4j`: (Listed above under Lombok) used across services for logging without boilerplate.

**Testing**

- `@SpringBootTest`: Bootstraps the full Spring Boot context for integration tests.
- `@Test` (JUnit): Marks a test method.
- `@BeforeAll`: JUnit lifecycle method run once before all tests in a class.

**Kafka / Messaging / Schedulers**

- `@Component` / `@Service` / `@Controller`: Commonly used to mark listeners and publishers as Spring-managed beans. (Kafka listeners in this codebase are implemented as `@Component` classes.)

---

Notes and interview tips
- Prefer constructor injection (final fields + Lombok's `@RequiredArgsConstructor`) over field injection; it's easier to unit-test and immutable.
- Distinguish stereotypes (`@Component`, `@Service`, `@Repository`, `@Controller`/`@RestController`) by intent: they are all components but communicate architectural role and can have small framework-specific behavior (`@Repository` exception translation, `@RestController` `@ResponseBody`).
- Understand JPA annotations on entities (`@Entity`, `@Table`, `@Id`, relationship annotations) and why `@MappedSuperclass` helps reuse common fields like `id`, `createdAt`, `updatedAt`.
- Be ready to explain validation flow: `@Valid` on controller arguments triggers validation of field constraints (`@NotNull`, `@Size`), and how `@RestControllerAdvice`/`@ExceptionHandler` maps validation errors to HTTP responses.
- Know Lombok reduces boilerplate; be ready to discuss pros/cons (compile-time generation, fewer visible methods, IDE support requirements).

---

If you'd like, I can:
- Add code snippets showing one concise example per annotation category.
- Link every annotation to the actual file(s) in this repository where it appears.

File: [docs/ANNOTATIONS.md](docs/ANNOTATIONS.md)
