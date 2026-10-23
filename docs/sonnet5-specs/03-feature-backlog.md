# Backlog de nouvelles fonctionnalités — client VRHub

Features proposées (hors personnalisation, traitée dans `02-…`). Chacune = **une branche + une PR**. Priorité indicative : ⭐ (forte) → ○ (nice-to-have). À affiner avec le propriétaire avant de coder.

---

## F1 — ⭐ Écran de détail du jeu (fiche plein écran)
- **Objectif** : au tap sur un jeu, ouvrir une fiche : héro (icône/thumbnail), nom, packageName, taille, version, description complète, **galerie de screenshots** (déjà récupérés dans `getGameRemoteInfo` → `screenshots`), bouton trailer (déjà géré via intent YouTube), actions Install / Download-only / Favori / Désinstaller.
- **Pourquoi** : aujourd'hui tout est condensé dans `GameListItem` ; une fiche améliore la découverte et met en valeur les métadonnées déjà présentes en base.
- **Esquisse** : nouvel écran `GameDetailScreen(releaseName)` piloté par un `currentScreen`/nav state dans `MainScreen` ; réutiliser `MainViewModel` + `getGameRemoteInfo`. Carrousel `LazyRow` d'images (Coil déjà en deps). 
- **Branche** : `feat/game-detail-screen`.

## F2 — ⭐ Pull-to-refresh du catalogue
- **Objectif** : geste « tirer pour rafraîchir » sur la liste/grille pour déclencher `refreshData()`/`syncCatalogNow()`.
- **Pourquoi** : le refresh est aujourd'hui une icône dans la barre ; le geste est le standard attendu.
- **Esquisse** : `PullToRefreshBox` (Material3) autour de `LazyColumn`/`LazyVerticalStaggeredGrid` dans `MainScreen`, lié à `isRefreshing`.
- **Note** : vérifier la version Material3 du BOM `2023.10.01` — si `PullToRefresh` indisponible, utiliser `androidx.compose.material.pullRefresh` (Material 1) ou remonter le BOM (dans ce cas, PR séparée de bump de deps).
- **Branche** : `feat/pull-to-refresh`.

## F3 — ⭐ Écran « Stockage » (gestion de l'espace)
- **Objectif** : lister l'espace occupé par les téléchargements/OBB conservés, par jeu, avec total et bouton « supprimer » par entrée + « tout nettoyer ». 
- **Pourquoi** : l'app télécharge des archives volumineuses (jusqu'à plusieurs Go) ; `downloadsDir`, `tempInstallRoot`, `filesDir` s'accumulent. `clearCache()` existe déjà mais en gros grain.
- **Esquisse** : parcourir `downloadsDir` + `tempInstallRoot` (IO/Dispatchers.IO), agréger tailles, afficher en tri décroissant ; réutiliser `deleteDownloadedGame(releaseName)`.
- **Branche** : `feat/storage-manager`.

## F4 — ⭐ Import/partage de configuration serveur par QR code
- **Objectif** : générer un QR encodant le JSON `ServerConfig` (ou l'URL JSON) pour partager sa config entre casques ; et un scanner pour l'importer (onglet supplémentaire dans `ConfigurationScreen`).
- **Pourquoi** : l'onboarding actuel oblige à saisir URL + password au clavier VR (pénible). Un QR accélère fortement.
- **Esquisse** : génération QR sans dépendance lourde possible (encodeur QR minimal) ou lib légère justifiée ; scan via CameraX + ML Kit **optionnel** (attention aux permissions caméra et à la dispo sur Quest). Prioriser d'abord la **génération** (sortie), le **scan** peut être phase 2.
- **Branche** : `feat/server-config-qr`.

## F5 — ○ Sauvegarde / restauration des favoris & de la config
- **Objectif** : exporter/importer un petit fichier (JSON) contenant favoris (`isFavorite`) + config serveur, pour survivre à une réinstallation.
- **Esquisse** : sérialiser depuis Room + DataStore vers un fichier dans Downloads ; import inverse. Attention à ne pas exporter le password en clair sans avertissement.
- **Branche** : `feat/backup-restore`.

## F6 — ○ Internationalisation (i18n) FR
- **Objectif** : ajouter `res/values-fr/strings.xml`. Beaucoup de chaînes sont déjà externalisées (`stringResource(R.string.…)`), mais des libellés restent **codés en dur en anglais** dans `MainActivity.kt` (« Catalog », « Installation History », « Become Supporter », « Check for Updates », « Settings », « Cancel Download? », etc.).
- **Sous-tâche préalable** : extraire ces libellés codés en dur vers `strings.xml` (PR de refactor `refactor/externalize-strings`), puis traduire (`feat/i18n-fr`).
- **Pourquoi** : le propriétaire travaille en français ; audience FR probable.
- **Branches** : `refactor/externalize-strings` puis `feat/i18n-fr`.

## F7 — ○ Accessibilité & confort VR
- **Objectif** : `contentDescription` sur toutes les icônes actionnables, tailles de cible ≥ 48.dp, focus/navigation à la manette, contrastes AA. Synergie directe avec l'échelle de texte de la feature Personnalisation.
- **Esquisse** : audit a11y écran par écran ; corriger les `Icon(..., contentDescription = null)` porteurs d'action.
- **Branche** : `feat/a11y-pass`.

## F8 — ○ Indicateur de connexion serveur / bannière hors-ligne
- **Objectif** : afficher un état clair « hors-ligne / serveur injoignable » (bannière) distinct d'un catalogue vide, avec bouton « réessayer ».
- **Pourquoi** : améliore le diagnostic quand le serveur perso est down (cas fréquent en self-host).
- **Esquisse** : observer la connectivité + résultat du dernier sync ; réutiliser `ErrorScreen`/bannière existante.
- **Branche** : `feat/offline-banner`.

---

## Ordre suggéré

1. **F1** (fiche jeu) et **F2** (pull-to-refresh) : fort impact UX, risque faible.
2. **F3** (stockage) : utile vu la taille des jeux.
3. **F4** (QR config) : gros gain onboarding — commencer par la génération.
4. **F6** (i18n FR) après le refactor d'externalisation des chaînes (utile aussi pour la Personnalisation qui ajoute des libellés).
5. Le reste selon retour du propriétaire.

> Toutes ces features doivent respecter le process **branche + PR unique** et être validées sur l'émulateur `Pixel_7` (cf. `00-environment-and-workflow.md`).
