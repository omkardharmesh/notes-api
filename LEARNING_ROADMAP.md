# Backend Learning Roadmap — Note-Taking API (Production-Grade)

**Author**: Claude (AI pair-programmer)
**For**: Android developer ramping up on backend before joining a production Spring Boot project
**Created**: 2026-04-08
**Timeline**: ~2 weeks, breadth-first, learn by building
**Goal**: Walk into a production BE codebase and not be lost. Understand the full stack: Spring Boot, PostgreSQL, Redis, Kafka, AWS, Docker.

---

## IMPORTANT: How to use this with an AI agent

**DO NOT autopilot.** The user is learning. Do not generate the project, configure everything, and build it without them touching anything. That defeats the whole purpose.

**Rules for the agent:**
- Explain the HLD (what to build and why)
- Let the user write the code
- Review what they write, like a real PR review
- Only write code if the user explicitly asks you to
- Use Android analogies — the user is an Android dev
- When explaining, connect to what they already know (Room, Ktor, Koin, MVVM)
- Log every mistake the user makes to the **Lessons Learned** section at the bottom of this doc, in the same table format, grouped by phase/step

---

## Context & Background

### Who is this for?
An Android developer with strong Kotlin, Clean Architecture, and mobile patterns (MVVM, Repository, UseCases, Room, Ktor). New to backend, has done one Spring Boot tutorial (note-taking app with MongoDB + JWT auth). Joining a production backend team in ~2 weeks.

### Company Tech Stack (what we're mirroring)
| Layer | Technology |
|-------|-----------|
| Language | Java/Kotlin (Spring Boot primary) |
| Database (relational) | PostgreSQL |
| Database (document) | MongoDB (AWS DocumentDB) |
| Caching | Redis (AWS ElastiCache) |
| Messaging | Kafka (AWS MSK) |
| Scripting/Lambdas | Python |
| Infra | AWS (ECS, RDS, ALB, API Gateway) |

### What we're building
The same note-taking API from the tutorial, **rebuilt from scratch** using the production tech stack. PostgreSQL instead of MongoDB. Docker for everything. Same domain, new tools.

---

## Design Decisions (Agreed)

### 1. Standard API Response Envelope
Every endpoint returns this structure (matches company pattern):
```json
{
  "code": 200,
  "message": "Success",
  "data": { ... }
}
```

Kotlin implementation:
```kotlin
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T? = null
)
```

Error responses follow the same shape:
```json
{
  "code": 400,
  "message": "Validation failed",
  "data": {"errors": ["Title cannot be blank"]}
}
```

### 2. JWT Design
- **Payload**: `sub` (userId), `deviceId`, `exp` (expiry)
- **No sessionId** — keeping it simple
- Access token: short-lived (~15 min)
- Refresh token: long-lived (~30 days), hashed before DB storage
- Token rotation on refresh (old token invalidated)

### 3. Request Headers (mirrors company pattern)
```
Authorization: Bearer <jwt>
Content-Type: application/json
appVersion: 9
platform: 0          // 0=Android, 1=iOS
deviceId: <device-id>
os: Android 15 (API 35)
ts: <unix-timestamp>
```
BE extracts userId from JWT (never from request params). Headers like `deviceId`, `platform`, `appVersion` are logged and may be used for feature flags, analytics, debugging.

### 4. BE-Only Concepts (things FE never sees)
- **Audit columns**: `createdAt`, `updatedAt`, `isDeleted` (soft delete) on every table
- **DB indexes**: on foreign keys, frequently queried columns
- **Internal DTOs**: DB entity ≠ API response. Always map between them.
- **Logging with traceId**: every request gets a correlation ID for debugging across services
- **Rate limiting**: per userId, per IP
- **Inter-service calls**: services talk to each other via REST or Kafka

### 5. Learning Format
- **HLD first, then implement** — mirrors how it works in production (senior dev discusses design, you implement)
- **TODOs in code** — explain what we're doing and why at each step
- **Trace the request** — at each phase, walk through "a request arrives, what happens?"
- **Android analogies** — map BE concepts to mobile equivalents where helpful

---

## Roadmap

### Phase 1: Docker Setup
**What**: Install Docker Desktop. Create `docker-compose.yml` with PostgreSQL.
**Why**: Everything at work runs in containers. You need Docker to run Postgres, Redis, Kafka locally. No native installs.
**Android analogy**: Like setting up an emulator — you don't install a phone OS natively, you run it in a container.

**You'll learn**:
- What Docker is (process isolation, images vs containers)
- `docker-compose.yml` — defining services
- Port mapping (container port → host port)
- Volumes (persistent data)
- Basic commands: `docker compose up`, `docker compose down`, `docker ps`, `docker logs`

---

### Phase 2: New Spring Boot + Kotlin Project
**What**: Initialize a fresh Spring Boot project. Connect to Dockerized PostgreSQL. Verify the connection.
**Why**: Understanding project setup from zero. The tutorial skipped this — you used a pre-made project.

**You'll learn**:
- Spring Initializr — picking dependencies
- `application.yml` (we'll use YAML, more common in production than `.properties`)
- DataSource configuration, connection pooling (HikariCP)
- How Spring auto-configures things based on what's on the classpath
- Project structure: where things go and why

**Dependencies we'll pick**:
- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-validation`
- `spring-boot-starter-security`
- PostgreSQL driver
- JJWT (JWT library)

---

### Phase 3: Entities, JPA, CRUD Endpoints
**What**: Build `User` and `Note` entities. Repositories. NoteController with full CRUD. Standard `ApiResponse<T>` wrapper.
**Why**: This is 80% of what you'll do in your first weeks — writing entities, repos, services, controllers.

**You'll learn**:
- JPA entities vs Mongo documents (you know `@Document`, now learn `@Entity`, `@Table`, `@Column`)
- `@OneToMany`, `@ManyToOne` relationships (vs Mongo's embedded docs / manual ID references)
- JPA Repository — `JpaRepository<Note, Long>` (similar to `MongoRepository`)
- Custom queries with `@Query` (JPQL) vs method-name-derived queries
- DTOs — request/response objects, mapping to/from entities
- Validation — `@Valid`, `@NotBlank`, `@Email`, custom validators
- `@RestControllerAdvice` for global error handling with our `ApiResponse` envelope
- Service layer — business logic lives here, not in controllers

**Key difference from your tutorial project**:
| Mongo (what you did) | PostgreSQL/JPA (what we'll do) |
|---------------------|-------------------------------|
| `@Document` | `@Entity` + `@Table` |
| `ObjectId` | `Long` (auto-increment) or `UUID` |
| `MongoRepository` | `JpaRepository` |
| No schema, flexible | Strict schema, columns defined |
| `findByOwnerId` (auto) | Same, but backed by SQL `WHERE` clause |

---

### Phase 4: Authentication (JWT + Spring Security)
**What**: Register, Login, Refresh endpoints. JWT generation/validation. Security filter chain.
**Why**: Every production endpoint is secured. You need to understand the auth flow deeply.

**You'll learn**:
- Spring Security filter chain — how a request is intercepted before reaching your controller
- `SecurityFilterChain` bean configuration
- BCrypt password hashing
- JWT creation and validation (HMAC-SHA256)
- How `SecurityContextHolder` works (thread-local, per-request)
- `@AuthenticationPrincipal` — getting the current user cleanly
- Refresh token rotation — why and how
- Storing hashed refresh tokens in PostgreSQL

**Request flow after this phase**:
```
HTTP Request
  → Security Filter Chain
    → JwtAuthFilter: extract + validate token, set SecurityContext
      → Controller: get userId from context, process request
        → Service: business logic
          → Repository: DB query
            → PostgreSQL
```

---

### Phase 5: Docker-Compose Expansion + Redis
**What**: Add Redis to `docker-compose.yml`. Implement caching and token blacklist.
**Why**: Redis is used everywhere in production — caching DB queries, session/token management, rate limiting.

**You'll learn**:
- What Redis is (in-memory key-value store, like a giant `HashMap` that persists)
- Spring Data Redis — `RedisTemplate`, `@Cacheable`, `@CacheEvict`
- Cache patterns: Cache-Aside (most common), TTL-based expiry
- Token blacklist — when a user logs out, blacklist the access token in Redis until it expires
- When to cache vs when not to (stale data tradeoffs)
- `docker-compose.yml` with multiple services talking to each other

**Android analogy**: Like an in-memory LRU cache before hitting the network. Room is your PostgreSQL, OkHttp cache is your Redis.

---

### Phase 6: Kafka Basics
**What**: Add Kafka to `docker-compose.yml`. Publish events when notes are created/updated. Build a simple consumer.
**Why**: Production systems don't do everything synchronously. Kafka decouples services.

**You'll learn**:
- What Kafka is — distributed event log (not a queue, a log)
- Topics, partitions, consumer groups
- Producer: publish a `NoteCreatedEvent` when a note is saved
- Consumer: a listener that processes events (e.g., log it, send notification)
- `spring-kafka` — `KafkaTemplate`, `@KafkaListener`
- Why async: "user doesn't need to wait for the notification to be sent before getting a response"
- Serialization: JSON events

**Android analogy**: Like `WorkManager` — fire an event, it gets processed in the background. The user doesn't wait.

---

### Phase 7: AWS — Account, Deploy, Understand Infra
**What**: Create AWS free-tier account. Dockerize the app. Deploy to AWS. Understand how requests flow in production.
**Why**: You need to understand where your code runs, how it's accessed, and what infrastructure surrounds it.

**You'll learn**:
- **Dockerfile** — how to package your Spring Boot app
- **AWS Free Tier** — what's free, what costs money, how to not get surprise-billed
- **VPC, Subnets, Security Groups** — the network your code lives in
- **ECS (Fargate)** — running Docker containers on AWS without managing servers
- **RDS** — managed PostgreSQL (like your Docker Postgres, but AWS runs it)
- **ElastiCache** — managed Redis
- **ALB (Application Load Balancer)** — distributes traffic across multiple instances of your app
- **API Gateway** — single entry point, routing, rate limiting
- **How a request flows in AWS**:
  ```
  User → DNS → API Gateway / ALB → ECS Task (your Docker container)
       → RDS (PostgreSQL)
       → ElastiCache (Redis)
       → MSK (Kafka)
  ```
- **Inter-service communication**: Service A calls Service B via ALB (sync) or publishes to Kafka topic (async)
- **CloudWatch** — logs and monitoring

---

### Phase 8: Feature-Based Packaging (Industry-Scale Codebase Structure)
**What**: Refactor from layer-first packages (`controller/`, `service/`, `repository/`) to feature-first packages (`auth/`, `note/`, `common/`).
**Why**: In larger teams, feature ownership scales better than cross-layer ownership. It reduces package sprawl and makes changes easier to review.

**You'll learn**:
- How to organize Spring Boot code by domain feature instead of technical layer
- How to keep each feature vertically sliced (controller + service + repository + dto together)
- What belongs in `common/` (shared response envelope, global exception handler, config, shared security utilities)
- How package boundaries improve onboarding, parallel development, and merge conflict reduction
- Migration strategy: move one feature at a time without breaking endpoints

**Target package shape (example)**:
```text
com.notesapp.notes_api
├── auth
│   ├── AuthController.kt
│   ├── AuthService.kt
│   ├── dto/
│   ├── model/
│   └── repository/
├── note
│   ├── NoteController.kt
│   ├── NoteService.kt
│   ├── dto/
│   ├── model/
│   ├── mapper/
│   └── repository/
└── common
    ├── response/
    ├── exception/
    ├── security/
    └── config/
```

**Android analogy**: Same as feature modules / package-by-feature in Android (`feature/auth`, `feature/notes`, `core/common`) instead of dumping everything into global `ui/`, `data/`, `domain` buckets.

---

## How to Use This Document

If starting a new Claude session or using a different agent:
1. Share this file for full context
2. Mention which phase you're on
3. The agent should continue from where you left off
4. All design decisions above are **agreed and final** — don't re-discuss unless explicitly asked

## Current Status
- [x] Phase 1: Docker Setup
- [x] Phase 2: New Spring Boot Project
- [x] Phase 3: Entities, JPA, CRUD
- [x] Phase 4: Auth (JWT + Spring Security)
- [x] Phase 5: Redis
- [ ] Phase 6: Kafka
- [ ] Phase 7: AWS
- [ ] Phase 8: Feature-Based Packaging

---

## Lessons Learned (Mistakes → Corrections)

### Phase 2: application.yml
| Mistake | Correction | Lesson |
|---------|-----------|--------|
| JDBC URL `host:port` left as placeholder, then tried `5432:5432` | `localhost:5432` — Docker maps container port to your host, so use `localhost` | JDBC URL format: `jdbc:postgresql://host:port/db`. Docker port mapping `X:Y` means connect to `localhost:X` |
| `hibernate.dialect` set to `true` | `org.hibernate.dialect.PostgreSQLDialect` (and later removed — Hibernate auto-detects it) | Dialect is a Java class, not a boolean. Modern Hibernate doesn't even need it explicitly |
| `ddl-auto: none` | `ddl-auto: update` — auto-creates/updates schema without wiping data | `none` = Hibernate touches nothing. `create` = drops + recreates on every start. `update` = safe for dev |

### Phase 3: Entities
| Mistake | Correction | Lesson |
|---------|-----------|--------|
| `id: String` for primary key | `id: Long? = null` with `@Id @GeneratedValue(IDENTITY)` | Auto-generated IDs are nullable — the DB assigns them. Like Room's `autoGenerate = true` |
| `createdAt: String` | `createdAt: Instant` | Use proper types, not Strings. JPA maps `Instant` to SQL `timestamp` |
| Entity named `Notes` (plural) | `Note` (singular) | Entity = one row. Singular by convention, same as Room |
| No `@Id` annotation | Added `@Id` on the `id` field | JPA won't compile without knowing the primary key |
| Stored `ownerId: Long` directly | `@ManyToOne` + `@JoinColumn` with `owner: User` | JPA models relationships via entity references, not raw IDs. DB still stores just the FK column |

### Phase 3: Repositories
| Mistake | Correction | Lesson |
|---------|-----------|--------|
| Defined `createNote()`, `deleteNote()`, `updateNote()` manually | Removed them — `JpaRepository` provides `save()`, `deleteById()`, `findAll()` for free | Unlike Room DAOs, JPA repos inherit all basic CRUD. Only add custom query methods |
| `findByOwnerID` (capital ID) | `findByOwnerId` (camelCase `Id`) | Spring derives SQL from method name by matching entity field names exactly |
| Put update logic in repository | Moved to service layer | Repository = data access only. Business logic (ownership checks, field mapping) belongs in the service |

### Phase 3: DTOs
| Mistake | Correction | Lesson |
|---------|-----------|--------|
| `NoteRequest` included `owner: User` | Removed — server gets user from JWT, client never declares identity | Never trust the client to say who they are. Auth info comes from the token |
| `NoteRequest` included `isDeleted` | Removed — server-side flag only | Internal fields (soft delete, audit columns) are never set by the client |
| `NoteResponse` wrapped entire `Note` entity | Flattened to individual fields | DTOs exist to NOT expose entities. Pick only what the client needs |
| `NoteResponse.createdAt` had default `Instant.now()` | Removed default — value comes from entity mapping | Response DTOs don't generate data. They receive it from the entity |

### Phase 3: Service
| Mistake | Correction | Lesson |
|---------|-----------|--------|
| `update()` created a new Note (no `id` set) | Pass existing `noteId`, fetch existing note, use `copy()` | Without an ID, `save()` inserts a new row. With an ID, it updates. Fetch first, then copy |
| No ownership check in update/delete | Added `if (userId != existingNote.owner.id)` check | Always verify the requesting user owns the resource. Security is a service-layer concern |
| `getAll` filtered deleted notes in Kotlin (`.filter { !it.isDeleted }`) | Created `findByOwnerIdAndIsDeletedFalse()` in repo | Let the DB filter — less data transferred, more efficient. Like writing a proper Room `@Query WHERE` clause instead of filtering a `Flow` |
| `findById().get()` to load owner | `getReferenceById()` — proxy, no DB query | When you only need the FK (not the full entity), use a reference proxy. Avoids unnecessary SELECT |
| `throw` inside `orElseThrow { throw Exception() }` | `orElseThrow { Exception() }` — no `throw` keyword | `orElseThrow` already throws. Adding `throw` is redundant |

### Phase 3: Controller & Validation
| Mistake | Correction | Lesson |
|---------|-----------|--------|
| URL had verbs like `/createNote` | Just `POST /notes` — HTTP method is the verb | REST convention: nouns in URLs, HTTP methods are the verbs |
| `@PostMapping("/notes")` with `@RequestMapping("/notes")` on class | Remove `/notes` from method annotation — class prefix already adds it | Class-level `@RequestMapping` prefixes all method paths. `/notes` + `/notes` = `/notes/notes` |
| `userId` in URL path `/{userId}` | Hardcoded for now, will come from JWT in Phase 4 | Client never sends their own identity. Server extracts it from auth token |
| `@NotBlank` on `Long` field (color) | Removed — `@NotBlank` is for Strings only | Use `@NotNull` for non-String types, or rely on Kotlin's non-null types |
| `user` as table name | `@Table(name = "users")` | `user` is a reserved keyword in PostgreSQL. Always quote or rename |

### Phase 4: JwtService
| Mistake | Correction | Lesson |
|---------|-----------|--------|
| `@Value` path `${jwt.secret}` didn't match yml key `app.jwt.secret` | `${app.jwt.secret}` — must match the full yml path | `@Value` paths are exact matches to your yml hierarchy. If it's under `app.jwt:`, the path is `app.jwt.secret` |
| JJWT deps without version (`io.jsonwebtoken:jjwt-api`) | Added explicit version `:0.12.6` | Spring BOM only manages Spring libraries. Third-party libs (JJWT, Retrofit, etc.) always need explicit versions |
| `expiration` fields typed as `String` | Changed to `Long` | Millisecond values need numeric types for date math |
| Missing `.compact()` on `Jwts.builder()` | Added `.compact()` at the end | `.builder()` returns a builder object, not the JWT string. `.compact()` serializes it |
| Returned `"Bearer $accessToken"` from generate method | Return raw token — `accessToken.compact()` | `Bearer` prefix is added by the client in the `Authorization` header, not by the token generator |
| `generateRefreshToken` contained parse logic instead of generate | Replaced with builder pattern matching `generateAccessToken` | Generate = `Jwts.builder()`, Parse = `Jwts.parser()` — don't mix them up |
| `extractDeviceId` read `.subject` instead of custom claim | `claims.get("deviceId", String::class.java)` | `subject` is a standard JWT field (userId). Custom claims use the generic `.get("key", Type)` accessor |
| `extractDeviceId` return type was `Long` | Changed to `String` | deviceId is a string identifier, not a number |
| Caught `ParseException` in `isTokenValid` | Catch `Exception` (JJWT throws `JwtException` subtypes) | JJWT uses its own exception hierarchy — `ExpiredJwtException`, `SignatureException`, etc. Broad catch covers all |
| `app:` block indented under `spring:` in yml | Moved to root level (zero indentation) | YAML indentation = nesting. `app:` under `spring:` becomes `spring.app.jwt.secret` instead of `app.jwt.secret` |

### Phase 4: RefreshToken Entity
| Mistake | Correction | Lesson |
|---------|-----------|--------|
| Missing `@Id` and `@GeneratedValue` on `id` field | Added both annotations | JPA requires these on every entity PK — same as Note and User |
| Included `accessToken` field in entity | Removed — access tokens are stateless, not stored in DB | Only refresh tokens are stored. Access tokens are validated by signature alone |

### Phase 4: AuthService
| Mistake | Correction | Lesson |
|---------|-----------|--------|
| Called `findByEmail()` three times in `login()` | Fetch once, store in val, reuse | Each call is a separate DB query. Fetch once and reuse the result |
| Stored raw refresh token instead of hashed in update branch | `bCryptPasswordEncoder.encode(refreshToken)` | Always hash before storing — same rule as passwords |
| `@Value` path `refresh-token-expiry` didn't match yml key `refresh-token-expiration` | Matched the exact yml key | Mismatched `@Value` paths crash the app on startup — Spring can't find the property |
| Used `plusSeconds()` with a millisecond value | Changed to `plusMillis()` | Units must match — yml stores milliseconds, so use `plusMillis()` |
| Used generic `throw Exception(...)` for all errors | Created custom exceptions (`InvalidCredentialsException`, etc.) | Custom exceptions map to specific HTTP status codes via `@ExceptionHandler`. Generic exceptions all become 500 |

### Phase 4: NoteController
| Mistake | Correction | Lesson |
|---------|-----------|--------|
| `userId` as class-level property from `SecurityContextHolder` | Changed to `getUserId()` method called per-request | `SecurityContextHolder` is per-request (thread-local). Reading it at construction time gets nothing — the controller is created once at startup |

### Phase 4: AuthController
| Mistake | Correction | Lesson |
|---------|-----------|--------|
| `@PostMapping` without path on register | Added `@PostMapping("/register")` | Without a sub-path, all POST methods on the class conflict — each endpoint needs its own path |
| Login endpoint returned `HttpStatus.CREATED` (201) | Changed to `HttpStatus.OK` (200) | 201 = resource created (register). 200 = success (login, refresh). Match status to semantics |

---

## Reference: Existing Tutorial Project
Located at: `/Users/eloelo/Downloads/spring_boot_crash_course/`
- Spring Boot 3.4.1 + Kotlin
- MongoDB, JWT auth, Note CRUD
- Useful as a comparison point ("how did Mongo do this vs how JPA does it")
- Has its own `PROJECT_DOCUMENTATION.md` with detailed explanations
