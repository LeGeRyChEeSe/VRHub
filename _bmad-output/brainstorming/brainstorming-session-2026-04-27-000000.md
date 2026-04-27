# Brainstorming Session Results

**Facilitator:** Garoh
**Date:** 2026-04-27
**Status:** COMPLETED

---

## Session Overview

**Topic:** Refonte système catalogue - URL custom obligatoire
**Goals:** Supprimer URL VRP codé en dur, UI moderne URL custom, affichage bibliothèque jeux

### Context Guidance

Refonte du système de récupération de bibliothèque de jeux VR avec séparation légale du piratage.

### Session Setup

**Topic Focus:** Refonte système catalogue avec URL custom obligatoire
**Primary Goals:** Écran config moderne + parse JSON + affichage bibliothèque

---

## Technique Selection

**Approach:** AI-Recommended Techniques
**Recommended Techniques:** First Principles Thinking, SCAMPER Method, Decision Tree Mapping

---

## Phase 1: First Principles Thinking

**Contrainte fondamentale:** Frictionless pour accéder aux jeux rapidement.

**Décisions clés:**
- URL 100% manual — responsabilité utilisateur
- Blank canvas — aucun pré-remplissage, aucune suggestion de clé
- Distance légale — disclaimer clair, app = outil neutre
- Storage local — URL conservée entre sessions

---

## Phase 2: SCAMPER Method

### S — SUBSTITUTE (Serveur)
| Aspect | Décision |
|--------|----------|
| Input mode | URL JSON + Manual KV pairs |
| Clés | Aucune suggérée — vide total |
| Extensibilité | Clés unlimited, utilisateur définit tout |
| Storage | JSON config stocké localement |
| Philosophie | Blank canvas, zero assumption |

### C — COMBINE
| Combinaison | Décision |
|------------|----------|
| Config + Settings | **Unifiés** — popup unique pour setup ET modification |

### M — MODIFY
- Auto-load du catalogue si config existe
- Setup requis si pas de config → écran config + disclaimer

### E — ELIMINATE
- Ancien URL VRP_BASE_URI codé en dur → supprimé

### R — REVERSE
- Au lieu d'auto-configurer → utilisateur contrôle tout
- Au lieu d'erreur vide → on affiche la config pour corriger

### TEST Button
- Bouton pour valider les KV pairs AVANT de sauvegarder
- Valide que l'accès au catalogue fonctionne

---

## Phase 3: Decision Tree Mapping

```
[START: App Launch]
        │
        ▼
┌───────────────────────────────┐
│ Config exists in storage ?     │
└───────────────────────────────┘
        │
   ┌────┴────┐
   │YES       │NO
   ▼          ▼
[Load     [Show Config Screen]
Catalog]        │
   │            │
   ▼            ▼
[Success ?]  ┌─────────────────────┐
   │          │ Input Mode ?         │
┌──┴──┐      └─────────────────────┘
│     │           │
│YES  │NO         ├──────┬────────────┐
▼     ▼           │      │            ▼
┌─────────┐       │      │      ┌──────────────┐
│Display  │       ▼      ▼      │ JSON URL     │
│Catalog  │   ┌────────┐ ┌──────────┐└──────────────┘
└─────────┘   │URL     │ │Manual KV │
               │Field   │ │Pairs     │
               └────────┘ └──────────┘
                     │          │
                     └────┬─────┘
                          ▼
                   ┌─────────────┐
                   │ TEST Button │
                   └─────────────┘
                          │
                    ┌─────┴─────┐
                    │           │
                   SUCCESS     FAIL
                    │           │
                    ▼           ▼
              [Save Config] [Show Error]
                    │           │
                    ▼           ▼
              [Load Catalog] [Stay on Config]
```

---

## Idea Organization and Prioritization

### Thematic Organization

**Theme 1: Système de Config**
- URL JSON + Manual KV pairs
- Zero defaults (blank canvas)
- Storage local
- TEST button pour valider avant sauvegarde

**Theme 2: Flux Utilisateur (UX)**
- Écran config au premier lancement
- Auto-load catalogue si config existe
- Popup unifiée (setup + settings)
- Gestion d'erreurs (Test fail → stay, Catalog fail → error state)

**Theme 3: Branding & Légal**
- Nom: VRHub
- Studio: Solstice
- Disclaimer clair et visible
- Distance avec le piratage

---

## Branding

| Élément | Valeur |
|---------|--------|
| **App name** | VRHub |
| **Studio** | Solstice (branding only) |
| **Repo** | github.com/LeGeRyChEeSe/VRHub |
| **Philosophie** | A Solstice Project |

---

## Décisions finales

- URL custom obligatoire, aucune par défaut
- Config stockée localement (SharedPreferences ou Room)
- TEST button pour valider avant sauvegarde
- Auto-load du catalogue si config existe
- Popup config unifiée (premier lancement + settings)
- Disclaimer légal visible
- Zéro pré-remplissage, zéro suggestion de clé

---

## Session Summary

**Key Achievements:**
- Refonte architecturale du système de catalogue définie
- Système de config KV pairs générique conçu
- Flow UX complet: Config → Test → Save → Catalog
- Branding VRHub + Solstice validé
- Distance légale clairement établie

**Session Reflections:**
- Utilisateur a clairement défini ses contraintes (légales, techniques)
- Approche "blank canvas" validée pour maximiser responsabilité utilisateur
- Focus sur la simplicité et le frictionless

**Prochaines étapes recommandées:**
1. Créer les epics et stories d'implémentation
2. Refactorer le codebase pour supprimer VRP_BASE_URI
3. Implémenter le système de config KV pairs
4. Créer l'UI de configuration unifiée
5. Renommer le projet en VRHub
