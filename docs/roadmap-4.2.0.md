# VRHub Roadmap — v4.2.0 (Stats Collection & Privacy)

> Milestone: **4.2.0**  
> Epic: **#35**  
> Target: Anonymous statistics collection with user-consent management.

---

## Overview

This release introduces a complete **Stats Collection & Privacy** subsystem that collects anonymized usage data (installed games, favorites, user tier) while strictly respecting user consent.

All requirements are driven by:
- [`CLIENT_SPEC.md`](./CLIENT_SPEC.md) — API contract & privacy model
- [`CLIENT_IMPLEMENTATION.md`](./CLIENT_IMPLEMENTATION.md) — Implementation guide

---

## Milestone

- **GitHub Milestone:** [`4.2.0`](https://github.com/LeGeRyChEeSe/VRHub/milestone/1)

---

## Issues

| # | Title | Layer | Status |
|---|-------|-------|--------|
| **#35** | **[Epic] Stats Collection & Privacy Foundation** | Epic | 🔵 Open |
| **#36** | **[Network] Implement StatsApiService and data models** | Network | 🔵 Open |
| **#37** | **[Data] Implement ConsentPreferences with DataStore** | Data | 🔵 Open |
| **#38** | **[UI] First-launch consent dialog** | UI | 🔵 Open |
| **#39** | **[UI] Add Privacy section in Settings** | UI | 🔵 Open |
| **#40** | **[Service] Create StatsCollector and integrate with MainRepository** | Service | 🔵 Open |
| **#41** | **[Worker] StatsCollectionWorker with WorkManager** | Background | 🔵 Open |
| **#42** | **[Tests] Unit and integration tests for Stats Collection** | QA | 🔵 Open |

---

## Architecture

```
┌─────────────────────┐                    ┌─────────────────────┐
│   VRHub Android App │                    │  vrhub-monetization │
│                     │                    │    (Rust Server)    │
│  • StatsCollector   │ ── POST /stats ──→ │                     │
│  • ConsentPrefs     │                    │  • Stats aggregation│
│  • DataStore        │ ←── GET /user/tier │  • Discord webhooks │
│                     │                    │  • Lucky role API   │
└─────────────────────┘                    └─────────────────────┘
```

---

## File Additions

```
app/src/main/java/com/vrhub/
├── network/
│   ├── StatsApiService.kt       (NEW)
│   └── StatsModels.kt           (NEW)
├── data/
│   └── ConsentPreferences.kt    (NEW)
├── ui/components/
│   └── ConsentDialog.kt         (NEW)
└── worker/
    └── StatsCollectionWorker.kt (NEW)
```

---

## Acceptance Criteria

- [ ] Consent dialog is shown on first app launch (one-time)
- [ ] User can change consent in **Settings > Privacy**
- [ ] No data is sent if consent is declined
- [ ] Stats are collected periodically (daily) via WorkManager when consent is granted
- [ ] Test coverage is > 80% on the stats module

---

## GitHub Project

- **Project Board:** [Roadmap 4.2.0](https://github.com/users/LeGeRyChEeSe/projects/1) *(Public)*
- **Repository link:** [VRHub / Projects](https://github.com/LeGeRyChEeSe/VRHub/projects)
- All 8 issues have been added to the board.

### Sprint status (from `sprint-status.yaml`)

| Item | Epic | Project Status |
|------|------|----------------|
| #35 Epic | stats-epic-global | **In Progress** |
| #37 DataStore | stats-epic-1 | **In Progress** |
| #38 Consent Dialog | stats-epic-1 | Todo |
| #39 Privacy Settings | stats-epic-1 | Todo |
| #36 Network API | stats-epic-2 | Todo |
| #40 Service | stats-epic-2 | Todo |
| #41 Worker | stats-epic-3 | Todo |
| #42 Tests | stats-epic-3 | Todo |

### Custom fields
- **Epic** — maps each item to its `stats-epic-*` from the sprint plan
- **Status** — synchronized with `sprint-status.yaml`

---

*Generated on 2026-05-11*
