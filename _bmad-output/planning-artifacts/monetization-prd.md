---
stepsCompleted: []
inputDocuments: ["_bmad-output/brainstorming/brainstorming-session-2026-04-29-000000.md", "MONETIZATION.md"]
workflowType: 'prd'
lastStep: ''
briefCount: 0
researchCount: 0
brainstormingCount: 1
projectDocsCount: 0
workflowStatus: 'active'
completedDate: ''
lastEdited: ''
editHistory: []
---

# Product Requirements Document - VRHub Monetization

**Author:** Garoh
**Date:** 2026-04-29

## Executive Summary

**Context:** VRHub is a standalone Android app for Meta Quest VR headsets. This PRD defines the monetization system for VRHub, enabling two premium tiers (Supporter at 5€ one-time, Lucky at 10€ one-time) with server-side license validation.

**Problem:** The app needs a secure, Quest-friendly payment and validation system. Payment providers were evaluated (Ko-fi, Patreon, Gumroad, LemonSqueezy). Ko-fi was chosen for its webhook-based push model and one-time purchase support, despite lacking OAuth capabilities.

**Solution:** Email + Magic Link flow where users enter their email in the app, receive a verification email with a magic link, are redirected to Ko-fi for purchase, and are automatically validated via webhook upon successful payment.

## Project Classification

**Technical Type:** Mobile App + Server Backend (VR Quest + VPS)
**Domain:** Gaming (VR Game Distribution) / Monetization
**Complexity:** Medium
**Project Context:** Greenfield - new monetization system for existing VRHub app

**Architecture:**
- **App:** Kotlin + Jetpack Compose (existing VRHub app)
- **Backend:** Rust + Axum on VPS OVH (2GB RAM, 30GB storage)
- **Database:** SQLite (single table for users)
- **Email:** Postfix send-only or SMTP relay service

## User Experience

### User Flows

#### New User Flow
1. User opens VRHub app → navigates to premium settings
2. User enters email address
3. App sends request to VPS `/init` endpoint
4. VPS creates pending user entry in SQLite with `status: pending`
5. VPS sends email with magic link (UUID v4, hashed, expires 1h)
6. User clicks magic link → opens purchase redirect page on VPS
7. Page redirects to `ko-fi.com/vrhub` (Ko-fi product page)
8. User completes purchase on Ko-fi
9. Ko-fi sends webhook POST to VPS `/webhook/kofi` with purchase details
10. VPS verifies `verification_token`, updates user status to `verified`, sets `tier`
11. App polls `/validate` endpoint → badge displayed, Lucky features unlocked

#### Existing User Flow (reinstall / new device)
1. User opens VRHub app → navigates to premium settings
2. User enters email address
3. VPS detects email exists → sends magic link for login
4. User clicks magic link → validated via `/login` endpoint
5. App receives valid response → premium access restored

### Key UX Decisions

| Decision | Rationale |
|----------|-----------|
| Email as identity | Cross-device, no password to remember, Quest-friendly |
| Magic link | No password entry on VR headset (frictionless) |
| Immediate badge | No app restart required after validation |
| 1h pending timeout | Prevents abandoned accounts from accumulating |
| Multiple devices | Same email, new magic link per device |

### UI States

1. **Default state** — No email entered, standard UI
2. **Pending state** — "Check your email to continue" message displayed
3. **Resend available** — If magic link not received after 1 minute
4. **Link expired** — "Link expired, request a new one" option
5. **Purchase complete** — "Welcome [Founder/Lucky]!" success screen
6. **Email mismatch** — Error screen if Ko-fi email differs from app email

## Functional Requirements

### FR1: Email Initiation
User can enter their email address in the VRHub app to initiate the premium purchase or login flow.

**Acceptance Criteria:**
- **Given** user is on premium settings screen
- **When** user enters valid email and submits
- **Then** app sends POST to `/init` with email, pending entry created, magic link email sent

### FR2: Magic Link Email
VPS sends an email containing a magic link when user initiates the flow.

**Acceptance Criteria:**
- **Given** user submitted email via `/init`
- **When** VPS receives request
- **Then** VPS sends email with magic link valid for 1 hour
- **And** magic link contains UUID v4 token (hashed in database)

### FR3: Purchase Redirect
Magic link opens a redirect page on VPS that sends user to Ko-fi product page.

**Acceptance Criteria:**
- **Given** user clicks magic link
- **When** VPS receives GET to `/verify/{token}`
- **Then** VPS serves redirect page to `ko-fi.com/vrhub`
- **And** token is marked as used (single-use)

### FR4: Webhook Processing
VPS receives and processes Ko-fi webhook to confirm purchase.

**Acceptance Criteria:**
- **Given** user completes Ko-fi purchase
- **When** Ko-fi sends POST to `/webhook/kofi`
- **Then** VPS validates `verification_token`
- **And** updates user status to `verified` with correct `tier` (supporter/lucky)
- **And** handles email mismatch gracefully (reject + notification email)

### FR5: License Validation
VRHub app validates user license against VPS server.

**Acceptance Criteria:**
- **Given** user is logged in or after magic link verification
- **When** app sends GET to `/validate` with email
- **Then** VPS returns `{valid: true/false, tier: "supporter"|"lucky"|null}`
- **And** app displays badge and enables Lucky features if valid

### FR6: Badge Display
Premium badge displays immediately after successful validation.

**Acceptance Criteria:**
- **Given** user is validated as premium
- **When** validation completes or app restarts
- **Then** golden/yellow badge displays next to VRHub title in sidebar
- **And** Lucky features are accessible without app restart

### FR7: Pending Cleanup
VPS automatically deletes pending entries older than 1 hour.

**Acceptance Criteria:**
- **Given** pending entry exists in database
- **When** entry is older than 1 hour
- **Then** cron job or timer deletes expired pending entries
- **And** user must restart the flow if they still want to purchase

### FR8: Rate Limiting
Email sending is rate-limited to prevent abuse.

**Acceptance Criteria:**
- **Given** user or attacker tries to send multiple emails
- **When** more than 3 email requests per email per hour
- **Then** VPS returns 429 Too Many Requests
- **And** no additional emails are sent

## Non-Functional Requirements

### Performance
- **NFR-P1:** Email delivery within 30 seconds of request
- **NFR-P2:** Webhook processing within 2 seconds
- **NFR-P3:** Validation response within 500ms
- **NFR-P4:** VPS RAM usage under 512MB (Rust + SQLite + Postfix on 2GB VPS)

### Reliability
- **NFR-R1:** Webhook retry handled by Ko-fi (server responds 200 or Ko-fi retries)
- **NFR-R2:** No data loss if VPS is down during purchase (Ko-fi retries when up)
- **NFR-R3:** Pending cleanup runs every 5 minutes via cron

### Security
- **NFR-S1:** Magic link token stored as BCrypt/Argon2 hash
- **NFR-S2:** Ko-fi webhook authenticated via `verification_token`
- **NFR-S3:** HTTPS enforced on all VPS endpoints
- **NFR-S4:** Email enumeration prevention (always return same message: "If email exists, link was sent")
- **NFR-S5:** Input validation on all endpoints (email format, SQL injection prevention)

### Usability
- **NFR-U1:** No password entry required on VR headset
- **NFR-U2:** Badge displays immediately after validation (no app restart)
- **NFR-U3:** Clear error messages for expired links, mismatched emails

## Architecture

### Server Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/init` | POST | Create pending user, send magic link email |
| `/verify/{token}` | GET | Validate token, redirect to Ko-fi purchase page |
| `/login` | GET | Validate existing user magic link |
| `/webhook/kofi` | POST | Receive Ko-fi purchase webhook |
| `/validate` | GET | Check if email has valid license |
| `/pending/cleanup` | POST | Internal: delete expired pending entries (cron) |

### Database Schema (SQLite)

```sql
CREATE TABLE users (
    email TEXT PRIMARY KEY,
    status TEXT NOT NULL,  -- 'pending' | 'verified' | 'revoked'
    tier TEXT,              -- 'supporter' | 'lucky' | NULL
    magic_link_hash TEXT,   -- BCrypt/Argon2 hash, expires 1h
    created_at INTEGER,     -- Unix timestamp
    verified_at INTEGER,   -- Unix timestamp (NULL if pending)
    revoked_at INTEGER     -- Unix timestamp (NULL if not revoked)
);
```

### Data Flow

```
VRHub App                    VPS OVH (Rust+Axum)              Ko-fi
     │                             │                            │
     │  POST /init {email}         │                            │
     ├────────────────────────────►│                            │
     │                             │  Create pending user        │
     │                             │  Send magic link email      │
     │  Email sent (magic link)     │                            │
     │◄────────────────────────────│                            │
     │                             │                            │
     │  Click link → GET /verify   │                            │
     ├────────────────────────────►│                            │
     │                             │  Redirect to Ko-fi          │
     │  Redirect to ko-fi.com/vrhub│                            │
     │◄────────────────────────────│                            │
     │                             │                            │
     │                      Purchase │                            │
     │◄────────────────────────────│──Webhook POST─────────────►│
     │                             │                            │
     │                             │  Verify + update BDD        │
     │                             │                            │
     │  GET /validate {email}       │                            │
     ├────────────────────────────►│                            │
     │                             │  Return {valid, tier}      │
     │  {valid: true, tier: lucky}  │                            │
     │◄────────────────────────────│                            │
     │                             │                            │
     │  ★ LUCKY BADGE DISPLAYED ★  │                            │
```

### Infrastructure

| Resource | Specification |
|----------|---------------|
| VPS | OVH 2GB RAM / 30GB storage, Ubuntu 22.04 LTS |
| Domain | `sunshine-aio.com` with Cloudflare CNAME to VPS |
| Database | SQLite (single file, zero config) |
| Email | Postfix send-only OR SMTP relay (Brevo/SendGrid free tier) |
| Rust | Axum framework (~50MB RAM footprint) |

## Open Questions

1. **Ko-fi retry behavior** — Exact retry interval and count? (To document for operations)
2. **SMTP choice** — Postfix on VPS or external SMTP relay service?
3. **Ko-fi Terms compliance** — Is redirect-based purchase flow allowed by Ko-fi?
4. **Chargeback handling** — Manual revocation only, accepted risk for small project

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Chargeback without notification | Medium | Medium | Manual monitoring, "no refund" clause |
| Email goes to spam | Low | Low | SPF/DKIM setup, warn user in UI |
| VPS RAM exhaustion | Low | High | Monitor RAM usage, SQLite is lightweight |
| Email enumeration attack | Low | Low | Unified response message |

## Success Criteria

### User Success
- User can purchase Supporter or Lucky tier with minimal friction
- Badge displays immediately after successful purchase
- User can access premium features on reinstall or new device
- Zero password entry required on VR headset

### Technical Success
- Webhook processing < 2 seconds
- VPS RAM usage < 512MB with all services running
- Zero webhook loss (Ko-fi retry mechanism)
- Pending cleanup runs reliably every 5 minutes
