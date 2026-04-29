---
stepsCompleted: ['step-01-validate-prerequisites', 'step-02-design-epics', 'step-03-create-stories', 'step-04-final-validation']
inputDocuments:
  - '_bmad-output/planning-artifacts/monetization-prd.md'
  - '_bmad-output/planning-artifacts/architecture.md'
  - '_bmad-output/brainstorming/brainstorming-session-2026-04-29-000000.md'
workflowType: 'epics'
project_name: 'vrhub-monetization'
user_name: 'Garoh'
date: '2026-04-29'
---

# VRHub Monetization - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for the VRHub monetization system. The system enables two premium tiers (Supporter 5€, Lucky 10€) with server-side license validation via email + magic link + Ko-fi webhook flow.

## Requirements Summary

**8 Functional Requirements:**
- FR1: Email initiation from VRHub app
- FR2: Magic link email delivery (UUID v4, Argon2 hash, 1h expiry)
- FR3: Purchase redirect to Ko-fi product page
- FR4: Ko-fi webhook processing with verification_token
- FR5: License validation via /validate endpoint
- FR6: Badge display immediate after validation
- FR7: Pending cleanup (cron, 1h timeout)
- FR8: Rate limiting (3 per email per hour)

**6 Endpoints:**
- `POST /init` - Create pending user, send magic link
- `GET /verify/{token}` - Validate token, redirect to Ko-fi
- `GET /login` - Validate existing user magic link
- `POST /webhook/kofi` - Receive Ko-fi purchase webhook
- `GET /validate` - Check if email has valid license
- `POST /pending/cleanup` - Delete expired pending entries

---

## Epic 1: Project Foundation

**Goal:** Initialize Rust project with all dependencies, structure, and core infrastructure.

### Story 1.1: Initialize Rust Project
**As a:** Developer  
**I want to:** Initialize the Rust project with proper Cargo.toml and dependencies  
**So that:** The codebase is ready for implementation.

**Acceptance Criteria:**
- [ ] Cargo.toml created with all dependencies (axum 0.2.x, sqlx 0.8.x, argon2 0.5.x, lettre 0.10.x, uuid 1.x, tokio 1.x, serde 1.x, axum-governor 0.4.x, tracing, tracing-appender)
- [ ] Project structure follows architecture spec: src/main.rs, config.rs, db.rs, models.rs, handlers/, services/, middleware/
- [ ] Cargo build succeeds without errors
- [ ] Git repository initialized with .gitignore for target/, .env, logs/

### Story 1.2: Configure Logging System
**As a:** Developer  
**I want to:** Set up structured logging with file rotation  
**So that:** Logs are persistent and searchable.

**Acceptance Criteria:**
- [ ] tracing crate configured with JSON formatter
- [ ] tracing-appender for daily rotating log files in /var/log/vrhub-monetization/
- [ ] Non-blocking writer to prevent I/O blocking
- [ ] Log level configurable via RUST_LOG env var
- [ ] Logs include: timestamp, level, target, message, request_id

### Story 1.3: Create Health Check Endpoint
**As a:** DevOps  
**I want to:** Have a health endpoint for container orchestration  
**So that:** Docker health check and load balancer can verify service status.

**Acceptance Criteria:**
- [ ] `GET /health` returns 200 with `{"status": "healthy", "timestamp": "..."}`
- [ ] Endpoint does not require authentication
- [ ] Response time < 50ms

### Story 1.4: Setup SQLite Database
**As a:** Developer  
**I want to:** Initialize SQLite database with users table  
**So that:** License data can be persisted.

**Acceptance Criteria:**
- [ ] SQLx migrations created for users table
- [ ] Schema: email (PK), status, tier, magic_link_hash, created_at, verified_at, revoked_at
- [ ] Compile-time query verification works
- [ ] Database file stored at configurable path

---

## Epic 2: Email Initiation & Magic Link Flow

**Goal:** Implement FR1 (email initiation) and FR2 (magic link email delivery).

### Story 2.1: Email Initiation Endpoint
**As a:** VRHub app  
**I want to:** POST my email to /init and trigger magic link flow  
**So that:** I can start the premium purchase or login process.

**Acceptance Criteria:**
- [ ] `POST /init` accepts `{"email": "user@example.com"}`
- [ ] Email validated (format check, RFC 5322)
- [ ] Rate limiting check: max 3 requests per email per hour (return 429 if exceeded)
- [ ] If email exists with `status: verified` → send login magic link (Story 3.1)
- [ ] If email exists with `status: pending` → resend existing magic link
- [ ] If new email → create `status: pending` entry with UUID v4 token (hashed with Argon2)
- [ ] Return 200 with `{"message": "If email exists, link was sent"}` (email enumeration prevention)

### Story 2.2: Magic Link Email Delivery
**As a:** User  
**I want to:** Receive an email with a magic link after initiating  
**So that:** I can complete verification on another device.

**Acceptance Criteria:**
- [ ] Email sent via SMTP (lettre crate)
- [ ] From address: noreply@vrhub.sunshine-aio.com (configurable)
- [ ] Subject: "Your VRHub Magic Link"
- [ ] Email contains magic link: `https://vrhub.sunshine-aio.com/verify/{uuid}`
- [ ] Magic link token stored as Argon2 hash (not plaintext)
- [ ] Token expires in 1 hour (stored with created_at timestamp)
- [ ] Email delivery attempted within 30 seconds of /init call (NFR-P1)

### Story 2.3: Rate Limiting Implementation
**As a:** System  
**I want to:** Prevent email abuse via rate limiting  
**So that:** We don't become a spam source or DoS target.

**Acceptance Criteria:**
- [ ] axum-governor middleware configured for /init endpoint
- [ ] Limit: 3 emails per unique email address per hour
- [ ] Return RFC 7807 error: `{"type": "rate-limited", "title": "429", "status": 429, "detail": "...", "instance": "/init"}`
- [ ] Rate limit state persisted in memory (acceptable for single-instance deployment)

---

## Epic 3: Purchase Redirect & Ko-fi Webhook

**Goal:** Implement FR3 (purchase redirect) and FR4 (webhook processing).

### Story 3.1: Magic Link Verification & Redirect
**As a:** User  
**I want to:** Click magic link and be redirected to Ko-fi purchase  
**So that:** I can complete my premium purchase.

**Acceptance Criteria:**
- [ ] `GET /verify/{token}` endpoint
- [ ] Token looked up by hash (Argon2 verify)
- [ ] If valid and not expired (< 1h) → mark token as used (single-use), redirect to `https://ko-fi.com/vrhub`
- [ ] If invalid or expired → return error page with "Link expired, request a new one"
- [ ] User redirected to Ko-fi for purchase

### Story 3.2: Login Verification Endpoint
**As a:** Existing premium user  
**I want to:** Click magic link and be validated without purchase  
**So that:** I can restore premium access on reinstall/new device.

**Acceptance Criteria:**
- [ ] `GET /login` accepts token via query param or header
- [ ] If token valid and user exists with `status: verified` → return success (enables app validation)
- [ ] If token valid but status is `pending` → redirect to /verify/{token} for purchase
- [ ] Response indicates if user is premium and their tier

### Story 3.3: Ko-fi Webhook Processing
**As a:** Ko-fi platform  
**I want to:** POST purchase notifications to VRHub server  
**So that:** Verified purchases automatically grant premium access.

**Acceptance Criteria:**
- [ ] `POST /webhook/kofi` endpoint
- [ ] Verify `verification_token` from Ko-fi matches configured secret (NFR-S2)
- [ ] Extract email, type (Shop Order), tier from webhook payload
- [ ] If Supporter item purchased → set tier = "supporter"
- [ ] If Lucky item purchased → set tier = "lucky"
- [ ] Update user status to `verified` with correct tier
- [ ] Handle email mismatch: if webhook email ≠ pending email → reject silently, send notification to webhook email
- [ ] Return 200 within 2 seconds (NFR-P2)

### Story 3.4: Tier Detection from Ko-fi Items
**As a:** System  
**I want to:** Determine which tier was purchased from Ko-fi webhook  
**So that:** Users get correct tier assignment.

**Acceptance Criteria:**
- [ ] Parse `shop_items` array from Ko-fi webhook payload
- [ ] Each item has `direct_link_code` identifying the product
- [ ] Supporter item: `vrhub-supporter` tier, 5€
- [ ] Lucky item: `vrhub-lucky` tier, 10€
- [ ] If user purchases both → upgrade to Lucky (higher tier wins)
- [ ] Log purchase details for auditing

---

## Epic 4: License Validation & Badge Display

**Goal:** Implement FR5 (license validation) and FR6 (badge display - backend only).

### Story 4.1: License Validation Endpoint
**As a:** VRHub app  
**I want to:** GET /validate with my email  
**So that:** I know if my license is valid and what tier I have.

**Acceptance Criteria:**
- [ ] `GET /validate?email=user@example.com` endpoint
- [ ] Query database for email with `status: verified`
- [ ] Return JSON: `{"valid": true/false, "tier": "supporter"|"lucky"|null}`
- [ ] Response time < 500ms (NFR-P3)
- [ ] If email not found or not verified → return `{"valid": false, "tier": null}`
- [ ] Email validation before DB query

### Story 4.2: Badge Display (App Side)
**As a:** User  
**I want to:** See my premium badge immediately after validation  
**So that:** I know my purchase was successful.

**Acceptance Criteria:**
- [ ] App polls /validate after magic link click
- [ ] If valid=true and tier exists → display golden/yellow badge
- [ ] Badge visible without app restart (NFR-U2)
- [ ] Lucky features unlocked immediately upon valid tier detection

---

## Epic 5: Maintenance Operations

**Goal:** Implement FR7 (pending cleanup) and FR8 (rate limiting infrastructure).

### Story 5.1: Pending Entry Cleanup
**As a:** System  
**I want to:** Delete pending entries older than 1 hour  
**So that:** Abandoned accounts don't accumulate.

**Acceptance Criteria:**
- [ ] `POST /pending/cleanup` endpoint (internal, cron-triggered)
- [ ] Delete all rows where `status = 'pending'` AND `created_at < now - 3600 seconds`
- [ ] Called every 5 minutes via cron (NFR-R3)
- [ ] Log cleanup statistics: X entries deleted
- [ ] No authentication required (internal endpoint, protected by network)

### Story 5.2: Cron Job Setup
**As a:** DevOps  
**I want to:** Schedule pending cleanup to run automatically  
**So that:** Maintenance happens without manual intervention.

**Acceptance Criteria:**
- [ ] Cron entry: `*/5 * * * * curl -X POST http://localhost:8080/pending/cleanup`
- [ ] Or: internal timer in Rust app using tokio::time
- [ ] Cleanup runs even if no entries to delete (idempotent)

### Story 5.3: Rate Limit State Management
**As a:** System  
**I want to:** Track and enforce rate limits per email  
**So that:** Abuse is prevented.

**Acceptance Criteria:**
- [ ] In-memory rate limit state using axum-governor
- [ ] Key extractor uses email from request body
- [ ] Limits tracked: 3 per hour per email (Story 2.3)
- [ ] State cleared after 1 hour window expires
- [ ] Graceful handling if rate limit state grows large

---

## Epic 6: Infrastructure & Deployment

**Goal:** Containerize application and prepare for Cloudflare Tunnel deployment.

### Story 6.1: Dockerfile Creation
**As a:** DevOps  
**I want to:** Have a Docker container for the application  
**So that:** Consistent deployment across environments.

**Acceptance Criteria:**
- [ ] Multi-stage build: rust:bookworm-slim for compilation, debian:bookworm-slim for runtime
- [ ] Non-root user in container (security)
- [ ] Health check configured: `curl -f http://localhost:8080/health`
- [ ] Graceful shutdown on SIGTERM
- [ ] Container size minimized (< 200MB runtime image)

### Story 6.2: Cloudflare Tunnel Configuration
**As a:** DevOps  
**I want to:** Expose the application via Cloudflare Tunnel  
**So that:** HTTPS is handled without nginx.

**Acceptance Criteria:**
- [ ] cloudflared connects to localhost:8080
- [ ] No nginx or reverse proxy needed
- [ ] HTTPS auto-managed by Cloudflare
- [ ] Domain: vrhub.sunshine-aio.com

### Story 6.3: Environment Configuration
**As a:** DevOps  
**I want to:** Configure application via environment variables  
**So that:** Secrets are not hardcoded.

**Acceptance Criteria:**
- [ ] All secrets via environment: DATABASE_URL, SMTP_HOST, SMTP_USERNAME, SMTP_PASSWORD, KOFIVFY_TOKEN, APP_URL
- [ ] From email configurable: FROM_EMAIL
- [ ] config.rs loads from env vars with clear error messages if missing

### Story 6.4: Docker Compose for Local Development
**As a:** Developer  
**I want to:** Run the full stack locally for testing  
**So that:** I can develop without VPS.

**Acceptance Criteria:**
- [ ] docker-compose.yml with: app container, cloudflared tunnel
- [ ] Volumes for: SQLite database, log files
- [ ] .env file template with all required variables
- [ ] `docker-compose up` starts fully functional stack

---

## Story Index

| Epic | Story | Priority |
|------|-------|----------|
| 1.1 | Initialize Rust Project | P0 |
| 1.2 | Configure Logging System | P1 |
| 1.3 | Create Health Check Endpoint | P1 |
| 1.4 | Setup SQLite Database | P0 |
| 2.1 | Email Initiation Endpoint | P0 |
| 2.2 | Magic Link Email Delivery | P0 |
| 2.3 | Rate Limiting Implementation | P1 |
| 3.1 | Magic Link Verification & Redirect | P0 |
| 3.2 | Login Verification Endpoint | P1 |
| 3.3 | Ko-fi Webhook Processing | P0 |
| 3.4 | Tier Detection from Ko-fi Items | P0 |
| 4.1 | License Validation Endpoint | P0 |
| 4.2 | Badge Display (App Side) | P2 (app-side) |
| 5.1 | Pending Entry Cleanup | P1 |
| 5.2 | Cron Job Setup | P1 |
| 5.3 | Rate Limit State Management | P1 |
| 6.1 | Dockerfile Creation | P0 |
| 6.2 | Cloudflare Tunnel Configuration | P1 |
| 6.3 | Environment Configuration | P1 |
| 6.4 | Docker Compose for Local Development | P2 |

**P0 = Must have for MVP | P1 = Should have | P2 = Nice to have**