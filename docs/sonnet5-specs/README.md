# Specs d'implémentation — pour Sonnet 5

> Ces documents sont des **spécifications d'implémentation prêtes à coder**, rédigées par un agent de revue (Opus/Fable) pour être **implémentées ultérieurement par Sonnet 5**.
> Aucun code applicatif n'a été écrit à ce stade — seulement cette documentation.

## Contexte de la mission

Revue complète du client Android **VRHub** (`com.vrhub`, Kotlin + Jetpack Compose, MVVM, Room, WorkManager, Retrofit, DataStore). Objectifs, dans l'ordre :

1. **Corriger tous les bugs du client**, un bug = une branche dédiée = une PR sur `main`.
2. Ajouter des **nouvelles fonctionnalités** utiles (visuelles ou techniques), une feature = une branche = une PR.
3. Priorité produit explicite du propriétaire : offrir la **personnalisation de l'app** (couleurs, police, agencement) pour rendre la navigation unique. → voir [`02-feature-appearance-customization.md`](02-feature-appearance-customization.md).

## État de la revue automatisée

Un hunt de bugs multi-agents (10 agents : finders par module + lentilles transverses sécurité/concurrence/fuites/offline, avec vérification adversariale) a été **lancé mais interrompu par une limite de session** avant de produire des findings vérifiés. Le backlog d'audit ([`01-bug-audit-backlog.md`](01-bug-audit-backlog.md)) contient donc :

- des **suspicions concrètes** relevées à la lecture du code (avec `fichier:ligne`), chacune **à confirmer avant correction** ;
- un **plan d'audit** couvrant les modules pas encore inspectés en profondeur.

Sonnet 5 doit **re-vérifier chaque item avant de le corriger** (repro/trace) et ne pas traiter ces suspicions comme des faits acquis.

## Index des documents

| Doc | Contenu |
|-----|---------|
| [`00-environment-and-workflow.md`](00-environment-and-workflow.md) | Environnement de test **validé** (émulateur, serveur, build, install), et process branche→PR imposé |
| [`01-bug-audit-backlog.md`](01-bug-audit-backlog.md) | Bugs suspectés (file:line + vérif + fix) et plan d'audit des modules restants |
| [`02-feature-appearance-customization.md`](02-feature-appearance-customization.md) | **Feature phare** : personnalisation couleurs / police / agencement |
| [`03-feature-backlog.md`](03-feature-backlog.md) | Autres features proposées (visuelles + techniques) |

## Règles d'exécution imposées par le propriétaire

- **Une branche dédiée + une PR par item** (bug ou feature). Ne pas empiler plusieurs correctifs indépendants dans une seule PR.
- Cible des PR : `main`. Remote : `origin` (`https://github.com/LeGeRyChEeSe/VRHub.git`).
- Vérifier que le build passe (`assembleDevDebug`) avant de pousser.
- Ajouter un test JVM (Robolectric/JUnit) quand c'est raisonnable — voir la contrainte androidTest ci-dessous.

## Contraintes techniques connues (mémoire projet)

- Le wrapper Gradle est sous `scripts/` : `scripts\gradlew.bat` (Windows) / `scripts/gradlew`. Utiliser les tâches par flavor (`assembleDevDebug`, etc.).
- Dans un worktree, créer `local.properties` (avec `sdk.dir`) et copier `keystore.properties` si besoin.
- **Le source set instrumenté (`androidTest`) ne compile pas** (package pré-rebrand). Écrire les tests en **JVM Robolectric** plutôt qu'en tests instrumentés.
- `minSdk = 29`, `targetSdk = 34`, `compileSdk = 34`, Java 11, Compose BOM `2023.10.01`, Kotlin compiler extension `1.5.8`.
- Flavors : `prod` et `dev` (`applicationIdSuffix = .debug`, `versionNameSuffix = -dev`).
