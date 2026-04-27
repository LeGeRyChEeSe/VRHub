# Deferred Work

## Deferred from: code review of story-10-3-configure-server-via-manual-kv-pairs (2026-04-27)

- **Delete of last KVPair impossible** — pre-existing. When only 1 KVPair exists, delete button is hidden with no way to reset to empty state.
- **SAVE button disabled after isSaved=true** — pre-existing. Once saved, the button stays disabled requiring user to re-test after navigating back.
- **Extra keys case-sensitive vs baseUri/password ignore-case** — pre-existing. Repository preserves case for extra keys while validateManualConfig uses ignoreCase for baseUri/password.
- **No debounce on TEST button** — pre-existing. Rapid clicks can queue multiple test jobs though only the latest is tracked.

## Deferred from: code review of story-10-1-configuration-screen-on-first-launch (2026-04-27)

- **TEST button non implémenté** — deferred, pre-existing. TEST button est explicitement scope story 10.4, pas 10.1. Implémentation complète du TEST avec connection test et JSON structure check appartient au story 10.4.
- **Placeholder password invalide le système de validation** — deferred: bridge intentionnel, password validé en 10.4 (TEST button). `"pending_validation"` est un placeholder acceptable pour 10.1.
- **SAVE sans validation en mode JSON URL** — deferred: pas de validation requise dans l'AC de 10.1. Validation viendra avec 10.4.
