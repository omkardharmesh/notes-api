# Notes API — Kotlin/Spring → Python/FastAPI Migration Guide

Side-by-side mapping. Same project, Python idioms. Use alongside existing Kotlin code to learn FastAPI.

---

## 1. Project Structure Mapping

### Current (Kotlin/Spring)

```
src/main/kotlin/com/notesapp/notes_api/
├── NotesApiApplication.kt
├── config/RedisConfig.kt
├── controller/
│   ├── AuthController.kt
│   ├── NoteController.kt
│   └── GlobalExceptionHandler.kt
├── dto/
│   ├── AuthRequest.kt, AuthResponse.kt, RefreshRequest.kt
│   ├── NoteRequest.kt, NoteResponse.kt
│   └── BaseResponse.kt
├── events/
│   ├── EventsConsumer.kt
│   └── NoteCreatedEvent.kt
├── exception/AuthExceptions.kt
├── mapper/NoteMapper.kt
├── model/
│   ├── User.kt, Note.kt, RefreshToken.kt
├── repository/
│   ├── UserRepository.kt, NoteRepository.kt, RefreshTokenRepository.kt
├── security/
│   ├── JwtService.kt, JwtAuthFilter.kt, SecurityConfig.kt
└── service/
    ├── AuthService.kt, NoteService.kt, TokenBlacklistService.kt
```

### Target (Python/FastAPI) — feature-sliced

```
notes-api-py/
├── pyproject.toml
├── uv.lock
├── .env / .env.example
├── Dockerfile
├── docker-compose.yml
├── alembic.ini
│
├── src/notes_api/
│   ├── __init__.py
│   ├── main.py                      # = NotesApiApplication.kt
│   ├── config.py                    # = application.yml (pydantic-settings)
│   │
│   ├── core/
│   │   ├── deps.py                  # get_db, get_current_user (Depends)
│   │   ├── exceptions.py            # = exception/AuthExceptions.kt
│   │   ├── exception_handlers.py    # = controller/GlobalExceptionHandler.kt
│   │   └── middleware.py            # request-id, CORS, logging
│   │
│   ├── db/
│   │   ├── session.py               # SQLAlchemy engine + SessionLocal
│   │   └── base.py                  # DeclarativeBase
│   │
│   ├── auth/
│   │   ├── router.py                # = controller/AuthController.kt
│   │   ├── service.py               # = service/AuthService.kt
│   │   ├── models.py                # = model/User.kt + RefreshToken.kt
│   │   ├── schemas.py               # = dto/AuthRequest.kt + AuthResponse.kt
│   │   ├── repository.py            # = repository/UserRepository.kt + RefreshTokenRepository.kt
│   │   ├── security.py              # = security/JwtService.kt + JwtAuthFilter.kt
│   │   └── blacklist.py             # = service/TokenBlacklistService.kt
│   │
│   ├── notes/
│   │   ├── router.py                # = controller/NoteController.kt
│   │   ├── service.py               # = service/NoteService.kt
│   │   ├── models.py                # = model/Note.kt
│   │   ├── schemas.py               # = dto/NoteRequest.kt + NoteResponse.kt
│   │   ├── repository.py            # = repository/NoteRepository.kt
│   │   └── mapper.py                # = mapper/NoteMapper.kt  (often unneeded, see §6)
│   │
│   ├── cache/
│   │   └── redis_client.py          # = config/RedisConfig.kt
│   │
│   └── events/
│       ├── producer.py              # Kafka producer
│       ├── consumer.py              # = events/EventsConsumer.kt
│       └── schemas.py               # = events/NoteCreatedEvent.kt
│
├── alembic/
│   ├── env.py
│   └── versions/
│       └── 001_initial.py
│
├── tests/
│   ├── conftest.py
│   ├── test_auth.py
│   └── test_notes.py
│
└── scripts/
    └── seed.py
```

---

## 2. Dependencies Mapping

### `build.gradle.kts` → `pyproject.toml`

| Kotlin/Spring | Python/FastAPI |
|---|---|
| `spring-boot-starter-web` | `fastapi`, `uvicorn[standard]` |
| `spring-boot-starter-data-jpa` | `sqlalchemy>=2.0`, `alembic` |
| `spring-boot-starter-security` | `passlib[bcrypt]`, `python-jose[cryptography]` |
| `spring-boot-starter-data-redis` | `redis>=5` |
| `spring-kafka` | `aiokafka` or `confluent-kafka` |
| `jackson-module-kotlin` | `pydantic>=2` (built-in) |
| `postgresql` driver | `psycopg[binary]>=3` or `asyncpg` |
| `spring-boot-starter-validation` | `pydantic>=2` (built-in) |
| `spring-boot-starter-test` | `pytest`, `httpx`, `pytest-asyncio` |

Sample `pyproject.toml`:

```toml
[project]
name = "notes-api"
version = "0.1.0"
requires-python = ">=3.12"
dependencies = [
  "fastapi>=0.115",
  "uvicorn[standard]>=0.30",
  "sqlalchemy>=2.0",
  "alembic>=1.13",
  "psycopg[binary]>=3.2",
  "pydantic>=2.8",
  "pydantic-settings>=2.4",
  "passlib[bcrypt]>=1.7",
  "python-jose[cryptography]>=3.3",
  "redis>=5.0",
  "aiokafka>=0.11",
]

[dependency-groups]
dev = ["pytest>=8", "httpx>=0.27", "pytest-asyncio>=0.24", "ruff", "mypy"]
```

Install: `uv sync`
Run: `uv run uvicorn notes_api.main:app --reload`

---

## 3. Config Mapping

### `application.yml` → `config.py` + `.env`

Kotlin/Spring reads `application.yml` auto. Python uses `pydantic-settings` loading from `.env`.

```python
# src/notes_api/config.py
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str                      # postgresql+psycopg://...
    redis_url: str = "redis://localhost:6379/0"
    kafka_bootstrap_servers: str = "localhost:9092"

    jwt_secret: str
    jwt_access_expiry_minutes: int = 15
    jwt_refresh_expiry_days: int = 7

settings = Settings()  # singleton
```

`.env`:

```
DATABASE_URL=postgresql+psycopg://postgres:postgres@localhost:5432/notes
REDIS_URL=redis://localhost:6379/0
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
JWT_SECRET=change-me
```

---

## 4. Entry Point

### `NotesApiApplication.kt` → `main.py`

```python
# src/notes_api/main.py
from contextlib import asynccontextmanager
from fastapi import FastAPI

from notes_api.auth.router import router as auth_router
from notes_api.notes.router import router as notes_router
from notes_api.cache.redis_client import redis_client
from notes_api.events.consumer import start_consumer, stop_consumer
from notes_api.core.exception_handlers import register_exception_handlers
from notes_api.core.middleware import setup_middleware

@asynccontextmanager
async def lifespan(app: FastAPI):
    # startup — like @PostConstruct / CommandLineRunner
    await redis_client.ping()
    await start_consumer()
    yield
    # shutdown — like @PreDestroy
    await stop_consumer()
    await redis_client.aclose()

app = FastAPI(title="Notes API", lifespan=lifespan)
setup_middleware(app)
register_exception_handlers(app)

app.include_router(auth_router, prefix="/auth", tags=["auth"])
app.include_router(notes_router, prefix="/notes", tags=["notes"])
```

---

## 5. Entity / Model Mapping

### `model/User.kt` → `auth/models.py`

Kotlin JPA:
```kotlin
@Entity
@Table(name = "users")
data class User(
    @Id @GeneratedValue val id: UUID? = null,
    @Column(unique = true) val email: String,
    val passwordHash: String,
    val createdAt: Instant = Instant.now()
)
```

SQLAlchemy 2.0 (typed):
```python
# src/notes_api/auth/models.py
from datetime import datetime
from uuid import UUID, uuid4
from sqlalchemy import String, DateTime
from sqlalchemy.orm import Mapped, mapped_column
from notes_api.db.base import Base

class User(Base):
    __tablename__ = "users"

    id: Mapped[UUID] = mapped_column(primary_key=True, default=uuid4)
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(255))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=datetime.utcnow)
```

Key diffs:
- No `@Entity` — inherit `Base`.
- `@Column` props become `mapped_column()` args.
- Relationships: `relationship("Note", back_populates="user")`.
- No Hibernate lazy-loading surprises — explicit `selectinload()` in queries.

---

## 6. DTO / Schema Mapping

### `dto/NoteRequest.kt` → `notes/schemas.py`

Kotlin:
```kotlin
data class NoteRequest(
    @field:NotBlank val title: String,
    val body: String?
)
```

Pydantic v2:
```python
# src/notes_api/notes/schemas.py
from pydantic import BaseModel, Field
from uuid import UUID
from datetime import datetime

class NoteRequest(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    body: str | None = None

class NoteResponse(BaseModel):
    id: UUID
    title: str
    body: str | None
    created_at: datetime

    model_config = {"from_attributes": True}  # = can build from ORM obj directly
```

**`from_attributes=True` kills `NoteMapper.kt`** — Pydantic reads ORM attrs directly. Your mapper becomes:

```python
return NoteResponse.model_validate(note_orm)
```

Gone. 20 lines of Kotlin mapper → 1 line.

---

## 7. Repository Mapping

### `repository/NoteRepository.kt` → `notes/repository.py`

Kotlin Spring Data:
```kotlin
interface NoteRepository : JpaRepository<Note, UUID> {
    fun findByUserId(userId: UUID): List<Note>
}
```

Python — no magic interface, write it:
```python
# src/notes_api/notes/repository.py
from uuid import UUID
from sqlalchemy import select
from sqlalchemy.orm import Session
from notes_api.notes.models import Note

class NoteRepository:
    def __init__(self, db: Session):
        self.db = db

    def find_by_user_id(self, user_id: UUID) -> list[Note]:
        stmt = select(Note).where(Note.user_id == user_id)
        return list(self.db.scalars(stmt))

    def save(self, note: Note) -> Note:
        self.db.add(note)
        self.db.commit()
        self.db.refresh(note)
        return note

    def find_by_id(self, id: UUID) -> Note | None:
        return self.db.get(Note, id)

    def delete(self, note: Note) -> None:
        self.db.delete(note)
        self.db.commit()
```

Tradeoff: more boilerplate than Spring Data, but no hidden query derivation. What you write = what runs.

---

## 8. Service Mapping

### `service/NoteService.kt` → `notes/service.py`

```python
# src/notes_api/notes/service.py
from uuid import UUID
from notes_api.notes.repository import NoteRepository
from notes_api.notes.models import Note
from notes_api.notes.schemas import NoteRequest
from notes_api.events.producer import publish_note_created

class NoteService:
    def __init__(self, repo: NoteRepository):
        self.repo = repo

    def create(self, user_id: UUID, req: NoteRequest) -> Note:
        note = Note(user_id=user_id, title=req.title, body=req.body)
        saved = self.repo.save(note)
        publish_note_created(saved)
        return saved

    def list_for_user(self, user_id: UUID) -> list[Note]:
        return self.repo.find_by_user_id(user_id)
```

No `@Service` annotation. Plain class. Wired via `Depends()`.

---

## 9. Controller / Router Mapping

### `controller/NoteController.kt` → `notes/router.py`

```python
# src/notes_api/notes/router.py
from uuid import UUID
from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from notes_api.core.deps import get_db, get_current_user
from notes_api.notes.repository import NoteRepository
from notes_api.notes.service import NoteService
from notes_api.notes.schemas import NoteRequest, NoteResponse
from notes_api.auth.models import User

router = APIRouter()

def get_note_service(db: Session = Depends(get_db)) -> NoteService:
    return NoteService(NoteRepository(db))

@router.post("", response_model=NoteResponse, status_code=status.HTTP_201_CREATED)
def create_note(
    req: NoteRequest,
    user: User = Depends(get_current_user),
    svc: NoteService = Depends(get_note_service),
):
    return svc.create(user.id, req)

@router.get("", response_model=list[NoteResponse])
def list_notes(
    user: User = Depends(get_current_user),
    svc: NoteService = Depends(get_note_service),
):
    return svc.list_for_user(user.id)
```

Mapping:
- `@RestController` + `@RequestMapping` → `APIRouter()` + `prefix=` in `main.py`.
- `@GetMapping` / `@PostMapping` → `@router.get()` / `@router.post()`.
- `@RequestBody` → typed parameter (Pydantic model).
- `@AuthenticationPrincipal` → `Depends(get_current_user)`.
- `@Autowired` constructor injection → `Depends()`.

---

## 10. Security (JWT) Mapping

### `security/JwtService.kt` + `JwtAuthFilter.kt` → `auth/security.py` + `core/deps.py`

```python
# src/notes_api/auth/security.py
from datetime import datetime, timedelta, timezone
from jose import jwt, JWTError
from passlib.context import CryptContext
from notes_api.config import settings

pwd_ctx = CryptContext(schemes=["bcrypt"], deprecated="auto")

def hash_password(pw: str) -> str:
    return pwd_ctx.hash(pw)

def verify_password(pw: str, hashed: str) -> bool:
    return pwd_ctx.verify(pw, hashed)

def create_access_token(sub: str) -> str:
    exp = datetime.now(timezone.utc) + timedelta(minutes=settings.jwt_access_expiry_minutes)
    return jwt.encode({"sub": sub, "exp": exp, "type": "access"}, settings.jwt_secret, algorithm="HS256")

def decode_token(token: str) -> dict:
    return jwt.decode(token, settings.jwt_secret, algorithms=["HS256"])
```

```python
# src/notes_api/core/deps.py
from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from jose import JWTError
from sqlalchemy.orm import Session

from notes_api.db.session import SessionLocal
from notes_api.auth.security import decode_token
from notes_api.auth.repository import UserRepository
from notes_api.auth.blacklist import is_blacklisted
from notes_api.auth.models import User

oauth2 = OAuth2PasswordBearer(tokenUrl="/auth/login")

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

async def get_current_user(
    token: str = Depends(oauth2),
    db: Session = Depends(get_db),
) -> User:
    if await is_blacklisted(token):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Token revoked")
    try:
        payload = decode_token(token)
        user_id = payload["sub"]
    except (JWTError, KeyError):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid token")
    user = UserRepository(db).find_by_id(user_id)
    if not user:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "User not found")
    return user
```

No `SecurityFilterChain` / `SecurityConfig`. FastAPI = per-route `Depends(get_current_user)`. Explicit. Public routes skip it.

---

## 11. Redis + Token Blacklist

### `config/RedisConfig.kt` + `service/TokenBlacklistService.kt`

```python
# src/notes_api/cache/redis_client.py
import redis.asyncio as redis
from notes_api.config import settings

redis_client = redis.from_url(settings.redis_url, decode_responses=True)
```

```python
# src/notes_api/auth/blacklist.py
import hashlib
from notes_api.cache.redis_client import redis_client

def _key(token: str) -> str:
    h = hashlib.sha256(token.encode()).hexdigest()
    return f"bl:{h}"

async def blacklist(token: str, ttl_seconds: int) -> None:
    await redis_client.setex(_key(token), ttl_seconds, "1")

async def is_blacklisted(token: str) -> bool:
    return await redis_client.exists(_key(token)) > 0
```

Matches your SHA-256 hashing decision from Phase 5.

---

## 12. Kafka Events

### `events/EventsConsumer.kt` + `NoteCreatedEvent.kt`

```python
# src/notes_api/events/schemas.py
from pydantic import BaseModel
from uuid import UUID
from datetime import datetime

class NoteCreatedEvent(BaseModel):
    note_id: UUID
    user_id: UUID
    title: str
    created_at: datetime
```

```python
# src/notes_api/events/producer.py
import json
from aiokafka import AIOKafkaProducer
from notes_api.config import settings
from notes_api.events.schemas import NoteCreatedEvent

_producer: AIOKafkaProducer | None = None

async def get_producer() -> AIOKafkaProducer:
    global _producer
    if _producer is None:
        _producer = AIOKafkaProducer(bootstrap_servers=settings.kafka_bootstrap_servers)
        await _producer.start()
    return _producer

async def publish_note_created(note) -> None:
    ev = NoteCreatedEvent(note_id=note.id, user_id=note.user_id,
                          title=note.title, created_at=note.created_at)
    p = await get_producer()
    await p.send_and_wait("notes.created", ev.model_dump_json().encode())
```

```python
# src/notes_api/events/consumer.py
import asyncio, json
from aiokafka import AIOKafkaConsumer
from notes_api.config import settings

_task: asyncio.Task | None = None

async def _run():
    consumer = AIOKafkaConsumer(
        "notes.created",
        bootstrap_servers=settings.kafka_bootstrap_servers,
        group_id="notes-api",
    )
    await consumer.start()
    try:
        async for msg in consumer:
            payload = json.loads(msg.value)
            print(f"[event] note created: {payload['note_id']}")
    finally:
        await consumer.stop()

async def start_consumer():
    global _task
    _task = asyncio.create_task(_run())

async def stop_consumer():
    if _task:
        _task.cancel()
```

`@KafkaListener` → background asyncio task started in `lifespan`.

---

## 13. Exception Handling

### `controller/GlobalExceptionHandler.kt` → `core/exception_handlers.py`

```python
# src/notes_api/core/exceptions.py
class AppError(Exception):
    status_code = 500
    message = "Internal error"

class InvalidCredentials(AppError):
    status_code = 401
    message = "Invalid credentials"

class NoteNotFound(AppError):
    status_code = 404
    message = "Note not found"
```

```python
# src/notes_api/core/exception_handlers.py
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from notes_api.core.exceptions import AppError

def register_exception_handlers(app: FastAPI):
    @app.exception_handler(AppError)
    async def app_error_handler(_: Request, exc: AppError):
        return JSONResponse(status_code=exc.status_code, content={"error": exc.message})
```

Spring `@ControllerAdvice` → `@app.exception_handler()`. Same concept.

---

## 14. Migrations

### Flyway → Alembic

```bash
uv run alembic init alembic
uv run alembic revision --autogenerate -m "initial"
uv run alembic upgrade head
```

`alembic/env.py` — point at `Base.metadata`:
```python
from notes_api.db.base import Base
from notes_api import auth, notes  # import models so metadata sees them
target_metadata = Base.metadata
```

---

## 15. Testing

### `@SpringBootTest` → `pytest + TestClient`

```python
# tests/conftest.py
import pytest
from fastapi.testclient import TestClient
from notes_api.main import app

@pytest.fixture
def client():
    return TestClient(app)
```

```python
# tests/test_notes.py
def test_create_note_requires_auth(client):
    r = client.post("/notes", json={"title": "x"})
    assert r.status_code == 401
```

No slow Spring context boot. Tests run in <1s.

---

## 16. Docker

Compose stays identical (Postgres/Redis/Kafka). App container changes:

```dockerfile
FROM python:3.12-slim
WORKDIR /app
RUN pip install --no-cache-dir uv
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev
COPY src ./src
COPY alembic alembic.ini ./
EXPOSE 8080
CMD ["uv", "run", "uvicorn", "notes_api.main:app", "--host", "0.0.0.0", "--port", "8080"]
```

Memory ~60MB vs Spring ~400MB. Startup <1s vs ~5s.

---

## 17. Migration Order (suggested)

1. Scaffold `pyproject.toml`, `main.py`, Postgres connection, health route.
2. User model + Alembic initial migration.
3. Signup/login (password hash, JWT issue).
4. `get_current_user` dep + protected route.
5. Note CRUD.
6. Refresh tokens + SHA-256 hashing.
7. Redis + token blacklist on logout.
8. Kafka producer on note create.
9. Kafka consumer background task.
10. Exception handlers + middleware polish.
11. Dockerize + docker-compose.
12. Deploy (same ECS pattern, smaller container).

Each step maps to one existing Kotlin slice. Compare diffs side-by-side as you go.

---

## 18. Mindset Shifts

- **Explicit > magic.** No classpath scan. Wire deps via `Depends()`.
- **Async-first for IO.** `async def` routes, `await` DB/Redis/Kafka.
- **Types matter.** Use type hints everywhere. Run `mypy` or `pyright` in CI.
- **Pydantic = your Jackson + Bean Validation + mapper combined.**
- **SQLAlchemy sessions = short-lived.** One per request (via `Depends(get_db)`). No `@Transactional` — use `db.commit()` / `db.rollback()` explicitly, or a `with db.begin():` block.
- **No DI container.** You are the container. It's fine.
