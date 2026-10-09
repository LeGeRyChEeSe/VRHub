# Feature phare — Personnalisation de l'apparence

> **⚠️ EN GRANDE PARTIE DÉJÀ IMPLÉMENTÉE.** Cette spec décrivait un chantier à construire, mais
> le code contient déjà `AppearancePreferences.kt`, `AppearanceScreen.kt`,
> `AppearanceViewModel.kt`, ainsi que `ui/theme/AppearanceColorSchemes.kt`,
> `AppearanceLayout.kt`, `AppearanceModels.kt` et `AppearanceTypography.kt` (voir
> `app/src/main/java/com/vrhub/`). Les commits `feat: add Appearance settings screen with
> accent, font and color picker`, `feat: wire density, card corner and catalog layout
> controls` et `feat: add appearance data layer and theme wiring` ont déjà livré une bonne
> partie du §1 (accent, police, densité, coins de carte, agencement catalogue). **Avant de
> reprendre ce document comme base de travail, vérifie l'état réel de ces fichiers** pour ne
> pas redévelopper une fonctionnalité existante — ne considère comme restant à faire que ce
> qui, après lecture du code, n'apparaît pas déjà couvert (par exemple le mode Clair/Système,
> non confirmé implémenté au moment de la rédaction de cette note).

**Objectif produit (propriétaire)** : permettre à l'utilisateur de personnaliser l'app selon ses goûts — **couleurs, police, agencement** — pour rendre sa navigation unique.

**Branche** : `feat/appearance-customization` (peut être découpée en sous-PR par phase, cf. §8).

---

## 0. État actuel (constat de code)

- Thème unique codé en dur : `app/src/main/java/com/vrhub/ui/theme/Theme.kt`
  - `VrpColorScheme = darkColorScheme(primary=blanc, secondary=#00B2FF « Meta Quest blue », background=#0A0A0A, surface=#121212, …)`
  - `VrpTypography = Typography(...)` avec `fontFamily = FontFamily.Default` partout.
  - `VRHubTheme(content)` ne prend **que** `content` et applique `MaterialTheme(colorScheme, typography)`.
- `MainActivity.onCreate` enveloppe toute l'UI dans `VRHubTheme { Surface(...) { MainScreenWrapper() } }`.
- **Beaucoup de couleurs codées en dur** hors du colorScheme, dispersées dans `MainActivity.kt` et les composants : `Color.Black`, `Color.White`, `Color(0xFF121212)`, `Color(0xFF1E1E1E)`, `Color(0xFFFFD700)` (or), `Color(0xFFCF6679)` (rouge), `Color.Gray`, etc. → une vraie personnalisation impose de **router ces couleurs vers le thème** (voir §6, c'est le gros du travail).
- DataStore Preferences est **déjà en dépendances** (`androidx.datastore:datastore-preferences:1.0.0`) et le pattern repository existe déjà (`ConsentPreferences`, `ServerConfigRepository`) — à réutiliser.
- L'app est **dark-only** aujourd'hui. Le mode clair est **optionnel (phase 2)** : le prioriser bas car il exige de fiabiliser toutes les couleurs claires.

---

## 1. Portée (ce que l'utilisateur peut régler)

| Réglage | Options | Défaut (= look actuel) |
|--------|---------|------------------------|
| **Couleur d'accent** | 6–8 presets (Meta Blue, Violet, Vert, Ambre, Rose, Cyan, Rouge) + **sélecteur personnalisé** (roue/HSV ou hex) | Meta Blue `#00B2FF` |
| **Mode thème** | Sombre / Clair / Système | Sombre |
| **Fond** | Noir pur (`#0A0A0A`) / Gris ardoise / Teinté par l'accent | Noir pur |
| **Police** | 4–5 familles bundle (Default/System, Rounded, Serif, Mono, Condensed) | System (Default) |
| **Taille du texte** | Échelle 0,85× → 1,30× (slider) | 1,0× |
| **Densité / agencement** | Confortable / Compact ; **rayon des cartes** (Net / Arrondi / Très arrondi) | Confortable, Arrondi |
| **Vue catalogue** | Auto (responsive) / Forcer Liste / Forcer Grille | Auto |

> Garder l'ensemble **simple et réversible** : un bouton « Réinitialiser l'apparence » restaure tous les défauts.

---

## 2. Architecture cible

```
data/AppearancePreferences.kt        (nouveau)  → DataStore, lecture/écriture, Flow<AppearanceSettings>
ui/theme/AppearanceModels.kt         (nouveau)  → data class AppearanceSettings, enums, presets, defaults
ui/theme/AppearanceColorSchemes.kt   (nouveau)  → build d'un ColorScheme à partir de AppearanceSettings
ui/theme/AppearanceTypography.kt     (nouveau)  → build d'une Typography (famille + échelle)
ui/theme/Theme.kt                    (modifié)  → VRHubTheme(settings, content) + CompositionLocal LocalAppearance
ui/AppearanceViewModel.kt            (nouveau)  → expose settings + setters (persistance)
ui/AppearanceScreen.kt               (nouveau)  → écran de réglages + preview live
res/font/*.ttf + res/font/*.xml      (nouveau)  → polices bundle (offline)
MainActivity.kt                      (modifié)  → collecte les settings et les passe à VRHubTheme ; entrée « Apparence » dans SettingsDialog
```

### 2.1 Modèle (`AppearanceModels.kt`)

```kotlin
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AppFont { SYSTEM, ROUNDED, SERIF, MONO, CONDENSED }
enum class BackgroundStyle { TRUE_BLACK, SLATE, ACCENT_TINTED }
enum class Density { COMFORTABLE, COMPACT }
enum class CardCorner { SHARP, ROUNDED, EXTRA_ROUNDED }   // 6.dp / 16.dp / 28.dp
enum class CatalogLayout { AUTO, LIST, GRID }

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentArgb: Int = 0xFF00B2FF.toInt(),   // Meta Blue par défaut (look actuel)
    val background: BackgroundStyle = BackgroundStyle.TRUE_BLACK,
    val font: AppFont = AppFont.SYSTEM,
    val fontScale: Float = 1.0f,                 // borné [0.85, 1.30]
    val density: Density = Density.COMFORTABLE,
    val cardCorner: CardCorner = CardCorner.ROUNDED,
    val catalogLayout: CatalogLayout = CatalogLayout.AUTO,
)

// Presets d'accent proposés à l'UI
val ACCENT_PRESETS = listOf(
    "Meta Blue" to 0xFF00B2FF, "Violet" to 0xFF8B5CF6, "Émeraude" to 0xFF10B981,
    "Ambre" to 0xFFF59E0B, "Rose" to 0xFFEC4899, "Cyan" to 0xFF06B6D4, "Rouge" to 0xFFEF4444,
).map { it.first to it.second.toInt() }
```

### 2.2 Persistance (`AppearancePreferences.kt`)

- Suivre **exactement** le pattern de `ConsentPreferences` (même style de `DataStore<Preferences>`, `Context.dataStore` par delegate, clés typées).
- Nom du store : `"appearance_prefs"`.
- Clés : `theme_mode` (String enum name), `accent_argb` (Int), `background_style` (String), `app_font` (String), `font_scale` (Float), `density` (String), `card_corner` (String), `catalog_layout` (String).
- API :
  ```kotlin
  val settings: Flow<AppearanceSettings>            // map des Preferences → data class, avec fallback défauts si valeur absente/illisible
  suspend fun update(transform: (AppearanceSettings) -> AppearanceSettings)  // ou un setter par champ
  suspend fun reset()                               // efface toutes les clés → défauts
  ```
- **Robustesse** : tout `enumValueOf` doit être encapsulé (`runCatching { … } ?: défaut`) pour ne jamais crasher sur une valeur corrompue ; `fontScale` borné via `coerceIn(0.85f, 1.30f)`.

### 2.3 Construction du ColorScheme (`AppearanceColorSchemes.kt`)

```kotlin
fun buildColorScheme(s: AppearanceSettings, systemDark: Boolean): ColorScheme {
    val dark = when (s.themeMode) {
        ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> systemDark
    }
    val accent = Color(s.accentArgb)
    val background = when (s.background) {
        BackgroundStyle.TRUE_BLACK -> if (dark) Color(0xFF0A0A0A) else Color(0xFFF7F7F7)
        BackgroundStyle.SLATE      -> if (dark) Color(0xFF12151A) else Color(0xFFECEFF3)
        BackgroundStyle.ACCENT_TINTED -> if (dark) accent.copy(alpha=0.08f).compositeOver(Color(0xFF0A0A0A)) else accent.copy(alpha=0.06f).compositeOver(Color.White)
    }
    return if (dark) darkColorScheme(
        primary = Color.White, onPrimary = Color.Black,
        secondary = accent, onSecondary = Color.White,
        background = background, surface = /* dérivé du background, +luminance */ …,
        onBackground = Color(0xFFE1E1E1), onSurface = Color(0xFFE1E1E1),
        error = Color(0xFFFF5252), outline = Color(0xFF333333),
    ) else lightColorScheme(
        primary = Color.Black, onPrimary = Color.White,
        secondary = accent, onSecondary = Color.White,
        background = background, surface = …, onBackground = Color(0xFF1A1A1A), onSurface = Color(0xFF1A1A1A),
        error = Color(0xFFB00020), outline = Color(0xFFCCCCCC),
    )
}
```
> Détail à soigner : `surface`, `surfaceVariant`, `primaryContainer` doivent être dérivés du background pour garder la hiérarchie visuelle. Assurer un **contraste AA** onX/X (voir §7 tests).

### 2.4 Typographie (`AppearanceTypography.kt`)

```kotlin
@Composable fun appFontFamily(font: AppFont): FontFamily = when (font) {
    AppFont.SYSTEM   -> FontFamily.Default
    AppFont.SERIF    -> FontFamily.Serif
    AppFont.MONO     -> FontFamily.Monospace
    AppFont.ROUNDED  -> FontFamily(Font(R.font.rounded))     // bundle .ttf
    AppFont.CONDENSED-> FontFamily(Font(R.font.condensed))   // bundle .ttf
}

fun buildTypography(base: Typography, family: FontFamily, scale: Float): Typography =
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontFamily = family, fontSize = base.headlineLarge.fontSize * scale),
        /* … idem pour chaque style utilisé … */
    )
```
- Réutiliser `VrpTypography` actuel comme `base` (préserve les poids/letterSpacing).
- **Polices bundle** (offline, fiable sur Quest — préférable aux Downloadable Fonts qui exigent Play Services + réseau) : déposer 1 `.ttf` par famille custom dans `app/src/main/res/font/` (ex. `rounded.ttf`, `condensed.ttf`) avec licence OFL. Documenter la source/licence dans `docs/`. `SYSTEM/SERIF/MONO` n'ont besoin d'aucun asset.

### 2.5 Theme + CompositionLocal (`Theme.kt` modifié)

```kotlin
val LocalAppearance = staticCompositionLocalOf { AppearanceSettings() }

@Composable
fun VRHubTheme(settings: AppearanceSettings = AppearanceSettings(), content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val colorScheme = remember(settings, systemDark) { buildColorScheme(settings, systemDark) }
    val typography = buildTypography(VrpTypography, appFontFamily(settings.font), settings.fontScale)
    CompositionLocalProvider(LocalAppearance provides settings) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
    }
}
```
- **Compat rétro** : garder le défaut `settings = AppearanceSettings()` pour ne pas casser les `@Preview` existants.

### 2.6 Câblage dans `MainActivity`

```kotlin
override fun onCreate(...) {
    setContent {
        val appearanceVm: AppearanceViewModel = viewModel()
        val settings by appearanceVm.settings.collectAsStateWithLifecycle(initialValue = AppearanceSettings())
        VRHubTheme(settings) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                MainScreenWrapper()
            }
        }
    }
}
```
- Ajouter la dépendance `androidx.lifecycle:lifecycle-runtime-compose` pour `collectAsStateWithLifecycle` (ou utiliser `collectAsState`).

---

## 3. Écran « Apparence » (`AppearanceScreen.kt`)

- **Accès** : dans `SettingsDialog` (drawer → *Settings*), ajouter une entrée « **Apparence** » qui ouvre `AppearanceScreen` (nouveau `showAppearanceDialog` state dans `MainScreen`, même mécanique que `showConfigDialog`). Optionnel : aussi une entrée directe dans le drawer.
- **Contenu** (sections, chacune met à jour le DataStore immédiatement via le VM) :
  1. **Aperçu live** en haut : une fausse `GameListItem` + une barre supérieure miniature reflétant accent/police/rayon/fond en temps réel.
  2. **Mode** : SegmentedButton Sombre/Clair/Système.
  3. **Accent** : rangée de pastilles presets (sélection = anneau) + bouton « Personnalisé… » ouvrant un color picker (HSV) ; afficher la valeur hex éditable.
  4. **Fond** : 3 choix visuels.
  5. **Police** : liste de familles (rendu du nom dans sa propre police) + slider « Taille du texte » (aperçu « Aa »).
  6. **Agencement** : Densité (Confortable/Compact), Rayon des cartes (3 choix), Vue catalogue (Auto/Liste/Grille).
  7. **Réinitialiser l'apparence** (bouton bas, confirmation).
- **Color picker** : implémenter un picker HSV simple en Compose (Canvas + sliders H/S/V + champ hex). Pas de dépendance externe requise ; si une lib est ajoutée, la justifier.

---

## 4. Application des réglages « agencement »

- **Rayon des cartes** : exposer `LocalAppearance.current.cardCorner` → un `RoundedCornerShape` central utilisé par `GameListItem` et les surfaces principales, en remplacement des `RoundedCornerShape(24.dp)` codés en dur.
- **Densité** : facteur d'espacement (`Comfortable`=1.0, `Compact`=0.75) appliqué aux paddings verticaux des items et `contentPadding` des listes.
- **Vue catalogue** : dans `MainScreen`, remplacer la décision `isWide` seule par : `when (catalogLayout) { LIST -> liste; GRID -> grille; AUTO -> if (isWide) grille else liste }`.
- **Échelle de texte** : déjà portée par la Typography ; ne PAS aussi multiplier via `fontScale` du `Configuration` pour éviter double application.

---

## 5. Réglages qui touchent des couleurs codées en dur

C'est la partie la plus étendue. Voir §6 pour le plan de migration détaillé. Principe : **tout ce qui doit réagir à l'accent/au fond doit lire `MaterialTheme.colorScheme` ou `LocalAppearance`**, plus des couleurs codées en dur.

---

## 6. Plan de migration des couleurs codées en dur (phasé)

Table de correspondance recommandée (appliquer progressivement, tester visuellement à chaque lot) :

| Codé en dur (exemples repérés) | Remplacer par |
|--------------------------------|----------------|
| `Color(0xFF00B2FF)` (accent) littéral | `MaterialTheme.colorScheme.secondary` |
| `Color(0xFF0A0A0A)` (fond) | `MaterialTheme.colorScheme.background` |
| `Color(0xFF121212)` (barres/drawer/surfaces) | `MaterialTheme.colorScheme.surface` (ou un `surfaceContainer` dérivé) |
| `Color(0xFF1E1E1E)` (dialogs/containers) | `MaterialTheme.colorScheme.surfaceVariant` / `primaryContainer` |
| `Color.Black` (fonds `Scaffold containerColor = Color.Black`, `SetupLayout`) | `MaterialTheme.colorScheme.background` |
| `Color.White` (textes/icônes principaux) | `MaterialTheme.colorScheme.onSurface` / `onBackground` |
| `Color.Gray` (texte secondaire/inactif) | `onSurface.copy(alpha=0.6f)` ou `colorScheme.outline` |
| `Color(0xFFCF6679)` (rouge destructif) | `MaterialTheme.colorScheme.error` |
| `Color(0xFFFFD700)` (or supporter) / `Color(0xFF9C27B0)` (lucky) | **garder codé en dur** : ce sont des couleurs sémantiques de *tier* monétisation, indépendantes du thème (documenter le choix) |
| `RoundedCornerShape(24.dp)` (cartes/dialogs) | forme dérivée de `LocalAppearance.current.cardCorner` (ou `MaterialTheme.shapes.large`) |

**Fichiers principaux à balayer** : `MainActivity.kt` (barre, drawer `ModalDrawerSheet(drawerContainerColor = Color(0xFF121212))`, `Scaffold(containerColor = Color.Black)`, dialogs `containerColor = Color(0xFF1E1E1E)`, `SetupLayout` fond `Color.Black`), `ui/GameListItem.kt`, `ui/components/*.kt`, `ui/InstallHistoryScreen.kt`, écrans `Configuration/Monetization/RestorePurchase`.

**Découpage recommandé** (une PR par lot pour rester rev-friendly) :
- Lot 1 : barre supérieure + drawer + `Scaffold` (surfaces structurelles).
- Lot 2 : `GameListItem` + overlays d'installation.
- Lot 3 : dialogs (delete/cancel/permission) + `SetupLayout` + écrans setup.
- Lot 4 : composants restants.

> Ne PAS tout migrer d'un coup : risque de régressions visuelles difficiles à relire. Chaque lot = capture avant/après sur l'émulateur.

---

## 7. Critères d'acceptation

1. Changer l'accent met à jour **immédiatement** (sans redémarrage) la couleur secondaire dans toute l'app (barre, sélection drawer, boutons, indicateurs).
2. Changer la police et la taille se répercute sur tous les textes ; l'aperçu live reflète le choix avant de fermer l'écran.
3. Les réglages **persistent** après fermeture/redémarrage de l'app (DataStore).
4. « Réinitialiser l'apparence » restaure exactement le look d'origine (dark, Meta Blue, System font, 1,0×, Confortable, Arrondi, Auto).
5. Mode Clair (si phase 2 livrée) : **aucun texte illisible** — contraste onSurface/surface et onBackground/background ≥ 4.5:1 (AA).
6. Aucun I/O bloquant sur le thread principal pour lire/écrire les préférences.
7. `Density`/`CardCorner`/`CatalogLayout` produisent un effet visible et non cassant sur catalogue vide comme rempli.
8. Aucune régression de démarrage : app lance toujours en < 3 s, pas de crash (logcat propre).

## 8. Découpage en PR

| PR | Contenu | Dépend de |
|----|---------|-----------|
| `feat/appearance-data-and-theme` | DataStore + modèle + refonte `VRHubTheme` + câblage `MainActivity` (accent + police + taille, dark uniquement) | — |
| `feat/appearance-settings-screen` | `AppearanceScreen` + entrée dans `SettingsDialog` + color picker + preview | PR précédente |
| `feat/appearance-layout-controls` | Densité + rayon cartes + vue catalogue | data-and-theme |
| `refactor/theme-hardcoded-colors-lot1..4` | Migration couleurs (4 lots, §6) | data-and-theme |
| `feat/appearance-light-mode` (optionnel, phase 2) | Mode clair + fiabilisation contrastes | tous les lots couleur |

## 9. Tests

- **JVM/Robolectric** (androidTest est cassé — cf. README) :
  - `AppearancePreferences` : round-trip write→read de chaque champ ; valeur corrompue → défaut ; `fontScale` borné ; `reset()` efface tout.
  - `buildColorScheme` : accent → `secondary` correct ; `themeMode`/`systemDark` → dark vs light ; `ACCENT_TINTED` produit un background distinct.
  - `buildTypography` : famille appliquée, tailles = base × scale.
- **Manuel émulateur** (`Pixel_7`) : parcourir chaque réglage, vérifier live update + persistance après `am force-stop` puis relance ; capture avant/après par lot de migration couleur.

## 10. Notes / pièges

- `staticCompositionLocalOf` pour `LocalAppearance` (les settings changent rarement ; si on veut recomposer finement, `compositionLocalOf`). Comme `VRHubTheme` reçoit déjà `settings` et reconstruit colorScheme/typography, le `MaterialTheme` propage la plupart des changements ; `LocalAppearance` ne sert qu'aux réglages hors colorScheme (rayon, densité, layout).
- Bundler des polices augmente la taille de l'APK (~100–400 Ko/famille) — acceptable ; garder 2–3 familles custom max.
- Le mode Clair est **coûteux** (toutes les couleurs codées en dur supposent un fond sombre). Le livrer seulement après les lots de migration §6, sinon l'app clair sera illisible.
