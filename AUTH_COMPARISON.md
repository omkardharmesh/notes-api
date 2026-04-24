# Auth System Comparison: notes-api vs STV Production

> notes-api = your learning monolith (Kotlin)
> STV = production microservices: gateway + authenticator + userservice (Java)

---

## 1. Architecture Overview

### notes-api (monolith)
```
Client
  └─▶ JwtAuthFilter (Spring Security)
        ├─ validate JWT locally (JwtService)
        ├─ check Redis blacklist (TokenBlacklistService)
        └─ set SecurityContext (userId as principal)
              └─▶ AuthController / NoteController (same app)
```

### STV Production (microservices)
```
Client
  └─▶ Gateway (Spring Cloud Gateway)
        └─ AuthenticationFilter
              ├─ rate limit check (Resilience4j)
              ├─ HTTP call → Authenticator /authenticate
              │     ├─ Redis blacklist check
              │     ├─ JWT decode (multi-key rotation)
              │     ├─ DB lookup (SessionToken table)
              │     ├─ blocked user check
              │     └─ subscription + mandate status
              └─ inject headers (userId, role, subStatus, ...) into request
                    └─▶ Userservice / Feedservice / Payments / Search
```

Key difference: notes-api validates JWT **in-process**. STV validates JWT **remotely** — the gateway outsources auth to a dedicated service. Every authenticated request = 1 extra HTTP hop to the authenticator.

---

## 2. JWT Token Design

| Dimension | notes-api | STV Authenticator |
|-----------|-----------|-------------------|
| Algorithm | HMAC-SHA256 | HMAC-SHA256 |
| Library | JJWT 0.12.6 | JJWT 0.12.6 |
| Subject | `userId` (Long) | `userId` (String) |
| Extra claims | `deviceId` | `role`, `deviceId`, `regDate`, `createdDate` |
| Access token expiry | **1 hour** | **3 days** |
| Refresh token expiry | 30 days | No separate refresh — 3-day JWT is reused |
| Key storage | Base64-decoded bytes (safe) | Raw UTF-8 bytes (risk: short secrets = weak key) |
| Key rotation | Single key only | **Primary + list of decryption keys** (backward compat) |
| Key source | `application.yml` → `app.jwt.secret` | AWS Secrets Manager → `access_token_secret` + `decryption_access_token_secrets` |

### notes-api token generation
```kotlin
fun generateAccessToken(userId: Long, deviceId: String): String =
    Jwts.builder()
        .subject(userId.toString())
        .claim("deviceId", deviceId)
        .signWith(key)
        .expiration(Date(System.currentTimeMillis() + accessTokenExpiration))
        .compact()
```

### STV token generation
```java
public String generateToken(String userId, String role, String deviceId,
                             String regDate, String createdDate) {
    return Jwts.builder()
        .claim("role", role)
        .claim("deviceId", deviceId)
        .claim("regDate", regDate)
        .claim("createdDate", createdDate)
        .setSubject(userId)
        .setIssuedAt(new Date())
        .signWith(primarySecretKey)
        .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
        .compact();
}

// Key rotation: tries each decryption key on parse failure
for (Key key : decryptionSecretKeys) { ... }
```

**Winner:** notes-api has shorter access tokens (safer). STV has key rotation (prod-essential). Migration needs both.

---

## 3. Token Storage & Refresh Strategy

### notes-api
- **RefreshToken entity** stored in DB:
  ```kotlin
  @Entity data class RefreshToken(
      val user: User,         // FK
      val deviceId: String,   // per-device
      val hashedToken: String, // SHA-256 hash of raw token
      val expiresAt: Instant,
      val createdAt: Instant,
  )
  ```
- **Refresh flow:**
  1. Extract userId from refresh token JWT
  2. Find stored RefreshToken by user + deviceId
  3. SHA-256 hash incoming token → compare with stored hash
  4. Check expiry
  5. **Rotate**: delete old token, issue new access + refresh pair
  6. Return new tokens

- **Multi-device**: one RefreshToken row per deviceId → logout one device, others intact

### STV Authenticator
- **SessionToken entity** (table: `device_tokens`):
  ```java
  // stores raw jwttoken + refreshtoken per device
  deviceid, jwttoken, refreshtoken, status, fcmtoken, usersessionid
  ```
- **No refresh endpoint** found in authenticator — userservice handles token generation via OTPless/TrueCaller login
- 3-day JWT means users stay "logged in" much longer without refresh
- Session invalidation via status field on SessionToken row

**Winner:** notes-api refresh model is cleaner and safer (short-lived tokens + rotation + hash storage). STV stores raw tokens in DB (security risk if DB is breached).

---

## 4. JWT Validation Filter

### notes-api — JwtAuthFilter
```kotlin
// Simple, local, fast
class JwtAuthFilter : OncePerRequestFilter() {
    override fun doFilterInternal(...) {
        val token = authHeader.removePrefix("Bearer ")
        if (jwtService.isTokenValid(token) && !tokenBlacklistService.isBlacklisted(token)) {
            val userId = jwtService.extractUserId(token)
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(userId, null, emptyList())
        }
        filterChain.doFilter(request, response)
    }
}
```
- Local validation (no network hop)
- Principal = `userId` (Long) — simple
- No roles in SecurityContext

### STV Authenticator — AuthenticationFilter (on the authenticator service itself)
```java
// Guards the /authenticate endpoint
// Full validation chain:
class AuthenticationFilter : OncePerRequestFilter() {
    override fun doFilterInternal(...) {
        // 1. Extract Bearer token
        // 2. Check Redis blacklist (Set: BLACKLISTED_JWT)
        // 3. Decode JWT (try primary key, fallback to rotation keys)
        // 4. Check expiry
        // 5. DB lookup: SessionToken active + jwttoken matches
        // 6. Check users.blocked flag
        // 7. Set SecurityContext with ROLE_USER
    }
}
```

### STV Gateway — AuthenticationFilter (the real auth gate)
```java
// Gateway calls authenticator via HTTP for every request
UserDTO userDTO = getUserDto(token, endpointPath);
// Injects into downstream request:
writeableHeaders.set("userId", userDTO.getUserId());
writeableHeaders.set("role", userDTO.getRole());
writeableHeaders.set("subState", userDTO.getSubStatus());
writeableHeaders.set("mandStatus", userDTO.getMandStatus());
writeableHeaders.set("userLangId", userDTO.getUserLangId());
writeableHeaders.set("sessionId", userDTO.getSessionId());
// ... etc
```

**Key difference:** notes-api auth = pure JWT (stateless). STV auth = JWT + DB check (stateful — every request hits DB to validate session). STV can revoke sessions instantly; notes-api can only blacklist tokens in Redis.

---

## 5. Token Blacklist (Logout)

### notes-api
```kotlin
// Key: "blacklist:<full_token>"
// TTL: exact remaining ms until token expires
fun blacklist(token: String, expirationMs: Long) {
    redisTemplate.opsForValue().set("blacklist:$token", "true", expirationMs, TimeUnit.MILLISECONDS)
}
fun isBlacklisted(token: String): Boolean = redisTemplate.hasKey("blacklist:$token")
```
- Stores full token as Redis key
- Auto-expires when token would have expired anyway
- Single Redis template (no master/replica split)
- Called from `AuthController.logout()` — controller extracts token, gets expiry, blacklists

### STV Authenticator
```java
// Key: Redis Set "BLACKLISTED_JWT" — token as set member
public boolean checkBlacklistedJwt(String jwtToken) {
    String BlacklistedTokenKey = "BLACKLISTED_JWT";
    return redisDevService.isElementInSet(BlacklistedTokenKey, jwtToken);
}
```
- All blacklisted tokens in **one Redis Set** (no TTL per token)
- Set grows forever unless manually pruned — **memory leak risk**
- Separate read/write templates (master + replica for HA)

**Winner:** notes-api blacklist is correct (auto-expiry per token). STV blacklist is a bug — tokens never expire from the Set. Migration: use notes-api approach with master/replica from STV.

---

## 6. Auth Endpoints

### notes-api (bundled in monolith)
| Method | Path | What it does |
|--------|------|-------------|
| POST | /auth/register | Create user (email + password + deviceId) → return access + refresh tokens |
| POST | /auth/login | Verify email + password → return access + refresh tokens |
| POST | /auth/refresh | Rotate refresh token by deviceId → return new access + refresh tokens |
| POST | /auth/logout | Blacklist access token in Redis |

### STV Production (split across services)
| Method | Path | Service | What it does |
|--------|------|---------|-------------|
| POST | /userservice/v1/otpless/verify | userservice | Mobile OTP verify → generate JWT |
| POST | /userservice/v1/truecaller/verify | userservice | TrueCaller verify → generate JWT |
| POST | /userservice/v1/jwt/generate | userservice | Generate JWT (internal) |
| POST | /userservice/v1/logout | userservice | Logout (blacklist) |
| GET | /authenticator/v1/authenticate | authenticator | Internal: validate JWT → return UserDTO |

No password-based auth in STV — mobile-first (OTP). No refresh token rotation endpoint — 3-day JWT used directly.

---

## 7. Security Config (Spring Security)

### notes-api
```kotlin
http
    .csrf { it.disable() }
    .authorizeHttpRequests {
        it.requestMatchers("/auth/register", "/auth/login", "/auth/refresh").permitAll()
        it.anyRequest().authenticated()
    }
    .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
    .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
```

### STV Authenticator
```java
// Permits only health endpoints
// All other requests need valid JWT (guards the /authenticate endpoint itself)
// CORS: wildcard origins, POST/GET/PUT/DELETE/OPTIONS
// Custom AuthenticationEntryPoint: returns GenericResponseDTO on 401
// CORSFilter registered as filter
```

### STV Gateway
No Spring Security — custom `AbstractGatewayFilterFactory` (reactive, not servlet-based).

---

## 8. What notes-api Got Right ✅

1. **Short-lived access tokens** (1hr) — industry standard, limits exposure window
2. **Refresh token rotation** — hash stored (not raw token), per-device, rotated on use
3. **Redis blacklist with auto-expiry** — correct TTL-based approach, no memory leak
4. **Clean separation**: JwtService → only JWT ops, AuthService → business logic, TokenBlacklistService → Redis ops
5. **Spring Security properly wired** — `permitAll` + STATELESS + filter chain
6. **Password hashing**: BCrypt (strong, salted)
7. **Global exception handler**: `@RestControllerAdvice` with typed exceptions
8. **`BaseResponse<T>` envelope**: consistent across all endpoints
9. **Multi-device refresh**: one RefreshToken row per user+device

---

## 9. What STV Production Has That notes-api Lacks ❌

1. **JWT key rotation** — multiple decryption secrets, backward compatible rolling key updates
2. **Stateful session validation** — JWT matched against DB (SessionToken), sessions revocable server-side instantly
3. **Subscription/mandate in auth** — every request checks if user is eligible (not just authenticated)
4. **Role-based access** — `UsersRoles` table, ROLE_USER in SecurityContext
5. **Multi-key Redis** — master/replica split for read performance and HA
6. **Mobile-first auth** — OTPless + TrueCaller (no password to breach)
7. **AWS Secrets Manager** — credentials never in source code or env files
8. **CI/CD pipeline** — GitHub Actions → ECR → ECS
9. **OpenTelemetry** — distributed tracing across services
10. **Gateway-level auth** — all services protected without each implementing auth

---

## 10. What STV Production Got Wrong ⚠️

1. **3-day access tokens** — if compromised, attacker has 3 days. Should be 1hr max.
2. **Redis blacklist memory leak** — `BLACKLISTED_JWT` Set never pruned, grows forever
3. **Raw token stored in SessionToken DB** — if DB breached, all active tokens exposed. Should hash like notes-api.
4. **No refresh token rotation** — reissue same long-lived JWT instead of short-lived + rotate pattern
5. **Inter-service HTTP hop on every request** — gateway → authenticator on every authenticated call, no caching
6. **Zero test coverage** — same as notes-api but worse because prod system
7. **Hardcoded ELB URLs** — no service discovery
8. **No Flyway/Liquibase** — schema managed manually

---

## 11. Migration Target: Best of Both

For the new Kotlin microservices:

### Token Design
- Access token: **1 hour** (from notes-api)
- Refresh token: **30 days**, stored as **SHA-256 hash** per device (from notes-api)
- JWT claims: `userId`, `role`, `deviceId`, `sessionId` (from STV, minus verbose createdDate/regDate)
- **Key rotation**: list of decryption secrets loaded from AWS Secrets Manager (from STV)

### Auth Flow (new authenticator service in Kotlin)
```
Login (OTPless/TrueCaller) → generate 1hr access token + 30day refresh token
                           → store hashed refresh token in DB (per device)
                           → cache sessionId→userId in Redis (TTL=1hr)

Every request → Gateway calls authenticator /authenticate
             → authenticator: check Redis cache first (fast path)
             → if cache miss: decode JWT → check DB session active → check subscription
             → inject headers downstream

Refresh → validate hashed refresh token → rotate (delete old, issue new pair)

Logout → blacklist access token in Redis with TTL (notes-api approach)
       → delete/deactivate RefreshToken row
```

### Redis Strategy
- Master/replica split (from STV)
- Blacklist: `blacklist:<token>` with TTL (from notes-api — fixes STV memory leak)
- Session cache: `session:<sessionId>` → UserValidDTO JSON (new — reduces DB hits)

### Security Config (Kotlin)
- Spring Security properly wired (from notes-api)
- CORS in SecurityConfig (from STV)
- `@RestControllerAdvice` global handler (from notes-api)
- `GenericResponse<T>` typed envelope (from notes-api `BaseResponse<T>`)
- AWS Secrets Manager injection at startup (from STV)
