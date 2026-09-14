# Auth Service and JWT Guide

This guide explains the `auth-service` in this project from first principles. It describes what the current code actually does, why each part exists, and how JSON Web Tokens (JWTs) fit into a microservice application.

## 1. What problem does auth-service solve?

Most application endpoints need to know who is making a request. For example, an order service may need to know which customer is placing an order.

This service has two public jobs:

1. Register a user by saving their email and a safely hashed password.
2. Log a user in by checking their credentials and returning a signed JWT.

The client can send that JWT to other protected services. Those services can verify it without asking the auth service to look up the user on every request.

## 2. The Request Flow at a Glance

```mermaid
sequenceDiagram
  participant C
  participant AC
  participant S
  participant D
  participant DB
  participant J

  C->>AC: POST /register
  AC->>DB: check email
  AC->>AC: hash password
  AC->>DB: save user
  AC-->>C: id,email

  C->>AC: POST /login
  AC->>S: authenticate
  S->>D: loadUser
  D->>DB: find email
  DB-->>D: user row
  D-->>S: user details
  S->>S: compare password
  S-->>AC: auth result
  AC->>J: gen token
  J-->>AC: token
  AC-->>C: {token}
```
  
**Diagram Legend**

- `C`: Client
- `AC`: AuthController
- `S`: Spring Security (authentication manager)
- `D`: `JpaUserDetailsService` (loads users)
- `DB`: H2 users table (persistence)
- `J`: `JwtProvider` (JWT creation/validation)

## 3. Main Files and Their Responsibilities

| File | Responsibility |
| --- | --- |
| [auth-service/src/main/java/com/example/auth/AuthServiceApplication.java](auth-service/src/main/java/com/example/auth/AuthServiceApplication.java) | Starts the Spring Boot application. |
| [auth-service/src/main/java/com/example/auth/web/AuthController.java](auth-service/src/main/java/com/example/auth/web/AuthController.java) | Defines the register and login HTTP endpoints. |
| [auth-service/src/main/java/com/example/auth/security/SecurityConfig.java](auth-service/src/main/java/com/example/auth/security/SecurityConfig.java) | Creates Spring Security beans and access rules. |
| [auth-service/src/main/java/com/example/auth/service/JpaUserDetailsService.java](auth-service/src/main/java/com/example/auth/service/JpaUserDetailsService.java) | Teaches Spring Security how to load a user from this database. |
| [auth-service/src/main/java/com/example/auth/model/User.java](auth-service/src/main/java/com/example/auth/model/User.java) | JPA entity mapped to the `users` table. |
| [auth-service/src/main/java/com/example/auth/repository/UserRepository.java](auth-service/src/main/java/com/example/auth/repository/UserRepository.java) | Database access and lookup by email. |
| [common/src/main/java/com/example/common/security/JwtProvider.java](common/src/main/java/com/example/common/security/JwtProvider.java) | Shared JWT creation and signature validation code. |
| [auth-service/src/main/resources/application.yml](auth-service/src/main/resources/application.yml) | H2 database, server port, and development JWT secret. |

## 4. The User Entity and Database

`User` is marked with `@Entity`, which tells JPA/Hibernate to store it in a database table. `@Table(name = "users")` makes that table name `users`.

The entity contains:

| Field | Meaning |
| --- | --- |
| `id` | Generated primary key that identifies one row. |
| `email` | User's login name. It is required and must be unique. |
| `password` | A BCrypt password hash, not the original password. |
| `role` | Authorization role. It defaults to `USER`. |

The application is configured with an in-memory H2 database:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:authdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
  jpa:
    hibernate:
      ddl-auto: update
```

H2 is convenient while learning because it needs no database server. It is in memory, so user data disappears when the application stops. `ddl-auto: update` lets Hibernate create or update the table schema from the entity class.

`UserRepository` extends `JpaRepository<User, Long>`. That gives it standard operations such as `save`, `findById`, and `findAll`. Its custom method is:

```java
Optional<User> findByEmail(String email);
```

Spring Data reads that method name and creates the SQL query for it. `Optional` represents either one matching user or no result.

## 5. Registration: `POST /api/auth/register`

The current controller accepts a JSON object containing `email` and `password`:

```json
{
  "email": "ada@example.com",
  "password": "correct-horse-battery-staple"
}
```

The `register` method performs these steps:

1. Reads `email` and `password` from the request body.
2. Calls `repo.findByEmail(email)` to prevent duplicate email addresses.
3. Creates a `User` entity.
4. Calls `encoder.encode(password)` and stores the resulting BCrypt hash.
5. Saves the entity with `repo.save(u)`.
6. Returns the generated id and email. It deliberately does not return the password hash.

If the email already exists, the controller returns HTTP `400 Bad Request` with `email taken`.

### Why hash a password?

A hash is a one-way transformation. The server should not need, or be able, to recover a user's original password.

BCrypt is designed for passwords: it includes a random salt and is intentionally expensive to compute. Therefore the same password usually produces a different stored hash for two users. During login, BCrypt checks whether the submitted password corresponds to the stored hash.

Never replace this with plain text storage or a fast general-purpose hash such as SHA-256. A compromised password database is much more dangerous in those designs.

## 6. Login: `POST /api/auth/login`

The login endpoint accepts the `AuthRequest` DTO:

```json
{
  "email": "ada@example.com",
  "password": "correct-horse-battery-staple"
}
```

Its implementation is deliberately short:

```java
authManager.authenticate(
    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
);
String token = jwtProvider.generateToken(request.getEmail());
return ResponseEntity.ok(new AuthResponse(token));
```

The controller does not compare passwords itself. It gives Spring Security a `UsernamePasswordAuthenticationToken`, which is a container for the submitted identity and credential.

`AuthenticationManager` then uses the application's configured authentication components. In this project, it reaches `JpaUserDetailsService`.

`JpaUserDetailsService.loadUserByUsername` treats the supplied username as an email, loads the `User` from `UserRepository`, and converts it to Spring Security's `UserDetails` object. That object includes:

- the email (username)
- the BCrypt hash stored in the database
- an authority such as `ROLE_USER`

Spring Security uses the `PasswordEncoder` bean from `SecurityConfig` to verify the submitted password against that stored BCrypt hash. A missing user causes `UsernameNotFoundException`; a wrong password fails authentication. In either case, no token should be issued.

After successful authentication, the controller calls `JwtProvider.generateToken(email)` and returns:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

## 7. Spring Security Configuration

`SecurityConfig` defines several important Spring beans.

### Password encoder

```java
new BCryptPasswordEncoder()
```

The same type of encoder is used when registering a user and when Spring Security verifies a login. This match is essential: an encoded password can only be checked correctly with its compatible encoder.

### Authentication manager

```java
config.getAuthenticationManager()
```

This asks Spring Security for the configured authentication manager instead of implementing password verification inside the controller. It connects the controller to the standard security authentication flow.

### Access rules

The filter chain applies these rules:

| Path | Current access rule |
| --- | --- |
| `/api/auth/**` | Public, so users can register and log in. |
| `/actuator/**` | Public, typically for health and monitoring endpoints. |
| Everything else | Authentication required. |

CSRF protection is disabled and HTTP Basic authentication is enabled. Disabling CSRF is common for a stateless JSON API, but it should be an intentional choice based on how browsers and cookies are used.

## 8. JWT Concepts

A JWT is a compact, URL-safe string containing claims and a cryptographic signature. It normally has three Base64URL-encoded parts separated by periods:

```text
header.payload.signature
```

For example, the decoded conceptual content looks like this:

```json
// Header
{ "alg": "HS256", "typ": "JWT" }

// Payload (claims)
{
  "sub": "ada@example.com",
  "iat": 1790000000,
  "exp": 1790086400
}
```

The payload is encoded, not encrypted. Anyone holding the token can decode it, so a JWT must never contain a password, secret key, credit-card number, or other sensitive data.

### Important JWT terms

| Term | Meaning in this project |
| --- | --- |
| Claim | A piece of token data. The email is stored as the `sub` (subject) claim. |
| `iat` | Issued-at time. It records when the token was created. |
| `exp` | Expiration time. It limits how long the token can be accepted. |
| Secret key | Shared private value used to sign and verify an HS256 token. |
| Signature | Proof that the header and payload have not been changed by someone without the secret. |
| HS256 | HMAC with SHA-256, a symmetric signing algorithm. The same secret signs and verifies. |

JWTs prove that a trusted signer created the token and that its contents have not changed. They do **not** prove that the person currently presenting the token is the original user, which is why HTTPS and careful client-side token handling matter.

## 9. How `JwtProvider` Creates and Validates Tokens

The shared `JwtProvider` is in the `common` module so multiple services can use exactly the same token format and signing rules.

### Token creation

`generateToken(subject)` builds a token with:

- `subject`: the email passed from the successful login
- `issuedAt`: the current time
- `expiration`: 24 hours after creation
- `HS256` signature: calculated from the configured secret

The expiry duration is:

$$
24 \text{ hours} = 24 \times 60 \times 60 \times 1000 \text{ milliseconds}
$$

### Token validation

`validateAndGetClaims(token)` parses the compact token while supplying the same signing key. The JWT library verifies the signature and validates time-based claims such as expiration. If validation succeeds, it returns `Claims`, from which a service can read the subject:

```java
Claims claims = jwtProvider.validateAndGetClaims(token);
String email = claims.getSubject();
```

An altered token, token signed with another secret, malformed token, or expired token causes the parser to throw a JWT exception. A consuming service should turn that into an HTTP `401 Unauthorized` response.

## 10. Sending a JWT to a Protected Endpoint

After login, a typical client sends the token in the HTTP `Authorization` header:

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

A protected service normally needs a filter that:

1. Reads the `Authorization` header.
2. Confirms it begins with `Bearer `.
3. Extracts the token.
4. Calls `validateAndGetClaims`.
5. Creates an authenticated Spring Security object using the subject and appropriate authorities.
6. Rejects invalid or expired tokens with `401 Unauthorized`.

### What this auth service currently does not do

`auth-service` registers `JwtFilter` before Spring Security's username/password filter. It reads `Authorization: Bearer <token>`, uses `JwtProvider` to validate the signature and expiry, and puts the email subject into Spring Security's authentication context. Invalid or expired bearer tokens receive HTTP `401 Unauthorized`.

The `JwtUtil` class in the auth service is not used by `AuthController`; the active login flow uses the shared `common` module's `JwtProvider`.

## 11. Configuration and Secrets

The service runs on port `8080`. Its configured development secret is in [auth-service/src/main/resources/application.yml](auth-service/src/main/resources/application.yml):

```yaml
jwt:
  secret: 0123456789abcdef0123456789abcdef
```

`JwtProvider` requires at least 32 characters for an HS256 key. It falls back to the same default value if the provided value is absent or shorter than 32 characters.

That visible value is acceptable only for local learning and development. In a deployed application:

- Generate a strong random secret.
- Keep it outside source control, for example in a secret manager or injected environment variable.
- Configure every service that validates tokens with the correct secret or migrate to an asymmetric signing design.
- Rotate secrets with a planned rollout strategy.

If an attacker obtains this secret, they can create tokens that the services will accept as valid.

## 12. Roles and Authentication vs Authorization

Authentication asks: "Who is this user?" Login and password verification handle authentication.

Authorization asks: "What is this authenticated user allowed to do?" The entity's `role` field and `ROLE_` authority convention support authorization. A `role` value of `USER` becomes the Spring authority `ROLE_USER` in `JpaUserDetailsService`.

At present, the JWT contains only the email subject; it does not contain the role. A service using only the token therefore needs either to load roles separately or the design must be expanded to include and safely trust role claims.

## 13. Running and Trying the Service

From the workspace root, start the service with Maven:

```powershell
mvn -pl auth-service -am spring-boot:run
```

Register a user:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/register `
  -ContentType 'application/json' `
  -Body '{"email":"ada@example.com","password":"correct-horse-battery-staple"}'
```

Then log in:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login `
  -ContentType 'application/json' `
  -Body '{"email":"ada@example.com","password":"correct-horse-battery-staple"}'
```

The login response contains the token. A new application run uses a fresh in-memory H2 database, so register the user again after a restart.

## 14. Tests and Current Gaps

[auth-service/src/test/java/com/example/auth/UserRepositoryTest.java](auth-service/src/test/java/com/example/auth/UserRepositoryTest.java) is a JPA repository test using H2. It is intended to save a user and then find that user by email.

The service now includes several production-oriented foundations:

- `@Valid`, `@Email`, and `@NotBlank` checks reject malformed email/password input before registration or login logic runs.
- `ApiExceptionHandler` returns a consistent JSON error body containing `timestamp`, `status`, `message`, and field-level `errors` for validation failures.
- `JwtFilter` authenticates valid bearer tokens on protected routes; `GET /api/auth/me` demonstrates this flow.
- `AuthControllerIntegrationTest` covers registration, duplicate registration, successful login, rejected login, validation failure, and authenticated bearer access. `JwtProviderTest` verifies generated-token validation and expired-token rejection.

Production work should also consider:

- A persistent production database and migrations.
- Rate limiting or lockout controls for repeated login attempts.
- HTTPS everywhere tokens or passwords travel.
- Shorter-lived access tokens plus a deliberate refresh-token and logout/revocation strategy, if the product requires it.

## 15. Mental Model to Keep

Use this chain when reading the code:

```text
register: raw password -> BCrypt hash -> database
login: raw password -> Spring Security/BCrypt comparison -> signed JWT
protected request: Bearer JWT -> signature and expiry validation -> authenticated user
```

The database protects the password by storing a hash. The JWT lets another service verify a recent login without receiving the password or querying the auth database for every request. Both are necessary, but they solve different problems.