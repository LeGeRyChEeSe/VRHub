---
name: monetization_plan
description: VRHub monetization strategy and feature roadmap
type: project
originSessionId: ca66ee28-7289-475c-b45b-978146c13fe3
---

# VRHub Monetization Plan

## Résumé de la stratégie

**Two-tier approach:**
1. **"Supporter" purchase (5€ lifetime)** — Badge + accès aux futures Lucky features
2. **Lucky tier (10€ lifetime, plus tard)** — Multi-serveurs + Sync cloud

**Why:** Audience tech/niche (Quest owners with self-hosted servers) = willing to pay for convenience. Early adopter pricing validates willingness-to-pay before committing to full Lucky development.

---

## Phase 1 — Bouton Supporter

### What
Bouton dans l'app (Settings) qui ouvre Ko-fi. Prix : **5€ one-time**.

### Plateforme de paiement
**Ko-fi** — plus établi pour les creators software, ~5% frais.

### Contreparties
- Badge "Founder" visible dans l'app
- Accès à vie à toutes les futures updates Lucky

### Langage marketing
*"Become a Founder — support VRHub and get lifetime access to all future Lucky features at the early adopter price of 5€."*

### Juridique
Promettre "lifetime Lucky updates" (service) plutôt que "features X et Y" (produit). Plus flexible si tu changes d'avis plus tard.

### Distribution
APK builds distribuées via Ko-fi après purchase (lien de download). Même repo GitHub public.

---

## Phase 2 — Lucky Tier (après développement)

### Features Lucky
- **Multi-serveurs** (3+ profiles serveur switchable)
- **Sync cloud** (favoris + settings entre devices)
- Priority download queue (plus tard)
- Advanced analytics (plus tard)

### Prix
| Tier | Prix | Description |
|------|------|-------------|
| **Supporter** | 5€ lifetime | Badge + Lucky updates futures |
| **Lucky** | 10€ lifetime | Multi-serveurs + Sync cloud + toutes les futures updates |

### Protection du code
**Tout reste dans le repo public (open source).**

Les features Lucky sont derrière une validation serveur :
```
[App] → [Ton Serveur VPS] → "Oui, license valide" → Lucky features activées
```

Même si qqn modifie le code pour essayer de bypass, les features Lucky ne marchent pas sans license valide sur ton serveur.

---

## Infrastructure Serveur (pour validation + sync future)

### Stack technique
- **VPS OVH existant** (2GB RAM / 30GB) — suffit amplement
- **API** : Rust + Axum (léger)
- **Database** : SQLite (début) → PostgreSQL (si scale)
- **OS** : Ubuntu 22.04 LTS

### Endpoints API
```
POST /validate     — validation license (license_key → {valid, tier})
GET  /sync         — récupère données user
POST /sync         — push données user
```

### Stack serveur
**Rust** avec [Axum](https://github.com/tokio-rs/axum) — moderne, léger (~50MB RAM), excellent performance.

Pourquoi Rust : mémoire minimale (important pour VPS 2GB), très rapide, sécurisé.

### Coût
- Phase initiale : **0€** (VPS existant)
- Si scale (100+ users actifs) : ~5-10€/mois pour VPS plus costaud

### Specs serveur vs besoins
| Ressource | Dispo | Nécessaire |
|-----------|-------|------------|
| RAM | 2GB | ~512MB-1GB |
| Stockage | 30GB | ~100MB pour 1000 users |
| CPU | partagé | ✅ OK (API légère) |

---

## Architecture de protection Lucky

### Principe
```
Repo GitHub public (100% open source)
│
├── feature_lucky/
│   ├── MultiServerScreen.kt
│   ├── SyncService.kt
│   └── ...
│
└── ServerValidation.kt
    └── checkLicense() → appelle ton VPS
```

Le code Lucky est visible par tous, mais :
- `checkLicense()` vérifie auprès de ton serveur
- Sans license valide, les features Lucky sont désactivées
- Pas de private repo =利于 community et contributions

### Revocation
Tu peux révoquer une license côté serveur si needed (fraude, etc).

---

## Questions ouvertes

- [x] Payment provider : **Ko-fi**
- [x] Prix early adopter : **5€ lifetime**
- [x] Prix Lucky (later) : **10€ lifetime**
- [x] Badge : **"Founder"**
- [x] Nom tier : **Lucky**
- [x] Code : **100% public, features derrière serveur**
- [x] Durée offre early adopter : **jusqu'aux Lucky features livrées**
- [x] Stack serveur : **Rust + Axum**
- [ ] Lien Ko-fi à créer (URL) — à définir plus tard
