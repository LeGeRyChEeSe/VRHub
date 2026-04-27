# Deferred Work

## Deferred from: code review of story-10-1-configuration-screen-on-first-launch (2026-04-27)

- **TEST button non implémenté** — deferred, pre-existing. TEST button est explicitement scope story 10.4, pas 10.1. Implémentation complète du TEST avec connection test et JSON structure check appartient au story 10.4.
- **Placeholder password invalide le système de validation** — deferred: bridge intentionnel, password validé en 10.4 (TEST button). `"pending_validation"` est un placeholder acceptable pour 10.1.
- **SAVE sans validation en mode JSON URL** — deferred: pas de validation requise dans l'AC de 10.1. Validation viendra avec 10.4.
