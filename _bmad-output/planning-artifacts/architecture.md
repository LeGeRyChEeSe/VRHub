---
stepsCompleted: [1, 2, 3, 4, 5]
inputDocuments: ["_bmad-output/planning-artifacts/monetization-prd.md", "_bmad-output/brainstorming/brainstorming-session-2026-04-29-000000.md", "MONETIZATION.md"]
workflowType: 'architecture'
project_name: 'vrhub-monetization'
user_name: 'Garoh'
date: '2026-04-29'
---

# Architecture Decision Document - VRHub Monetization

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements (8 total):**
- FR1-FR2: Email initiation + Magic link email delivery
- FR3-FR4: Purchase redirect flow + Ko-fi webhook processing
- FR5-FR6: License validation + Badge display
- FR7-FR8: Pending cleanup + Rate limiting

**Non-Functional Requirements:**
- Performance: Email <30s, webhook <2s, validation <500ms, RAM <512MB
- Security: BCrypt/Argon2 hash, HTTPS, email enumeration prevention
- Reliability: Ko-fi retry mechanism, no data loss on VPS downtime

### Scale & Complexity

- Primary domain: Backend API + Mobile app integration
- Complexity level: Medium
- Estimated components: 6 endpoints, 1 SQLite table, email service, cron job

### Technical Constraints

- VPS OVH 2GB RAM / 30GB storage (ressources limitées)
- Ubuntu 22.04 LTS, Domain via Cloudflare
- Ko-fi external webhook dependency

### Cross-Cutting Concerns

- Email deliverability and spam prevention
- Rate limiting for abuse prevention
- HTTPS enforcement
- Token security (hash-based magic links)

## Starter Template Evaluation

### Primary Technology Domain

API/Backend using Rust + Axum based on PRD requirements

### Selected Approach: Build from Scratch

**Rationale:** Project has specific requirements (webhook handling, rate limiting, email sending, token hashing) that benefit from explicit crate selection rather than relying on a template's defaults.

### Core Dependencies

| Crate | Version | Purpose |
|-------|---------|---------|
| axum | 0.2.x | HTTP framework |
| sqlx | 0.8.x | SQLite database with compile-time checks |
| argon2 | 0.5.x | Token hashing (memory-hard, better than bcrypt) |
| lettre | 0.10.x | Email SMTP sending |
| uuid | 1.x | Magic link token generation (v4) |
| tokio | 1.x | Async runtime |
| serde | 1.x | Serialization |
| axum-governor | 0.4.x | Rate limiting middleware |

### Project Structure

```
vrhub-monetization/
├── Cargo.toml
├── src/
│   ├── main.rs              # Entry point
│   ├── config.rs             # Configuration
│   ├── db.rs                # SQLite connection
│   ├── models.rs            # Data models (User)
│   ├── handlers/
│   │   ├── mod.rs
│   │   ├── init.rs          # /init endpoint
│   │   ├── verify.rs        # /verify/{token} endpoint
│   │   ├── login.rs         # /login endpoint
│   │   ├── webhook.rs       # /webhook/kofi endpoint
│   │   ├── validate.rs      # /validate endpoint
│   │   └── cleanup.rs      # /pending/cleanup endpoint
│   ├── services/
│   │   ├── mod.rs
│   │   ├── email.rs         # Email sending service
│   │   └── license.rs       # License validation logic
│   └── middleware/
│       ├── mod.rs
│       └── rate_limit.rs    # Rate limiting
├── migrations/              # SQLx migrations
└── tests/                  # Integration tests
```

### Note

Project initialization using `cargo init` with manual Cargo.toml setup should be the first implementation task.

## Step 4: Core Architectural Decisions

### API & Communication Patterns

**Decision:** RFC 7807 Problem Details for all error responses

**Selected Approach:** Structured error responses with `type`, `title`, `status`, `detail`, `instance` fields.

**Example:**
```json
{
  "type": "https://vrhub.example.com/errors/rate-limited",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "Rate limit exceeded for email sending. Max 3 per hour.",
  "instance": "/init"
}
```

**Rationale:** Standardized format enables client-side error handling, type-based UI messages, and is required for the VRHub app integration.

### Configuration Management

**Decision:** Static config.rs with Rust types

**Selected Approach:** `config.rs` module with typed configuration struct, loaded from environment variables at startup.

```rust
#[derive(Debug, Clone)]
pub struct Config {
    pub database_url: String,
    pub smtp_host: String,
    pub smtp_port: u16,
    pub smtp_username: String,
    pub smtp_password: Secret<String>,
    pub from_email: String,
    pub app_url: String,
    pub kofi_verification_token: Secret<String>,
}

impl Config {
    pub fn from_env() -> Result<Self> { ... }
}
```

**Rationale:** Type-safe, compile-time verification, no runtime parsing errors, 12-factor app compliant.

### Logging Strategy

**Decision:** `tracing` + file rotation

**Selected Approach:** Structured logging with `tracing` crate, JSON logs to file, rotation via `tracing-appender`.

```rust
// Initialisation
let file_appender = RollingFileAppender::new(
    Rotation::DAILY,
    "/var/log/vrhub-monetization",
    "vrhub.log",
);
let (non_blocking, _guard) = non_blocking(file_appender);
tracing_subscriber::fmt()
    .with_writer(non_blocking)
    .with_ansi(false)
    .json()
    .init();
```

**Rationale:** `tracing` is the standard in Rust async contexts, JSON format enables log aggregation, rotation prevents disk exhaustion.

### Infrastructure & Deployment

**Decision:** Docker container

**Selected Approach:** Multi-stage build, `debian:bookworm-slim` runtime, non-root user, health check endpoint.

**Container Specs:**
| Component | Specification |
|-----------|---------------|
| Build image | `rust:bookworm-slim` |
| Runtime image | `debian:bookworm-slim` |
| User | `non-root` (security) |
| Health check | `GET /health` returning 200 |
| Signal handling | Graceful shutdown on SIGTERM |

**Infrastructure:**
```
Internet → Cloudflare (HTTPS) → cloudflared → Rust app (localhost:8080)
```

**Clarification Cloudflare Tunnel:**
- **No nginx needed** — Cloudflare Tunnel provides HTTPS and routes directly to the container
- cloudflared connects to localhost:8080 where the Rust app listens
- Works with existing Cloudflare Tunnel setup on the VPS

**Rationale:** Consistent environment, no host dependency, easy SSH deployment.

## Step 5: Implementation Patterns

### Error Handling Pattern

```rust
// errors.rs
pub struct AppError {
    pub status: StatusCode,
    pub error_type: ErrorType,
    pub detail: String,
}

#[derive(Debug)]
pub enum ErrorType {
    ValidationError,
    NotFound,
    RateLimited,
    InternalError,
    EmailMismatch,
}

impl Into<response::Response<Body>> for AppError {
    fn into(self) -> response::Response<Body> {
        let body = Json(problem_details::ProblemDetails {
            type_: format!("https://vrhub.example.com/errors/{}", self.error_type),
            title: self.status.as_str(),
            status: self.status.as_u16(),
            detail: self.detail,
            instance: request.uri().path().to_string(),
        });
        (self.status, body).into_response()
    }
}
```

### Rate Limiting Pattern

```rust
// middleware/rate_limit.rs
use axum_governor::GovernorMiddleware;

pub fn rate_limiter() -> GovernorMiddleware {
    GovernorMiddleware::builder()
        .key_extractor(EmailHeaderKeyExtractor)
        .limit(3)
        .period(std::time::Duration::from_secs(3600))
        .use_initial_headers()
        .build()
}
```

### Request/Response Flow

```
Client Request
      ↓
axum_governor (rate limit check)
      ↓
extract Json body / path params
      ↓
Handler (validates, processes)
      ↓
Service Layer (business logic)
      ↓
db::check_license() / email::send()
      ↓
Response (Json or redirect)
```

### Database Query Pattern

```rust
// db.rs
pub async fn create_pending_user(
    pool: &SqlitePool,
    email: &str,
    token_hash: &str,
) -> Result<(), sqlx::Error> {
    sqlx::query!(
        "INSERT INTO users (email, status, magic_link_hash, created_at)
         VALUES (?, 'pending', ?, ?)",
        email,
        token_hash,
        chrono::Utc::now().timestamp()
    )
    .execute(pool)
    .await?;
    Ok(())
}

pub async fn verify_token(pool: &SqlitePool, token_hash: &str) -> Result<Option<String>, sqlx::Error> {
    let row = sqlx::query!(
        "SELECT email FROM users WHERE magic_link_hash = ? AND status = 'pending' AND created_at > ?",
        token_hash,
        chrono::Utc::now().timestamp() - 3600
    )
    .fetch_optional(pool)
    .await?;
    Ok(row.map(|r| r.email))
}
```

### Health Check Endpoint

```rust
// handlers/health.rs
pub async fn health() -> impl IntoResponse {
    Json(serde_json::json!({
        "status": "healthy",
        "timestamp": chrono::Utc::now().to_rfc3339()
    }))
}
```

### Email Service Pattern

```rust
// services/email.rs
pub async fn send_magic_link(email: &str, token: &str, app_url: &str) -> Result<()> {
    let magic_link = format!("{}/verify/{}", app_url, token);

    let email = Message::builder()
        .from("VRHub <noreply@vrhub.example.com>".parse()?)
        .to(email.parse()?)
        .subject("Your VRHub Magic Link")
        .multipart(
            MultiPart::alternative_plain(
                format!("Click here to verify: {}", magic_link)
            ),
        )
        .singlepart(
            SinglePart::builder(MediaType::TextHtml)
                .body(format!("<a href=\"{}\">Verify your email</a>", magic_link))
        )?;

    SmtpTransport::starttls_relay("smtp.example.com")?
        . credentials(creds)
        .transport()
        .send(email)
        .await?;
    Ok(())
}
```

---

**Next step:** Epics & Stories creation — decomposition of the PRD into actionable implementation units.
