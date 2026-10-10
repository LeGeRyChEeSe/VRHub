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

### P2 — client security audit findings (2026-10-09 / 2026-10-10)

Order followed: B3 -> B2 -> B1 -> A3 -> B4 -> B5 -> B6 -> B7 -> B8 -> A5.

- **B3 manifest — REFUTED (clean)**. `app/src/main/AndroidManifest.xml`: `android:exported`
  explicit on every component (L36 false, L45 true); permissions declared (MANAGE_EXTERNAL_STORAGE,
  REQUEST_INSTALL_PACKAGES, FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, POST_NOTIFICATIONS,
  WAKE_LOCK); FileProvider authority built from `${applicationId}` and `file_paths.xml` covers
  cache-path, files-path, external-files-path, external-path (the staged APK lives in
  `externalFilesDir`). No `PendingIntent` anywhere in the app (`grep PendingIntent app/src/main`
  = empty), so no mutability-flag risk.
- **B2 DownloadWorker — REFUTED (clean)**. `setForeground(getForegroundInfo())` called from
  `doWork()` with the manifest `foregroundServiceType="dataSync"`; `createNotificationChannel()`
  always invoked before `notify()`; `POST_NOTIFICATIONS` runtime check for API 33+ (L718-722);
  every request/response/stream via `.use` (L352, 391, 393, 447, 489, 515); cooperative
  cancellation uses the real predicate `isCancelled = { isStopped }` (L401); enqueue is unique
  (`enqueueUniqueWork("download_$releaseName", KEEP)`, L2856). No finding.
- **B1 Room — TWO CONFIRMED FINDINGS, both fixed (red->green)**:
  - **F1** `MainRepository.getGamesByReleaseNames` issued a single unbounded
    `IN (:releaseNames)` call, violating the chunking contract documented on
    `GameDao.deleteByReleaseNames` (SQLITE_MAX_VARIABLE_NUMBER = 999 on Android < 12). The
    sync path chunks at 500 (L293) but the queue cache path (MainViewModel L516) did not.
    Red: `GameDaoChunkingTest` with 600 names -> `AssertionError ... got calls: [600]`.
    Fix: `chunked(500).flatMap { gameDao.getByReleaseNames(it) }`. **PR #75 merged into wip**
    (CI: Build/Lint/Unit/Instrumented all pass).
  - **F2** `exportSchema = false` + no `room.schemaLocation` meant `MigrationTestHelper` could
    never load historical schemas — the real root cause of the `@Ignore("TODO(test-rot)")` on
    `RoomMigrationTest`. Related: `QueuedInstallEntity` gained `downloadStartedAt` at schema v5
    (commit `a645e0b`, story 1.9) but `MIGRATION_4_5` never ALTERed `install_queue`, so a device
    at Room v4 produced a schema mismatch and was silently wiped by
    `fallbackToDestructiveMigration` instead of migrating. Red: `Migration4To5ColumnTest` ->
    `missing: [downloadStartedAt]`. Fix: one-line `ALTER TABLE` in `MIGRATION_4_5` + schema
    export enabled + schemas 2-7 reconstructed from git history (`git show` of entity sources at
    `c8cdfd7`, `e311967`, `a645e0b`, `a646488`, KSP-generated 7.json) + androidTest assets wiring;
    the two `@Ignore` removed. **PR #76 open** (instrumented job validates the re-enabled tests).
- **A3 — REFUTED (behaviourally)**. `DownloadUtils.downloadWithProgress` checks
  `currentCoroutineContext().ensureActive()` **inside** the read loop (Constants.kt L652), so a
  4 GB segment is cancellable mid-flight as soon as the caller's Job is cancelled
  (`MainViewModel.cancelInstall()` cancels `currentTaskJob`). The backlog premise ("cancellation
  only between segments") is wrong; `isCancelled = { false }` is a no-op predicate, not a hang.
  No fix required.
- **B4 network — REFUTED (clean)**. `isSuccessful` checked before `body()` at every network site
  (MainRepository 505/640/671/687/777/1131, ServerConfigRepository 117/321/354, StatsCollector
  74/96, CatalogUtils 92/143, DebugMonetizationViewModel 62); OkHttp connect/read timeouts set in
  `NetworkModule`; Gson models use nullable fields consistently (`UserTierResponse.tier` nullable,
  resolved via `resolveTier` with `standard` fallback); passwords masked in logs
  (`"****"` at MainRepository L221/1308); no secret logged.
- **B5 parsing — REFUTED (clean)**. Comparator uses `compareBy` + `thenBy(String.CASE_INSENSITIVE_ORDER)`
  — total and consistent, no TimSort contract violation. `isVersionNewer` compares numerically per
  segment (so `4.10.0 > 4.9.0`) with digit filtering and pre-release tie-breaking. `lowercase()`
  used only on ASCII version strings; no `String.format` with locale-sensitive case. YouTube ID
  extraction guarded by `VIDEO_ID_REGEX` (11-char base64-url alphabet) returning null on malformed
  input. `CatalogParser.parse` uses `getOrNull(...) ?.toLongOrNull()/toIntOrNull()` — no unguarded
  parse, malformed lines skipped (`parts.size >= 4`).
- **B6 UI state — REFUTED (clean)**. `key = { it.releaseName }` cannot collide: `releaseName` is
  the Room `@PrimaryKey` on both `games` and `install_queue`, so list keys are unique by
  construction. `MainViewModel` holds `Application` (never an Activity) — no Activity leak.
  `AlphabetIndexer` click index comes from a map built on the same sorted list and is only rendered
  when `games.isNotEmpty()`. `LaunchedEffect`/`snapshotFlow` keys are the list states and layout
  info (correct).
- **B7 offline/errors — REFUTED (clean)**. Network calls wrapped in try/catch with user-facing
  error messages and retry-with-backoff (403/429/5xx handled distinctly, 404 not retried);
  history stats emits `null` on error instead of crashing the flow; progress division guarded
  (`if (totalBytes > 0) ... else 0f`, Constants L663); empty-catalog UI handled by
  LoadingScreen/error branch.
- **B8 config/monetization screens — REFUTED (clean)**. `DebugMonetizationPanel` is a no-op in
  release: early return unless `BuildConfig.DEBUG && APPLICATION_ID.endsWith(".debug")`.
  `ConfigurationViewModel.setJsonUrl` validates scheme, non-blank, no spaces, path present.
  `MonetizationEmailViewModel` validates with `android.util.Patterns.EMAIL_ADDRESS` before
  `/init`. Email/query state restored from repository (`getSavedEmail`).
- **A5 — REFUTED**. `extractMetaToCache` icon branch already has the `fileName.isNotEmpty()`
  guard (MainRepository L378-380), same as thumbnails/notes/trailers. No change needed.
- **LIKE escaping (InstallHistoryDao)** — contract honoured: ViewModel escapes `\`, `%`, `_`
  before the `ESCAPE '\'` query and handles truncation inside an escape sequence. Clean.

**P2 result**: 2 confirmed findings (both B1), fixed with red->green tests via PRs #75 and #76;
10 items refuted with the evidence above. No item left unverified.

