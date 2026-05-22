# STORY-2.1: Création du composant UI ConsentDialog

## Story Metadata

| Field | Value |
|-------|-------|
| Story ID | STORY-2.1 |
| Story key | 2-1-consent-dialog |
| Epic | Epic-2 — Dialogue de consentement et intégration de la confidentialité |
| Title | Création du composant UI ConsentDialog |

## Context

**What:** Créer un composant Jetpack Compose `ConsentDialog` qui présente une boîte de dialogue obligatoire lors du premier lancement de l'application.

**Why:** Le PRD (Journey 1: First Launch - Consent Decision) définit que l'utilisateur doit être clairement informé de la collecte de données avant toute opération. FR1-FR6 требуют un dialog non-navigable tant que l'utilisateur n'a pas fait son choix.

**Business Driver:** Transformer l'utilisateur en participant actif de la découverte communautaire en lui demandant son consentement éclairé, avec une transparente totale sur les données collectées (package names, favoris, tier) vs. non collectées (aucun PII).

## Acceptance Criteria

### AC #1: Composant Compose unique
- `ConsentDialog` est un `@Composable` function
- Affiche un titre, deux listes (ce qui est collecté / ce qui ne l'est pas), et deux boutons
- Blocking: interfère avec la navigation jusqu'à choix fait
- Positionné au centre de l'écran (Modal dialog)
- Utilise Material Design 3 (Dialog, TextButton, Button)

### AC #2: Contenu du dialog
- Titre: "Aidez à améliorer Rookie On Quest"
- Sous-titre: "Partagez des statistiques anonymes pour aider la communauté VR"
- Liste "Est collecté": package names des jeux installés, favoris, tier utilisateur
- Liste "N'est PAS collecté": informations personnelles, ID appareil, localisation, email
- Références: PRD Journey 1, FR2

### AC #3: Actions Accept/Decline
- Bouton "Accepter" → appelle `consentPreferences.setConsentEnabled(true)` + `consentPreferences.setHasSeenConsentDialog(true)`, puis dismiss
- Bouton "Refuser" → appelle `consentPreferences.setConsentEnabled(false)` + `consentPreferences.setHasSeenConsentDialog(true)`, puis dismiss
- Les deux callbacks propagent le choix vers l'appelant via `onDismiss`

### AC #4: Affichage conditionnel au premier lancement
- `ConsentDialog` affiché SEULEMENT si `consentPreferences.hasSeenConsentDialog == false`
- Logique dans le ViewModel/App层级 (pas dans le dialog lui-même)
- Une fois vu (Accept ou Decline), ne réapparaît plus (FR4)

### AC #5: UI/UX requirements
- Thème sombre VR compatible (utilise les couleurs du Material theme existant)
- Texte lisible sur fond sombre
- Bouton Accept en couleur primaire, bouton Refuser en texte secondaire
- Animations standard Material (fade in)

## Files à créer

| Fichier | Action |
|---------|--------|
| `app/src/main/java/com/vrhub/ui/dialog/ConsentDialog.kt` | Nouveau — Composant Compose |
| `app/src/main/java/com/vrhub/ui/dialog/ConsentDialogViewModel.kt` | Nouveau — ViewModel pour la logique de consentement |

## Files à modifier

| Fichier | Modification |
|---------|--------------|
| `app/src/main/java/com/vrhub/MainActivity.kt` ou ViewModel principal | Appeler ConsentDialog si `hasSeenConsentDialog == false` |

## Dependencies

- `ConsentPreferences.kt` (STORY-1.1) — `hasSeenConsentDialog`, `consentEnabled`
- `ConsentPreferencesInterface.kt` — interface DI
- Material 3 Compose (`androidx.compose.material3`)

## Definition of Done

- [ ] Dialog s'affiche au premier lancement (hasSeenConsentDialog = false)
- [ ] Les deux actions (Accept/Decline) fonctionnent et mettent à jour DataStore
- [ ] Dialog ne réapparaît pas après le premier choix
- [ ] Build successful
- [ ] Tests unitaires sur ConsentDialogViewModel
