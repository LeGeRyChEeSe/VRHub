# Sprint Change Proposal - Startup Crash & Merge Corruption

**Date:** 2026-02-11
**Story IDs:** [STORY-1.9] Installation History Tracking & [STORY-4.3] Catalog Sync & UI Fix
**Status:** PROPOSED

## 1. Issue Summary
L'application présentait un crash critique au démarrage et un échec total de compilation suite à une fusion (merge) corrompue entre les stories 1.9 et 4.3.

**Problèmes identifiés :**
- Présence de marqueurs de conflit Git (`<<<<<<<`, `=======`, `>>>>>>>`) dans `MainActivity.kt`.
- Duplication de blocs de code entiers (Layout principal, fonctions de dépôt).
- Déséquilibre des accolades fermant prématurément la classe `MainActivity`.
- Signature de fonction obsolète pour `SyncingOverlay` (paramètre `message` manquant).
- Ambiguïté de nommage entre `ui.InstallStatus` et `data.InstallStatus`.

## 2. Impact Analysis
- **Impact Épique :** Retard sur la validation de l'Epic 1 (Persistance) et l'Epic 4 (Catalogue).
- **Impact Technique :** Instabilité de la base de code, impossibilité de build l'APK de test.
- **Conflits d'artéfacts :** Le PRD (FR9, FR10, FR60) et l'Architecture étaient respectés en théorie, mais l'implémentation physique était cassée.

## 3. Recommended Approach (Executed)
**Path:** Direct Adjustment (Correction immédiate des fichiers corrompus).
- **Restauration de la syntaxe :** Nettoyage des marqueurs de conflit et des doublons.
- **Unification de l'UI :** Fusion manuelle du `ModalNavigationDrawer` (1.9) et de la `CatalogUpdateBanner` (4.3).
- **Correction du TopBar :** Application du correctif de clipping du titre (Acceptance Criteria 3 de la story 4.3).

## 4. Detailed Change Proposals

### MainActivity.kt
**CHANGES:**
- Résolution des conflits dans `MainScreen()`.
- Ajout de `syncMessage` à l'appel de `SyncingOverlay`.
- Correction de l'agencement du `Scaffold` pour inclure la bannière de mise à jour.
- Fix du `CustomTopBar` avec `Modifier.weight(1f)` et `overflow = TextOverflow.Ellipsis`.

### MainRepository.kt
**CHANGES:**
- Suppression de la duplication accidentelle des lignes 253-256 (bloc `syncCatalog`).

## 5. Implementation Handoff
- **Scope:** Minor (Correction de bugs d'intégration).
- **Handoff:** Équipe de développement (pour tests de non-régression).
- **Success Criteria:** 
  1. Build Gradle réussi (Confirmé : ✅).
  2. Application stable au démarrage.
  3. Navigation Drawer et Historique accessibles.
  4. Bannière de mise à jour du catalogue fonctionnelle.

---
**✅ Correct Course workflow complete, Garoh!**
