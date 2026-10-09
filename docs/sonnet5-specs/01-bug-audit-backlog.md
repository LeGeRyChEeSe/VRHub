# Backlog d'audit de bugs — client VRHub

> ⚠️ **Statut** : le hunt multi-agents a été interrompu (limite de session) avant de livrer des findings vérifiés. Ce document contient (A) des **suspicions** relevées à la lecture manuelle du code et (B) un **plan d'audit** des modules non encore inspectés.
>
> **Règle** : chaque item est **NON VÉRIFIÉ**. Sonnet 5 doit d'abord **reproduire / tracer** le problème (émulateur + logcat, ou test unitaire rouge) pour confirmer qu'il est réel, PUIS corriger via une branche+PR dédiée. Si un item se révèle non pertinent, le documenter dans la PR de clôture et passer au suivant.

Légende sévérité (provisoire) : 🔴 critique · 🟠 élevée · 🟡 moyenne · ⚪ faible/nettoyage.

---

## A. Suspicions concrètes (avec fichier:ligne)

### A1 — 🟡 Lecture de config potentiellement bloquante en composition (main thread)
- **Fichier** : `app/src/main/java/com/vrhub/MainActivity.kt` (~L114) — `MainScreenWrapper`.
- **Symptôme suspecté** : `configRepository.hasValidConfig()` est appelé **synchrone dans la composition**. Si `ServerConfigRepository.hasValidConfig()` fait une lecture DataStore/SharedPreferences bloquante, c'est de l'I/O disque sur le thread principal à chaque (re)composition du wrapper → jank / risque d'ANR au démarrage.
- **Vérifier** : lire `ServerConfigRepository.hasValidConfig()`. S'il utilise `runBlocking { dataStore.data.first() }` ou lit un fichier, confirmer. Activer StrictMode (`detectDiskReads().penaltyLog()`) en debug et observer `StrictMode` dans logcat au lancement.
- **Correctif proposé** : exposer l'état via un `Flow`/`StateFlow` collecté avec `collectAsStateWithLifecycle` (ou `produceState`), initialisé à un état « chargement » ; ne décider Config vs Main qu'après émission. Éviter tout I/O synchrone en composition.
- **Branche** : `fix/config-read-off-main-thread`.

### A2 — 🟡 `CoroutineScope(Dispatchers.Main)` non lié au cycle de vie (fuite + action tardive)
- **Fichier** : `app/src/main/java/com/vrhub/MainActivity.kt` (~L299 et bloc `OpenPermissionSettings`).
- **Symptôme suspecté** : `kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch { snackbar…; delay(1500); startActivity(...) }` crée un scope **orphelin** (jamais annulé). Si l'utilisateur quitte l'écran/append pendant le `delay(1500)`, la coroutine survit et peut lancer un `startActivity` sur un Context d'activité potentiellement détruit.
- **Vérifier** : ouvrir le flux permission Android 10 (API 29) « Files and media », quitter l'app pendant le délai, revenir ; chercher fuite/`Activity has leaked` en logcat.
- **Correctif proposé** : utiliser le `rememberCoroutineScope()` déjà présent dans `MainScreen`, ou un `LaunchedEffect` déclenché par un état, plutôt qu'un `CoroutineScope` ad hoc. Passer par `applicationContext` seulement si un Context non-UI est acceptable.
- **Branche** : `fix/permission-settings-scope-leak`.

### A3 — 🟡 Impossible d'annuler un téléchargement pendant un gros segment (chemin install direct)
- **Fichier** : `app/src/main/java/com/vrhub/data/MainRepository.kt` — `installGame`, appels à `DownloadUtils.downloadWithProgress(..., isCancelled = { false }, ...)` (~L1168 et ~L1199).
- **Symptôme suspecté** : la coopération à l'annulation ne se fait qu'**entre** segments (`ensureActive()` en tête de boucle, ~L1096). Pendant le téléchargement d'**un seul** segment volumineux (APK jusqu'à ~4 Go), `isCancelled` est câblé en dur à `{ false }` → l'annulation utilisateur n'est pas honorée avant la fin du segment.
- **Vérifier** : lire `DownloadUtils.downloadWithProgress` (est-ce que `isCancelled` est la seule voie d'annulation, ou vérifie-t-il aussi le contexte coroutine ?). Comparer avec `DownloadWorker` (chemin primaire) pour voir s'il a le même défaut ou s'il passe un vrai prédicat.
- **Correctif proposé** : passer un prédicat réel (`{ !currentCoroutineContext().isActive }`) ou faire vérifier l'annulation coopérative dans `downloadWithProgress`. Aligner les deux chemins (repo + worker).
- **Note** : le commentaire du code dit « Cancellation handled by ensureActive() » — ce qui est **faux à l'intérieur** d'un segment. À corriger aussi dans le commentaire.
- **Branche** : `fix/cancel-during-large-segment`.

### A4 — ⚪ Code mort / propriété trompeuse
- **Fichiers/lignes** : `MainRepository.kt` L84 `val config: ServerConfig? = null` (toujours null, jamais réassigné) ; `MainRepository.kt` L518 `resolveUserTier()` (semble non appelé — seul `resolveUserTierOrDefault()` est utilisé) ; `fetchConfig()` L127 `@Deprecated` retournant toujours `null`.
- **Vérifier** : `grep -rn "\.config\b\|resolveUserTier(" app/src` pour confirmer l'absence d'usage.
- **Correctif proposé** : supprimer le code mort (petite PR de nettoyage groupée, acceptable ici car purement suppression sans risque). 
- **Branche** : `chore/remove-dead-code-mainrepository`.

### A5 — ⚪ Extraction meta : garde de nom de fichier icône incomplète
- **Fichier** : `MainRepository.kt` `extractMetaToCache` (~L396) — branche icônes `.png/.jpg`.
- **Symptôme suspecté** : contrairement aux branches `thumbnails`/`notes`/`trailers`, la branche icônes ne teste pas `fileName.isNotEmpty()` avant `File(iconsDir, fileName)`. Risque théorique faible (entrée 7z dont le nom se termine par `/`). Le path traversal est déjà neutralisé par `substringAfterLast("/")` (le nom de dossier est retiré).
- **Vérifier** : bas risque ; confirmer par lecture. Pas de repro évidente.
- **Correctif proposé** : ajouter le même garde `if (fileName.isNotEmpty())`. À traiter seulement en même temps qu'un autre changement du même fichier.
- **Branche** : (à regrouper, non prioritaire).

---

## B. Plan d'audit — modules non encore inspectés en profondeur

Pour chacun, la démarche : lire le fichier → identifier les chemins d'erreur/limites → écrire un test JVM rouge si possible → confirmer → corriger.

### B1 — 🟠 Migrations Room / montée de version DB
- **Fichiers** : `data/AppDatabase.kt`, `data/MigrationManager.kt`, `data/*Dao.kt`, `data/*Entity.kt`, `data/Converters.kt`.
- **À chercher** : version DB vs migrations fournies (une montée sans migration → `IllegalStateException` au démarrage après update), `@Query` incorrects, round-trip des `TypeConverter`, requêtes `IN(...)` dépassant `SQLITE_MAX_VARIABLE_NUMBER` (le code de sync chunk déjà à 500 — vérifier les autres DAO), accès DB hors IO.
- **Test** : `MigrationTestHelper` en Robolectric (JVM) sur les schémas exportés (`room.schemaLocation`). Vérifier que `room.schemaLocation` est configuré ; sinon, tester la création + insert/query.

### B2 — 🟠 DownloadWorker & pipeline de fond (chemin primaire)
- **Fichier** : `worker/DownloadWorker.kt` (+ `worker/*Worker.kt`).
- **À chercher** : foreground service — sur `targetSdk 34`, un `setForeground`/`ForegroundInfo` doit déclarer un `foregroundServiceType` cohérent avec le manifeste (`dataSync`), sinon crash `MissingForegroundServiceTypeException` ; canal de notification créé avant `notify` (API 26+) ; `POST_NOTIFICATIONS` runtime (API 33+) ; flux/`InputStream`/`OutputStream` fermés sur tous les chemins d'erreur ; reprise/retry, corruption sur download partiel ; unicité WorkManager (`ExistingWorkPolicy`) et duplication d'enqueue ; annulation coopérative (cf. A3).

### B3 — 🟠 Manifeste & plateforme
- **Fichier** : `app/src/main/AndroidManifest.xml` (+ `res/xml/file_paths.xml`).
- **À chercher** : `android:exported` explicite sur tous les composants (obligatoire API 31+, sinon build/instant crash) ; permissions déclarées et cohérentes (`MANAGE_EXTERNAL_STORAGE`, `REQUEST_INSTALL_PACKAGES`, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `WAKE_LOCK`) ; `FileProvider` (`${packageName}.fileprovider`) et ses `file_paths` couvrant `externalFilesDir` où l'APK est stagé ; `PendingIntent` avec flag de mutabilité (API 31+).
- **Note** : le flavor `dev` a `applicationIdSuffix = .debug` → l'autorité FileProvider devient `com.vrhub.debug.fileprovider`. Vérifier que `MainActivity` construit bien `"${context.packageName}.fileprovider"` (c'est le cas L248) et que l'autorité manifeste utilise `${applicationId}`.

### B4 — 🟡 Désérialisation réseau (Gson/Retrofit)
- **Fichiers** : `network/*.kt` (`MonetizationModels`, `StatsModels`, `GitHubReleaseService`, `UpdateService`, `PublicConfig`, `MonetizationApi`, `StatsApiService`).
- **À chercher** : `response.isSuccessful` non testé avant `body()` ; `body()` nullable → NPE ; champs sans valeur par défaut désérialisés à `null` alors que déclarés non-nullables (Gson contourne les constructeurs Kotlin → NPE différée) ; timeouts OkHttp configurés ; pas de secret loggué.

### B5 — 🟡 Parsing catalogue & logique
- **Fichiers** : `logic/CatalogParser.kt`, `logic/CatalogUtils.kt`, `logic/TrailerUtils.kt`.
- **À chercher** : `String.lowercase()/format()` sans `Locale` (bug en locale turque, etc.) ; parse de nombres/dates sans `try` ; comparateur de tri violant le contrat (TimSort → `IllegalArgumentException: Comparison method violates its general contract`) ; comparaison de versions type semver mal ordonnée (`4.10.0` vs `4.9.0`) ; extraction d'ID YouTube produisant une URL/intent invalide ; robustesse sur ligne malformée du `VRP-GameList.txt`.

### B6 — 🟡 UI catalogue & MainViewModel (état Compose)
- **Fichiers** : `ui/MainViewModel.kt`, `ui/GameListItem.kt`, `ui/InstallHistoryScreen.kt`, reste de `MainActivity.kt` (>L1349 non lu).
- **À chercher** : rétention de `Context`/`Activity` dans le ViewModel (fuite) ; clés de liste (`key = { it.releaseName }`) → collision si releaseName non unique (recomposition/scroll cassés) ; index hors bornes sur catalogue vide (`AlphabetIndexer`, `scrollToItem`) ; `LaunchedEffect`/`snapshotFlow` avec mauvaises clés.

### B7 — 🟡 Offline & gestion d'erreurs
- **Transverse** : repository + workers + view models.
- **À chercher** : crash sans réseau (`UnknownHostException`/`IOException` non capturées) ; UI catalogue vide qui plante ; installs en file perdues ; exceptions avalées masquant l'échec à l'utilisateur ; état premier-lancement/sans-config qui plante ; division par zéro sur la progression (`totalBytes == 0`).

### B8 — 🟡 Écrans config / monétisation / consentement
- **Fichiers** : `ui/ConfigurationScreen.kt` + `ConfigurationViewModel`, `ui/Monetization*`, `ui/RestorePurchase*`, `ui/components/ConsentDialog.kt`, `ui/components/DebugMonetizationPanel.kt`.
- **À chercher** : validation d'URL/JSON à l'import (schéma manquant, double slash) ; validation d'email ; état de saisie perdu à la rotation ; états d'erreur non affichés ; `DebugMonetizationPanel` visible en release (il est monté dans `MainScreenWrapper` — vérifier qu'il est bien no-op/absent hors flavor dev / hors debug).

---

## C. Ordre de traitement suggéré

1. Compléter l'audit **B3 (manifeste)** et **B2 (DownloadWorker)** en premier : ce sont les zones à plus fort risque de crash réel sur `targetSdk 34`.
2. Puis **B1 (migrations Room)** — crash potentiel à la mise à jour, impact utilisateur direct.
3. Puis confirmer/traiter **A1, A2, A3**.
4. **B4–B8** en balayage.
5. Nettoyages **A4/A5** en fin, éventuellement regroupés.

> Rappel process : **une branche + une PR par bug confirmé** (cf. `00-environment-and-workflow.md`).
