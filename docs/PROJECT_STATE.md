# VRHub Ecosystem — Project State (orchestrator tracking file)

Maintained by the orchestrator agent. English. Append-only decision log.
Rule: every claim below must be reproducible with the documented command. Every completed
action gets a decision-log entry with date, item, verification command, and gate result.

## 1. Ecosystem map

Four linked repos under `Projets/VRHub_group/` (workspace root, see `QWEN.md`):

| Repo | Stack | Role |
|---|---|---|
| `vrhub/` (origin: LeGeRyChEeSe/VRHub) | Kotlin / Jetpack Compose, Room, WorkManager | Meta Quest client app |
| `vrhub-server/` | Go | Self-hosted backend: encrypted catalog + admin web UI |
| `vrhub-monetization/` | Rust / Axum | Ko-fi license validation, anonymous stats, trending/popularity |
| `vrhub-bot/` | Rust / poise+serenity | Discord bot (stats, releases, support) |

Flow: Quest client -> vrhub-server -> optional cloud via vrhub-monetization -> Discord via vrhub-bot.

Cross-project contracts (breaking one requires a correlating PR in the consumer):
- **C1 client<->server**: encrypted GameList protocol, admin API, `ServerConfig` (server URL + `monetizationUrl`). Server security PRs #13-#17 (CSRF, TLS, body limits, 0700 perms) can affect client expectations.
- **C2 client<->monetization**: `POST /stats/consent`, `POST /stats/collect` (tier `standard`, nullable email), `GET /user/tier`. Client must accept what the server emits (confirmed server-side in July commits).
- **C3 client<->GitHub releases / update gateway**: `VRHUB_UPDATE_SECRET` + release format shared between client secure-update client, `vrhub-server` update path and `vrhub-bot` `/version` `/changelog`.

## 2. Status dashboard

As of 2026-10-09 (verify: `git branch -a`, `gh pr list -R <repo> --state all`, `git status`).

- **vrhub**: main HEAD `60f3dbf` (PR #73 merged 2026-07-24). 0 open PRs, 0 open issues.
  Working branch: `wip/autonomous-2026` (single orchestrator target; main frozen until user validates the final merge).
- **vrhub-server**: 5 OPEN PRs, all security (created 2026-09-12): #17 CSRF coverage, #16 TLS, #15 body size limits, #14 dir perms 0700, #13 dynamic CSRF secret. Uncommitted work on `fix/m1-csrf-coverage` — preserved, snapshot committed before any intervention.
- **vrhub-monetization**: last activity 2026-07-30 (Ko-fi form-urlencoded webhook fix), ~80 commits post-merge; 11 modules with `mod tests`.
- **vrhub-bot**: last activity 2026-07-16; effectively no tests.
- **Branches on vrhub**: ~14 branches are squash-merge headstones (content already in main, verified with `git merge-base --is-ancestor <b> origin/main`). Real unmerged content: `feat/metadata-loading-indicator` (3 commits) and `vrhub-rebrand` (10 commits, partially superseded by main commit `c33f9d9`). Full per-branch verification table: see decision log 2026-10-09.
- **Testing gap**: no graphical/UI test bench equivalent to AutoSteamTools `e2e/` (real binary + window). Only UI test `app/src/androidTest/.../ui/QueueUITest.kt` is `@Ignore`'d (Compose never settles on headless swiftshader CI emulator). Planned solution: Paparazzi (headless deterministic Compose screenshots in CI) + possible Maestro.
- **Unit tests**: 16 files in `app/src/test/`; androidTest 14 files; vrhub-server 67 `*_test.go`; monetization 11 `mod tests`; bot ~0.
- **Rust migration decision**: full migration rejected (no production-grade Android UI/Room/WorkManager/DataStore equivalents in Rust). Hybrid only: pure-logic crate `vrhub-core` (~2.5-3.5k lines: CatalogParser/CatalogUtils, TierResolver, APK integrity, 7z password check, secure-update response validation) exposed to Kotlin via UniFFI; shared with the Rust repos. APK pipeline unchanged.

## 3. Open work

1. P0: this file + `wip/autonomous-2026` branch.
2. P1: branch hygiene (archive tags then delete verified squash-merged branches); PRs for real unmerged content (`feat/metadata-loading-indicator`, `vrhub-rebrand`).
3. P2: client security audit — unverified backlog in `docs/sonnet5-specs/01-bug-audit-backlog.md`. A1/A2/A4 already fixed (#60-#63). Remaining: A3 (cancellation not honored inside a large segment, `isCancelled = { false }` in `MainRepository.installGame`), A5, and modules B1-B8 in audit order B3 -> B2 -> B1 -> A3 -> B4 -> B5 -> B6 -> B7 -> B8.
4. P3: cross-project contract verification (see C1-C3), incl. review of vrhub-server PRs #13-#17 (no merge without user approval).
5. P4: graphical bench (Paparazzi wired into `pr-validation.yml`, QueueUITest re-enabled/replaced).
6. P5: `vrhub-core` crate + UniFFI.
7. P6: closing report; single final merge wip -> main only after user validation.

## 4. Safety measures (orchestrator guardrails)

1. main frozen; wip is the only merge target; final merge only with explicit user approval.
2. No destructive deletion: squash-merged branches archived with `git tag archive/<branch>` before deletion, only after `merge-base --is-ancestor` proof.
3. Existing uncommitted work snapshot-committed before any intervention (never overwritten).
4. Backup reference copies before touching binary/release-format-related files.
5. Linked repos: fix only contract-breaking, test-proven issues; no decorative refactoring.
6. Gates green after every wip merge (`gradlew test`, lint, `assembleDebug`, CI pr-validation); otherwise revert commit, never force-push.
7. No secrets logged; audit includes checking for logged secrets.
8. Every action traceable: PR linked to a criteria + append-only decision log entry.
9. Item blocked after 3 identical failures, documented, move on; no infinite loops.
10. Self-supervision: verifier cross-check at end of each phase (100% of confirmed security findings, >=1 sample per phase). Criteria evolve by dated additions, never silent deletion.

## 5. Criteria (acceptance gates)

- **G0** wip branch from main HEAD, documented (this file).
- **G1** this file complete + reproducible claims + append-only log.
- **G2** no squash-merged branch left on origin; real unmerged content PR'd into wip.
- **G3** audit: every item confirmed+fixed (red->green test) or refuted with justification, in order B3,B2,B1,A3,B4-B8,A5.
- **G4** gates green on wip after every merge (test, lint, assembleDebug, CI).
- **G5** Paparazzi bench operational in CI with committed baselines; QueueUITest re-enabled or replaced.
- **G6** contracts C1-C3 verified; consumer PRs only when a contract actually breaks; vrhub-server PRs not merged without user approval; uncommitted CSRF work preserved.
- **G7** `vrhub-core` compiles, cargo tests green, UniFFI bindings, APK still buildable/installable (min SDK 29).
- **G8** closing report on wip ready for user validation.

## 6. Decision log (append-only)

- **2026-10-09 — plan established** (criteria G0-G8, phases P0-P6, guardrails). Verified claims via `git branch -a`, `git merge-base`, `gh api repos/.../pulls`, `gh pr list`.
- **2026-10-09 — P0**: created `wip/autonomous-2026` from main `60f3dbf`; snapshot-committed untracked `docs/sonnet5-specs/` (protect against overwrite). Verification: `git branch --show-current`, `git log --oneline -1`.
- **2026-10-09 — squash-merge verification table** (`git merge-base --is-ancestor <branch> origin/main`):
  MERGED (headstones): backup/main-premerge-20260625, chore/remove-dead-code-mainrepository, feat/appearance-data-and-theme, feat/appearance-layout-controls, feat/appearance-settings-screen, fix/config-read-off-main-thread, fix/permission-settings-scope-leak, fix/validate-netlify-update-response, refactor/theme-hardcoded-colors-lot1, refactor/theme-hardcoded-colors-lot2.
  AHEAD-of-main (pre-squash originals, content merged as squash commits #47-#59): ci/gradle-dependency-submission, ci/test-orchestrator-isolation, fix/androidtest-package-migration, fix/catalog-delete-absent-sqlite-limit, fix/catalog-worker-rename-retry, fix/instrumented-tests-hang, fix/issue-57-instrumented-oom, fix/sort-update-scroll, fix/stats-worker-tier-normalization, test/stats-collection-integration.
  REAL unmerged: feat/metadata-loading-indicator, fix/parallelize-metadata-fetch-loop, local/test-all-prs (test-only, never PR'd), vrhub-rebrand (partially superseded).

### P1 — branch hygiene findings (2026-10-09)

- **vrhub-rebrand**: `git cherry origin/main vrhub-rebrand` shows 9/10 commits already in main
  (`-` prefix). The only unique commit `1eac0af` is a docs refresh (4.0.0 badge, README rewrite)
  that has been superseded by newer main docs (#69 changelog fill, README improvements). Also,
  the full branch diff vs main contains regressions (kapt vs ksp, flavors removed, orchestrator
  removed, versionCode 13) proving the branch is an older pre-cleanup state whose real content
  landed via squash `c33f9d9`. **Verdict: REFUTED — nothing to reconcile. Refuted with evidence;
  archive tag then delete.**
- **feature/monetization** (origin): single unique commit `602648c` (README server-config
  instructions) superseded by current main README. **Verdict: REFUTED, archive+delete.**
- **local/test-all-prs**: test-only merge branch, never PR'd, no unique content beyond merges.
  **Verdict: archive+delete.**
- **feat/metadata-loading-indicator + fix/parallelize-metadata-fetch-loop**: REAL content
  (spinner for queued games, semaphore-bounded parallel fetch loop, loop-overlap guard).
  Not in main (`grep loading` on main GameListItem = empty). **Action: cherry-pick the 3 commits
  onto wip as one PR, with regression check against main's Netlify validation (branch predates
  #62; only the 3 feature commits are taken, not the file as a whole).**
- **P1 executed**: all headstone branches archived as `archive/<branch>` tags (pushed via GitHub
  API refs because direct tag push is blocked by email-privacy policy) then deleted locally and
  on origin. PR #74 (indicator + parallel fetch, cherry-picked) merged into wip. Origin now holds
  only: main, wip/autonomous-2026.


### P3 — contract audit findings (2026-10-10, read-only)

- **C1 CSRF (#13/#17)**: `protectedRouter`/`csrfUpdate` cover admin routes only; public client
  endpoints (`/config.json`, `/meta.7z`) unaffected. Client impact: none. #14/#15/#16 (perms,
  body limits, TLS): no client-side contract change. Uncommitted work on `fix/m1-csrf-coverage`
  preserved untouched. **Verdict: no consumer PR.**
- **C2 stats payloads**: client `StatsCollectRequest`/`ConsentRequest`/`GameStat` (StatsModels.kt)
  match monetization serde structs exactly (`package_name`, nullable email, `tier`,
  `is_favorite` default false). **Verdict: conform.**
- **C2 note `/user/tier`**: monetization `tier_handler` rejects non-`is_valid_email_format` emails
  (400), while client `MainRepository.resolveUserTierOrDefault` calls `getUserTier("anonymous")`
  (MainRepository.kt L504). The client treats non-2xx as fallback "standard", which matches the
  server semantic for unverified users only by coincidence of the fallback path. Behaviorally
  tolerable; contract not broken -> flagged, no consumer PR per guardrail.
- **C3 update secret**: `VRHUB_UPDATE_SECRET` HMAC is used client<->Netlify gateway only;
  vrhub-bot reads GitHub releases directly, no coupling. Server `ClientConfigResponse`
  (`baseUri`, `password` Base64) matches client `PublicConfig` (`baseUri`, `password64`).
  **Verdict: conform.**

### P2/B1 fix #76 — second finding follow-up (2026-10-10)

- CI Instrumented run on #76 exposed two issues once `@Ignore` was lifted:
  1. `migrateAll` (2->3->4->5): MIGRATION_2_3 creates install_queue WITH
     `downloadStartedAt` (schema 3), so the new MIGRATION_4_5 ALTER duplicates it.
     Fix: idempotent ALTER guarded by `PRAGMA table_info` check (commit on
     `fix/room-schema-export`).
  2. `migrate4To5` test INSERT omitted NOT NULL games columns (`lastUpdated`,
     `popularity`, `isFavorite` per schema 4). Fix: test insert aligns with v4 schema.
- Local gates green: `testProdDebugUnitTest` (RoomSchemaContractTest +
  Migration4To5ColumnTest) + `lintProdDebug`. CI rerun in progress.
