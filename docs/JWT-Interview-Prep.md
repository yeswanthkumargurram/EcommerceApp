JWT Interview Prep — Concepts, Answers, and Repo Mapping

Goal: concise interview-ready notes about JWTs, how your repo uses them, common pitfalls, and hands-on exercises you can run.

1) Key concepts (short answers you should remember)
- What is a JWT?: A compact, URL-safe token representing claims (JSON). Format: header.payload.signature. Signed so receivers can verify integrity.
- Why use JWTs in microservices?: Stateless authentication, no central session store; services can verify token locally.
- Signature types: HS256 (HMAC with shared secret) vs RS256/ES256 (asymmetric, private key signs, public key verifies).
- Standard claims: `iss` (issuer), `sub` (subject), `aud` (audience), `exp` (expiry), `iat` (issued at), `nbf` (not before), `jti` (id).

2) How validation works (step-by-step)
- Extract token from `Authorization: Bearer <token>`.
- Verify signature using secret (HMAC) or public key (RSA/ECDSA).
- Check `exp`/`nbf` and optionally `iat` (allow small clock skew).
- Confirm `iss` and `aud` if your system sets them.
- Optionally check `jti` against a revocation list.

3) Common interview questions and short model answers
- Q: How does a service verify a JWT? A: It parses the token, verifies the signature with a key, and checks claims like expiry and audience.
- Q: When to use HMAC vs RSA? A: Use HMAC for simplicity in small deployments where you can safely share a secret; use RSA (public/private) for multi-service deployments to avoid sharing private keys. RSA allows distributing public keys (JWKS) to verifiers.
- Q: How do you revoke a JWT? A: Short tokens + refresh tokens is preferred. For immediate revocation, use a blacklist keyed by `jti` or maintain a token introspection endpoint.
- Q: Are JWTs encrypted? A: Not by default. JWT payload is base64url-encoded and readable. Use JWE for encryption if payload secrecy is required.
- Q: What are pitfalls? A: Long-lived tokens, storing sensitive data in payload, not validating `aud`/`iss`, using weak secrets, exposing keys.

4) How your repo implements tokens (mapping to files)
- Token creation & validation shared library: `common/src/main/java/com/example/common/security/JwtProvider.java` — builds tokens and validates them using an HMAC secret.
- Auth controller uses shared provider: `auth-service/src/main/java/com/example/auth/web/AuthController.java` calls `JwtProvider.generateToken(...)`.
- JwtFilter in services (example): `product-service/src/main/java/com/example/product/security/JwtFilter.java` — extracts token, calls `JwtProvider.validateAndGetClaims(token)`, sets Spring Security `Authentication` with `sub` as principal.
- Configured shared secret: each service defines a `JwtProvider` bean in its `SecurityConfig` with `@Value("${jwt.secret:0123456789abcdef0123456789abcdef}")` so you can set `JWT_SECRET` as env var to share the same key across services.
- Note: `auth-service/src/main/java/com/example/auth/security/JwtUtil.java` exists but is not used by the controller; the active flow uses `JwtProvider` from `common`.

5) Practical interview exercises (do these locally)
- Exercise A — Verify token flow end-to-end:
  1. Start `auth-service` and `product-service`.
  2. Register/login via `auth-service` to get a JWT.
  3. Call protected `product-service` endpoint with `Authorization: Bearer <token>` and confirm access.
- Exercise B — Break it intentionally:
  1. Change `jwt.secret` in `product-service` to a different value and confirm requests fail with 401.
  2. Shorten token expiry in `JwtProvider` to 5s and observe expired-token rejection.
- Exercise C — Add role claims:
  1. Modify `JwtProvider.generateToken` to add `.claim("roles", List.of("USER"))`.
  2. Update `JwtFilter` to read `roles` claim and map to `GrantedAuthority` instead of hardcoding `ROLE_USER`.

6) Example interview whiteboard answers (short patterns)
- Token propagation patterns: "pass-through", "token exchange", "gateway validation + context propagation" — pros/cons of each.
- Key rotation: publish public keys via JWKS, sign with rotating private keys; verifiers fetch and cache JWKS and handle key IDs (`kid`).

7) Quick study checklist (what to memorize)
- JWT format and standard claims.
- Difference between signing vs encryption (JWS vs JWE).
- How to verify signature for HS256 vs RS256.
- Common header fields (`alg`, `typ`, `kid`).
- Typical security mitigations (short expiry, rotate keys, validate `aud`/`iss`).

8) Further reading & commands
- Read RFC 7519 (JWT) and RFC 7515 (JWS).
- Run tests for JWT provider (Maven):

```bash
mvn -pl common test -Dtest=com.example.auth.security.JwtProviderTest
```

9) If you want, I can:
- Create a short quiz (10 flashcards) and add to `docs/`.
- Implement Exercise C changes in the repo and open a PR.
- Add a simple sequence diagram illustrating the flow.

---

Tell me which of the three follow-ups above you want next: `quiz`, `implement-role-claims`, or `diagram`.