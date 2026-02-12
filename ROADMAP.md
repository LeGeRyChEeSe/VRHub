# 🗺️ Roadmap

Welcome to the **Rookie On Quest** roadmap! This document gives you an overview of the project's progress and what's planned for future releases.

---

## ✅ Completed

### Persistent Installation Queue System (v2.5.0+)
- Installations that continue even after closing the app or rebooting the headset
- Automatic resumption of interrupted downloads
- Full queue management (pause, resume, cancel, prioritize)
- Pre-flight storage space checks before downloading
- 7z archive extraction with progress tracking
- Automatic APK + OBB file installation
- Clear and guided permission flow for new users

### Build Automation & Releases
- Automated build system via GitHub Actions
- Automatic releases with signed APK and changelog
- Pull Request validation with tests and lint
- Release Candidate (RC) support for test versions
- Dependency caching for faster builds

---

## 🚧 In Progress

### Epic 1 Finalization
- Final fix in progress for temporary APK file management

---

## 📋 Coming Soon

### 🎨 Visual Enhancements
**Animations & Progress**
- Animated character (stickman) showing current installation phase
- Global progress indicator (Step X/4)
- Contextual messages adapted to each phase

### 🔔 Notifications
**Installation Alerts**
- Notifications when installation completes
- Configurable sound alerts
- Error notifications with retry buttons
- Non-intrusive VR overlay display

### 📱 Catalog Management
**Smart Sorting & Search**
- Sort by size (when metadata is loaded)
- Automatic catalog update detection
- Automatic sync with VRPirates servers

### 🌐 Offline Mode
**Use Without Internet**
- Browse cached catalog offline
- Install pre-downloaded games without connection
- Visual offline mode indicators
- Automatic sync when reconnecting

### 🔍 Search & Discovery
**Catalog Improvements**
- Search games by name
- Filter by category/genre
- Favorites system
- Detailed game view with screenshots
- Installation history
- Diagnostics and log export

### 🔧 Silent Installation (Optional)
**Shizuku Integration**
- Install without manual confirmation (for advanced users)
- Automatic fallback to standard installation if Shizuku unavailable

---

## 💡 Important Notes

> **External Dependency**: This project is entirely dependent on the servers and infrastructure maintained by the **VRPirates/Rookie** team. All download and catalog features rely on their availability.

> **Current Version**: Features marked "Completed" are available in recent app versions. Check the [Releases](https://github.com/LeGeRyChEeSe/rookie-on-quest/releases) page to download the latest version.

---

## 📊 Priority Order

1. **Epic 1 Finalization** (fix in progress)
2. **Notifications** (Epic 3) - Important for user experience
3. **Animations** (Epic 2) - Major visual enhancement
4. **Offline Mode** (Epic 5) - Practical utility
5. **Catalog** (Epics 4 & 7) - Discovery improvements
6. **Shizuku** (Epic 6) - Optional feature for advanced users

---

*Last updated: February 10, 2026*