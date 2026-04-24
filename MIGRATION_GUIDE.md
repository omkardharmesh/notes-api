# STV Kotlin Migration Guide
## 3 Java Services → Kotlin | notes-api as the Foundation

> **Purpose**: You built notes-api to learn the stack. Now use it to rewrite 3 production Java services in Kotlin.
> Each section maps what you learned → what exists → what to build.

---

## Reference Files Created (5 total)

| # | File | Location | What's Inside |
|---|------|----------|---------------|
| 1 | `MIGRATION_GUIDE.md` | `/Users/eloelo/Downloads/notes-api/` | **This file** — master guide, all 3 services, execution plan |
| 2 | `AUTH_COMPARISON.md` | `/Users/eloelo/Downloads/notes-api/` | Deep auth-only comparison: notes-api JWT/filter/blacklist vs authenticator, what's right/wrong in each |
| 3 | `ARCHITECTURE.md` | `/Users/eloelo/IntellijProjects/STV/authenticator/` | Authenticator architecture + comparison vs notes-api |
| 4 | `ARCHITECTURE.md` | `/Users/eloelo/IntellijProjects/STV/gateway/` | Gateway architecture + comparison vs notes-api |
| 5 | `ARCHITECTURE.md` | `/Users/eloelo/IntellijProjects/STV/userservice/` | Userservice architecture + comparison vs notes-api |

### Reading Order
1. `AUTH_COMPARISON.md` — understand how auth evolved from monolith to microservice
2. `STV/authenticator/ARCHITECTURE.md` — deep dive on production auth service
3. `STV/gateway/ARCHITECTURE.md` — understand the routing layer
4. `STV/userservice/ARCHITECTURE.md` — understand the domain/business layer
5. `MIGRATION_GUIDE.md` (this file) — use while actively building each service

---

---

## 0. The Big Picture

```
notes-api (WHAT YOU BUILT)          STV PRODUCTION (WHAT EXISTS)
────────────────────────────         ──────────────────────────────────────────
                                     ┌────────────┐
                                     │  GATEWAY   │ ← routes, rate limit, auth handoff
                                     └─────┬──────┘
                                           │ HTTP (every authenticated request)
┌──────────────────────────────┐    ┌──────▼──────┐
│         notes-api            │    │ AUTHENTICATOR│ ← JWT validate, session, subscription
│  ┌────────┐  ┌────────────┐  │    └─────┬───────┘
│  │  AUTH  │  │   NOTES    │  │          │ (internal only)
│  │register│  │ CRUD notes │  │    ┌─────▼───────┐
│  │ login  │  │ soft delete│  │    │ USERSERVICE  │ ← user profile, subscription, devices
│  │refresh │  │   Kafka    │  │    │              │   OTPless/TrueCaller login
│  │logout  │  │   events   │  │    │              │   watch history, experiments
│  └────────┘  └────────────┘  │    └─────────────┘
└──────────────────────────────┘
         1 service                      3 services
         Kotlin/Gradle                  Java/Maven
         Phase 1–8 complete             Production, Java 17, AWS
```

---

## 1. Tech Stack Diff

| Layer | notes-api (yours) | STV Production | Migration Target |
|-------|-------------------|---------------|-----------------|
| Language | **Kotlin** | Java 17 | **Kotlin** ✅ |
| Framework | Spring Boot 4.0.5 | Spring Boot 3.4.3 / 3.2.3 | Spring Boot 3.4.x |
| Build | **Gradle** (Kotlin DSL) | Maven | **Gradle** ✅ |
| Java target | 21 | 17 | 21 |
| DB | PostgreSQL + Hibernate | PostgreSQL + Hibernate | PostgreSQL + Hibernate |
| DB migrations | ❌ `ddl-auto: update` | ❌ Pre-created schema | **Flyway** (add new) |
| Caching | Redis (single) | Redis (master + replica, ElastiCache) | Redis master+replica |
| Messaging | Kafka (docker) | Kafka (AWS MSK) | Kafka (MSK) |
| Auth | Email+password+JWT | OTPless+TrueCaller+JWT | OTPless+TrueCaller+JWT |
| Security | Spring Security ✅ | Authenticator: ✅, Userservice: ❌ | Spring Security ✅ |
| Gateway | ❌ (monolith) | Spring Cloud Gateway | Spring Cloud Gateway |
| Secrets | application.yml | AWS Secrets Manager | AWS Secrets Manager |
| Observability | ❌ | OpenTelemetry (gRPC) | OpenTelemetry |
| CI/CD | ❌ | GitHub Actions → ECR → ECS | GitHub Actions → ECR → ECS |
| Tests | ❌ near zero | ❌ near zero | **Write them** |
| Package structure | Layer-first | Layer-first | **Feature-first** (Phase 8) |

---

## 2. Your Learning Phases → Where They Live in Production

| Phase | What You Learned | Lives In (Production) |
|-------|-----------------|----------------------|
| Phase 1 | Docker compose (PostgreSQL, Kafka, Redis) | AWS RDS + MSK + ElastiCache |
| Phase 2 | Spring Boot + Kotlin project setup, application.yml | All 3 services (Maven, Java — to be migrated) |
| Phase 3 | Entities, JPA, CRUD, DTOs, mappers, service layer | **Userservice** (Users, UserSession, Device, Subscription entities) |
| Phase 4 | JWT, Spring Security, BCrypt, refresh tokens, filter chain | **Authenticator** (JWT, Spring Security, SessionToken, Redis blacklist) |
| Phase 5 | Redis caching, token blacklist | **Authenticator** (Redis blacklist, session cache) |
| Phase 6 | Kafka producer/consumer, events | **Userservice** (Kafka MSK events) |
| Phase 7 | Docker, AWS ECS, ECR, RDS, ALB, security groups | **All 3 services** (CI/CD already set up) |
| Phase 8 | Feature-based packaging | **Not done in any service** — implement in migration |

---

## 3. SERVICE 1: GATEWAY MIGRATION

### What It Does (Production)
- Single entry point for all client requests
- Rate limiting (Resilience4j: 200k req/s)
- JWT validation by calling authenticator (`/authenticate`)
- Injects user context headers (`userId`, `role`, `subStatus`, etc.) into downstream requests
- Blocks `/internal/**` paths (403)
- App version enforcement
- Routes to: userservice, feedservice, payments, searchservice, analytics

### What You Learned That Maps Here
- **Phase 4** filter chain → Gateway's `AuthenticationFilter` is the same concept, but reactive (WebFlux) and routes to a remote service instead of local validation
- **Phase 7** AWS → ALB in front of gateway, ECS for deployment
- **Phase 2** application.yml → routes defined in properties file

### What notes-api Doesn't Cover (New for You)
- Spring Cloud Gateway (WebFlux-based, reactive) — different from Spring MVC
- `AbstractGatewayFilterFactory` — reactive filter instead of `OncePerRequestFilter`
- `ServerWebExchange` instead of `HttpServletRequest`/`HttpServletResponse`
- Route configuration DSL (predicates + filters)
- Resilience4j rate limiting
- `ServerHttpRequestDecorator` to mutate requests

### Migration Plan: Gateway in Kotlin

**Stack:**
```kotlin
// build.gradle.kts
implementation("org.springframework.cloud:spring-cloud-starter-gateway")
implementation("io.github.resilience4j:resilience4j-spring-boot3:2.1.0")
implementation("software.amazon.awssdk:secretsmanager:2.x")
implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")
```

**Keep from existing gateway:**
- Route config structure (properties-based, profile-per-env)
- Public routes pattern: authenticator/**, otpless/verify, truecaller/verify → no auth
- AuthenticationFilter → call authenticator, inject headers
- Rate limiter config (200k/s, 500ms timeout)
- AWS Secrets Manager injection at startup
- `/internal/**` block → 403
- App version check logic
- Health endpoint

**Improve in Kotlin:**
```kotlin
// Replace hardcoded ELBs with Spring Cloud LoadBalancer
// application.yml (Kotlin migration)
spring:
  cloud:
    gateway:
      routes:
        - id: userservice
          uri: lb://userservice        # service discovery, not hardcoded ELB
          predicates:
            - Path=/userservice/**
          filters:
            - RemoveRequestHeader=userId
            - AuthenticationFilter

// Add circuit breaker per route
- name: CircuitBreaker
  args:
    name: userservice
    fallbackUri: forward:/fallback/userservice
```

**New AuthenticationFilter (Kotlin, reactive):**
```kotlin
@Component
class AuthenticationFilter(
    private val webClient: WebClient,
    private val rateLimiter: RateLimiter,
) : AbstractGatewayFilterFactory<AuthenticationFilter.Config>(Config::class.java) {

    override fun apply(config: Config): GatewayFilter = GatewayFilter { exchange, chain ->
        val token = exchange.request.headers.getFirst("Authorization")
            ?.removePrefix("Bearer ") ?: return@GatewayFilter handleUnauthorized(exchange)

        if (!rateLimiter.acquirePermission()) return@GatewayFilter handleRateLimited(exchange)

        webClient.get()
            .uri("$authenticatorUrl/authenticate")
            .header("Authorization", "Bearer $token")
            .retrieve()
            .bodyToMono(UserDTO::class.java)
            .flatMap { userDTO ->
                val mutatedRequest = exchange.request.mutate()
                    .header("userId", userDTO.userId)
                    .header("role", userDTO.role)
                    .header("subState", userDTO.subStatus)
                    .build()
                chain.filter(exchange.mutate().request(mutatedRequest).build())
            }
            .onErrorResume { handleUnauthorized(exchange) }
    }
}
```

**Add (neither has):**
- Circuit breaker per route (Resilience4j)
- Per-route rate limiting (not global)
- Request/response logging filter with MDC trace ID
- JWT caching at gateway (avoid authenticator call every request → cache `sessionId → UserDTO` in Redis, TTL=1min)

---

## 4. SERVICE 2: AUTHENTICATOR MIGRATION

### What It Does (Production)
- **One endpoint**: `GET /authenticator/v1/authenticate`
- Called by gateway on every authenticated request
- Full validation chain: Redis blacklist → JWT decode (multi-key rotation) → DB session lookup → blocked check → subscription/mandate status
- Returns `UserValidDTO` (userId, role, subStatus, mandStatus, sessionId, deviceId, etc.)
- Spring Security guards its own `/authenticate` endpoint
- Redis master+replica for blacklist + session cache

### What You Learned That Maps Here (Very Directly)
| notes-api | Authenticator |
|-----------|--------------|
| `JwtService.generateAccessToken()` | `JwtService.generateToken()` |
| `JwtAuthFilter` (OncePerRequestFilter) | `AuthenticationFilter` (OncePerRequestFilter) |
| `TokenBlacklistService` | `RedisDevService.isElementInSet()` |
| `SecurityConfig` + `permitAll` | `SecurityConfig` + permit /health |
| `RefreshToken` entity | `SessionToken` entity |
| `BaseResponse<T>` | `GenericResponseDTO` |

**You already know how to build this.** The concepts are identical. Your version is cleaner.

### What Authenticator Has That You Need to Add
1. **JWT key rotation** — multiple decryption secrets, try sequentially on parse failure
2. **DB session validation** — JWT matched against `SessionToken.jwttoken` row (stateful)
3. **Subscription + mandate status in auth response** — `UserValidDTO` includes `subStatus`, `mandStatus`
4. **Role-based** — `UsersRoles` table, `ROLE_USER` in SecurityContext
5. **Redis master+replica** — separate write/read templates
6. **AWS Secrets Manager** — no secrets in application.yml

### What notes-api Got Right That Authenticator Got Wrong
1. **1hr access tokens** (authenticator uses 3 days — fix this)
2. **Redis blacklist with TTL** (authenticator has a memory leak — `BLACKLISTED_JWT` Set never pruned)
3. **Hashed refresh tokens** (authenticator stores raw `jwttoken` in DB — security hole)
4. **Refresh rotation** (authenticator has no refresh rotation endpoint)
5. **`@RestControllerAdvice`** global exception handler (authenticator handles only in filter)
6. **Base64-decoded key** (authenticator uses raw UTF-8 bytes — weak if secret is short)

### Migration Plan: Authenticator in Kotlin

**Entities:**
```kotlin
// Keep from production, write in Kotlin
@Entity @Table(name = "user_session")
data class UserSession(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
    @ManyToOne @JoinColumn(name = "user_id") val user: Users,
    val deviceId: String,
    val deviceType: String,
    val lastLoginTime: Long,
    val logInType: String,
    var active: Boolean,
)

// Fix: store HASHED token (from notes-api), not raw token
@Entity @Table(name = "device_tokens")
data class SessionToken(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
    val deviceId: String,
    val hashedJwtToken: String,    // SHA-256 hash, NOT raw token
    val hashedRefreshToken: String, // SHA-256 hash
    val status: String,
    val fcmToken: String?,
    @ManyToOne @JoinColumn(name = "user_session_id") val userSession: UserSession,
)
```

**JwtService (Kotlin + key rotation):**
```kotlin
@Service
class JwtService(
    @Value("\${auth.jwt.primary-secret}") primarySecret: String,
    @Value("\${auth.jwt.decryption-secrets}") decryptionSecrets: String, // comma-separated
    @Value("\${auth.jwt.access-expiry-ms}") private val accessExpiryMs: Long,    // 3_600_000 (1hr)
    @Value("\${auth.jwt.refresh-expiry-ms}") private val refreshExpiryMs: Long,  // 2_592_000_000 (30d)
) {
    private val primaryKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(primarySecret))
    private val decryptionKeys = decryptionSecrets.split(",")
        .map { Keys.hmacShaKeyFor(Decoders.BASE64.decode(it.trim())) }

    fun generateAccessToken(userId: Long, role: String, deviceId: String, sessionId: Long): String =
        Jwts.builder()
            .subject(userId.toString())
            .claim("role", role)
            .claim("deviceId", deviceId)
            .claim("sessionId", sessionId.toString())
            .expiration(Date(System.currentTimeMillis() + accessExpiryMs))
            .signWith(primaryKey)
            .compact()

    fun parseClaims(token: String): Claims {
        // Try primary key first, fall back to rotation keys
        val allKeys = listOf(primaryKey) + decryptionKeys
        for (key in allKeys) {
            try {
                return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
            } catch (e: JwtException) { continue }
        }
        throw InvalidTokenException("Token invalid against all known keys")
    }
}
```

**Redis blacklist (fixed TTL — from notes-api pattern):**
```kotlin
@Service
class TokenBlacklistService(
    private val redisWriteTemplate: StringRedisTemplate,
    private val redisReadTemplate: StringRedisTemplate,
) {
    // Fix: key per token with TTL (not a Set — no memory leak)
    fun blacklist(token: String, remainingMs: Long) =
        redisWriteTemplate.opsForValue().set("blacklist:$token", "1", remainingMs, TimeUnit.MILLISECONDS)

    fun isBlacklisted(token: String): Boolean =
        redisReadTemplate.hasKey("blacklist:$token") == true
}
```

**AuthenticationService — full validation chain:**
```kotlin
fun authenticate(token: String): UserValidDTO {
    if (tokenBlacklistService.isBlacklisted(token)) throw BlacklistedTokenException()

    val claims = jwtService.parseClaims(token)   // handles key rotation
    val userId = claims.subject.toLong()
    val sessionId = claims["sessionId"].toString().toLong()
    val deviceId = claims["deviceId"].toString()

    // DB: session still active + token hash matches
    val sessionToken = sessionTokenRepository.findByUserSessionId(sessionId)
        ?: throw InvalidSessionException()
    if (!sessionToken.userSession.active) throw InvalidSessionException()
    val incomingHash = sha256(token)
    if (incomingHash != sessionToken.hashedJwtToken) throw InvalidTokenException()

    // User not blocked
    val user = usersRepository.findById(userId).orElseThrow { InvalidSessionException() }
    if (user.blocked) throw UserBlockedException()

    // Subscription + mandate
    val subStatus = resolveSubscriptionStatus(userId)
    val mandStatus = resolveMandateStatus(userId)

    return UserValidDTO(userId, sessionId, deviceId, subStatus, mandStatus, ...)
}
```

---

## 5. SERVICE 3: USERSERVICE MIGRATION

### What It Does (Production)
- User registration via OTPless (mobile OTP) and TrueCaller
- JWT generation after login (delegates token ops to JwtService)
- User profile (name, language, avatar)
- Subscription plans + status
- Device management (FCM tokens, platform)
- Watch history
- Feature experiments (A/B)
- Freshdesk (customer support)
- Account deletion
- Kafka events via AWS MSK
- No Spring Security — trusts `userId` header injected by gateway

### What You Learned That Maps Here

| notes-api concept | Userservice equivalent |
|-------------------|----------------------|
| `User` entity | `Users` entity (extended — 11 fields) |
| `UserRepository.findByEmail()` | `UsersRepository` (findByMobile, etc.) |
| `NoteService` (service layer) | `UserService`, `OtplessService`, `TrueCallerService` |
| `NoteController` (reads `userId` from SecurityContext) | `UsersController` (reads `userId` from header) |
| `BaseResponse<T>` envelope | `GenericResponseDTO` |
| Kafka `NoteCreatedEvent` producer | `KafkaService` (MSK events) |
| `@Valid` on DTOs | `@Valid` on request DTOs (30+ DTOs) |
| Soft delete on Note | `DeletedAccounts` entity (separate table) |

**Critical difference**: notes-api reads `userId` from `SecurityContextHolder` (JWT validated locally). Userservice reads `userId` from HTTP header (JWT validated by gateway → authenticator, not locally). No Spring Security in userservice.

### What Userservice Has That notes-api Doesn't
1. **OTPless + TrueCaller auth** — no username/password
2. **Full subscription domain** — `UserSubscription`, `Mandate`, `Transactions` entities
3. **Device management** — FCM tokens, platform-specific handling, multi-device
4. **Watch history** — `StreamSession`, `WatchShowList`
5. **Feature experiments** — `UserExperimentService` (A/B variants)
6. **Freshdesk integration** — customer support ticketing
7. **AWS S3, Lambda, Secrets Manager** — production infra

### What Userservice Got Wrong
1. **No Spring Security** — trusts headers, fine behind gateway but risky if service is ever exposed
2. **Java verbose code** — Kotlin reduces by ~40%
3. **`@Autowired` field injection** — use constructor injection (testable)
4. **Try-catch in controllers** — use `@RestControllerAdvice`
5. **30+ DTOs with no grouping** — organize by feature package (Phase 8)
6. **No tests** — add unit tests for `UserService`, `OtplessService`

### Migration Plan: Userservice in Kotlin

**Package structure (feature-first — Phase 8):**
```
com.storytv.userservice/
├── auth/
│   ├── OtplessController.kt
│   ├── OtplessService.kt
│   ├── TrueCallerService.kt
│   └── dto/
├── user/
│   ├── UserController.kt
│   ├── UserService.kt
│   ├── User.kt (entity)
│   └── dto/
├── subscription/
│   ├── SubscriptionController.kt
│   ├── SubscriptionService.kt
│   ├── UserSubscription.kt
│   ├── Mandate.kt
│   └── dto/
├── device/
│   ├── DeviceController.kt
│   ├── DeviceService.kt
│   └── Device.kt
├── watchhistory/
│   ├── WatchHistoryController.kt
│   ├── WatchHistoryService.kt
│   └── StreamSession.kt
├── experiment/
│   └── ExperimentService.kt
├── support/
│   └── FreshdeskService.kt
├── kafka/
│   └── UserKafkaService.kt
└── shared/
    ├── ApiResponse.kt       (BaseResponse<T> from notes-api)
    └── GlobalExceptionHandler.kt
```

**Header-based userId (no Spring Security needed):**
```kotlin
// Controller reads userId from header (injected by gateway)
@GetMapping("/profile")
fun getProfile(
    @RequestHeader("userId") userId: Long,
    @RequestHeader("subState") subState: String,
): ApiResponse<UserProfileDTO> = ApiResponse.ok(userService.getProfile(userId, subState))
```

**Kotlin entity (vs Java Lombok):**
```kotlin
@Entity @Table(name = "users")
data class Users(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
    val username: String,
    val mobile: String,
    val email: String?,
    val onboardingLang: String?,
    val currentLang: String?,
    var blocked: Boolean = false,
    val createdDate: Instant = Instant.now(),
    var updatedDate: Instant = Instant.now(),
)
```

---

## 6. LESSONS FROM LEARNING_ROADMAP → APPLY TO MIGRATION

### Phase 3 Lessons (Entities, DTOs)
| Mistake Made | Apply in Migration |
|-------------|-------------------|
| `NoteRequest` included `owner: User` | Never trust client-declared identity. Get `userId` from header (gateway-injected) |
| `NoteResponse` wrapped entire entity | Flatten DTOs — expose only what client needs |
| Internal fields (isDeleted) in DTO | `blocked`, `mandateStatus`, `sessionActive` never in client DTOs |
| Missing `@Id`/`@GeneratedValue` | Every entity PK needs both |

### Phase 4 Lessons (JWT + Security)
| Mistake Made | Apply in Migration |
|-------------|-------------------|
| Stored accessToken in DB | Never. Access tokens are stateless. Store only hash of refresh token |
| `@PostMapping` without path | Each endpoint needs its own sub-path |
| `SecurityContextHolder` read at class level | Always call per-request (thread-local) |
| 201 status on login | 201 = created (register), 200 = success (login/refresh) |
| Single JWT secret key | Add key rotation from day 1 (authenticator pattern) |

### Phase 5 Lessons (Redis)
| Mistake Made | Apply in Migration |
|-------------|-------------------|
| No TTL on blacklist entries | Always set TTL = token remaining lifetime. Authenticator's `BLACKLISTED_JWT` Set is wrong |
| Single Redis template | Master for writes, replica for reads |

### Phase 6 Lessons (Kafka)
| Mistake Made | Apply in Migration |
|-------------|-------------------|
| `localhost:9092` in ECS | Use MSK bootstrap URL from AWS Secrets Manager |
| Type headers issue | `spring.json.add.type.headers: false` + explicit deserializer |

### Phase 7 Lessons (AWS)
| Mistake Made | Apply in Migration |
|-------------|-------------------|
| Build ARM image, deploy AMD64 | `docker build --platform linux/amd64` always |
| Resources in different VPCs | All services in same VPC |
| Missing initial DB name on RDS | Set **Initial database name** in RDS creation |
| ECS in private subnet, no NAT | Use public subnets + auto-assign public IP |
| Health check returning 403 | Add `/health` endpoint without auth, or configure matcher to accept 403 |
| RDS security group too open | Inbound only from service SG, not 0.0.0.0/0 |

---

## 7. MIGRATION EXECUTION ORDER

Build in this order (each depends on the previous):

```
Step 1: AUTHENTICATOR (Kotlin)
   ✓ You know auth deeply from notes-api
   ✓ Simplest service (one endpoint + Spring Security)
   Add: key rotation, DB session validation, subscription status

Step 2: GATEWAY (Kotlin)
   ✓ Routes + filter (conceptually same as JwtAuthFilter, but reactive)
   ✓ Depends on authenticator being ready
   Add: Kotlin DSL routes, circuit breaker, session cache in Redis

Step 3: USERSERVICE (Kotlin)
   ✓ Largest service — tackle last when patterns are set
   ✓ Depends on gateway (header injection pattern)
   Add: feature packages, OTPless integration, subscription domain
```

---

## 8. NEW THINGS TO ADD (Neither Has)

| Thing | Why | Where |
|-------|-----|-------|
| **Flyway** | DB migrations instead of `ddl-auto` or manual schema | All 3 services |
| **Spring Actuator** `/health` | Proper health endpoint for ALB | All 3 services |
| **OpenAPI/Swagger** | Document all endpoints | All 3 services |
| **Unit tests** | JwtService, AuthService, UserService coverage | All 3 services |
| **Integration tests** | Test auth flow end-to-end against real DB | Authenticator |
| **JWT cache at gateway** | Cache `sessionId → UserDTO` in Redis (TTL=1min) to reduce authenticator calls | Gateway |
| **Circuit breaker** | If authenticator down, don't cascade failure to all services | Gateway |
| **`@ControllerAdvice`** | Global error handling in userservice | Userservice |
| **Structured logging** | JSON logs with traceId for CloudWatch search | All 3 services |
| **Logout-all-devices** | Delete all SessionToken rows for userId | Authenticator |
| **Key rotation endpoint** | Admin endpoint to add new signing secret | Authenticator |

---

## 9. QUICK REFERENCE: notes-api → Production Mapping

| notes-api class | Production equivalent | Service |
|----------------|----------------------|---------|
| `JwtService` | `JwtService` (+ key rotation) | Authenticator |
| `JwtAuthFilter` | `AuthenticationFilter` | Authenticator |
| `TokenBlacklistService` | `RedisDevService` (fix TTL) | Authenticator |
| `SecurityConfig` | `SecurityConfig` | Authenticator |
| `AuthService.register()` | `OtplessService.verify()` + `TrueCallerService.verify()` | Userservice |
| `AuthService.login()` | `UserService.generateJwtToken()` | Userservice |
| `AuthService.refresh()` | ❌ Add to authenticator | Authenticator (new) |
| `AuthService.logout()` | `UserService.logout()` | Userservice |
| `AuthController` | `UsersController` (login/logout) | Userservice |
| `NoteController` | `UsersController` (profile/subscription) | Userservice |
| `User` entity | `Users` entity | Userservice |
| `Note` entity | `StreamSession`, `WatchShowList` | Userservice |
| `RefreshToken` entity | `SessionToken` (fix: store hash) | Authenticator |
| `BaseResponse<T>` | `GenericResponseDTO` | All 3 |
| `GlobalExceptionHandler` | ❌ Missing in userservice (add it) | Userservice |
| `NoteMapper` | Feature-specific mappers | All 3 |
| `NoteCreatedEvent` + `KafkaListener` | `KafkaService` (MSK) | Userservice |
| No gateway concept | `AuthenticationFilter` (GatewayFilterFactory) | Gateway |
