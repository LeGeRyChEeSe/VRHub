# Environnement de test & process branche→PR

Ce document décrit l'environnement **déjà validé** sur cette machine (Windows 11) et le process de livraison imposé. Sonnet 5 peut s'y fier pour tester réellement l'app.

## 1. Environnement de test validé

### 1.1 SDK / émulateur

- Android SDK : `C:\Users\kilian\AppData\Local\Android\Sdk`
- `adb` : `C:\platform-tools\adb`
- AVD disponibles : `Pixel_7`, `Pixel_6`, `Resizable_Experimental`, `XR_Headset`, `ci_api29`.
- **Émulateur utilisé et vérifié** : `Pixel_7` (booté avec succès, `emulator-5554`).

Boot (bash) :
```bash
"$LOCALAPPDATA/Android/Sdk/emulator/emulator" -avd Pixel_7 -no-snapshot -no-boot-anim -netdelay none -netspeed full &
# attendre le boot :
until [ "$(/c/platform-tools/adb -s emulator-5554 shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
```

> Note VR : `XR_Headset` existe mais l'app cible se teste correctement sur un profil téléphone (`Pixel_7`) — l'UI est responsive (`BoxWithConstraints`, seuil `maxWidth > 800.dp` bascule en grille 3 colonnes).

### 1.2 Build + install (validé, exit 0)

```powershell
# Build dev-debug (baseline confirmée qui compile)
& "scripts\gradlew.bat" :app:assembleDevDebug --console=plain
```
APK produit : `app/build/outputs/apk/dev/debug/VRHub-v<version>-dev.apk`
Package installé : **`com.vrhub.debug`**, activité : `com.vrhub.MainActivity`.

Install avec permissions accordées :
```bash
/c/platform-tools/adb -s emulator-5554 install -r -g "app/build/outputs/apk/dev/debug/VRHub-v4.1.6-dev.apk"
/c/platform-tools/adb -s emulator-5554 shell am start -n com.vrhub.debug/com.vrhub.MainActivity -W
```

**Résultat du smoke-test** : l'app démarre sans crash (activité affichée en ~2,2 s) et présente l'écran de configuration de premier lancement (onglets *JSON URL* / *Manual Entry*, champs *JSON Configuration URL* + *Password (optional)*, boutons *TEST* / *SAVE* / *SKIP*, encart *Disclaimer*).

Capture logcat des crashs :
```bash
/c/platform-tools/adb -s emulator-5554 logcat -c   # clear avant test
# ... interagir ...
/c/platform-tools/adb -s emulator-5554 logcat -d | grep -iE "AndroidRuntime|FATAL|E vrhub"
```

Screenshot (attention à la conversion de chemin sous Git Bash → préfixer `MSYS_NO_PATHCONV=1` et doubler le slash) :
```bash
MSYS_NO_PATHCONV=1 /c/platform-tools/adb -s emulator-5554 shell screencap -p //sdcard/s.png
MSYS_NO_PATHCONV=1 /c/platform-tools/adb -s emulator-5554 pull //sdcard/s.png ./s.png
```

### 1.3 Serveur back-end (vrhub-server)

- Binaire : `../vrhub-server/server.exe` (Go). Flags : `-data-dir <path>`, `-port <int>`.
- Démarrage : `./server.exe -data-dir ./testdata -port 8080` (répond sur `:8080`).
- **Depuis l'émulateur**, le host se joint via **`http://10.0.2.2:8080`** (pas `localhost`).
- Le serveur entre en **mode setup** quand `config.toml` est absent (API publique → 503). Servir un vrai catalogue nécessite de configurer le serveur et d'y déposer des jeux (APK/OBB) — non disponible ici. Pour tester les chemins d'erreur/offline du client, pointer le client sur le serveur nu suffit.

### 1.4 Protocole client ↔ serveur (utile pour reproduire des bugs réseau)

- Config client = `ServerConfig(baseUri, password, monetizationUrl?, kofiVerificationToken?)`, `password` **encodé Base64** puis décodé côté client (`MainRepository.decodeBase64Password`).
- Catalogue : `GET {baseUri}/meta.7z` → archive 7z **protégée par mot de passe** contenant `VRP-GameList.txt` (+ `thumbnails/`, `notes/`, `trailers/`, icônes `.png/.jpg`).
- Fraîcheur : entêtes `Last-Modified` / `ETag` / `MD5` comparés au cache.
- Fichiers d'un jeu : listés sous `{baseUri}/{md5(releaseName + "\n")}/` (listing HTML parsé par regex `HREF_REGEX`), segments APK/OBB/7z téléchargés par `Range`.

## 2. Process de livraison (imposé)

Pour **chaque** item (bug ou feature) :

1. Partir de `main` à jour : `git switch main && git pull`.
2. Créer une branche dédiée :
   - bug : `fix/<slug-court>` (ex. `fix/main-thread-config-read`)
   - feature : `feat/<slug-court>` (ex. `feat/appearance-customization`)
3. Implémenter le changement **le plus minimal et ciblé possible** ; suivre les conventions du fichier voisin (densité de commentaires, nommage, idiomes).
4. Ajouter/mettre à jour un **test JVM** (JUnit/Robolectric/MockK — déjà en dépendances) si la surface s'y prête. Le module `androidTest` compile et tourne en CI (`connectedDebugAndroidTest`) — vérifier par une compilation réelle avant d'écrire un test instrumenté si un doute subsiste, mais ce n'est plus une contrainte connue bloquante.
5. Vérifier le build : `& "scripts\gradlew.bat" :app:assembleDevDebug` (et `testDevDebugUnitTest` si tests ajoutés).
6. Commit conventionnel (`fix: …`, `feat: …`), une seule PR par item.
7. `gh pr create --base main --title "…" --body "…"` — décrire le problème, la repro, le correctif et la façon dont il a été vérifié.
8. Si possible, **valider le comportement sur l'émulateur** (relancer l'app, reproduire le scénario) avant de marquer la PR prête.

### Conventions de PR

- Titre = message de commit conventionnel.
- Corps : `## Problème` / `## Cause` / `## Correctif` / `## Vérification` (pour un bug) ; `## Objectif` / `## Changements` / `## Captures` / `## Tests` (pour une feature).
- Lier l'item du backlog correspondant (`docs/sonnet5-specs/01-bug-audit-backlog.md#<ancre>`).
