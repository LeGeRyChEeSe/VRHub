# Bridge Update Migration Guide

## Version 2.4.1-bridge

### Why This Manual Update Is Needed

The Rookie On Quest app previously used GitHub Releases to check for and distribute updates. Since the repository transitioned to private status, the standard GitHub update mechanism is no longer accessible to unauthenticated users.

To maintain seamless updates for existing users without exposing the source code or APKs publicly, we have migrated to a custom secured distribution gateway hosted on Netlify (Sunshine-AIO).

**This bridge version (2.4.1-bridge) is a one-time update** that transitions your app from the broken GitHub update path to the new secure Sunshine-AIO gateway. After installing this bridge version, all future updates will be delivered automatically through the new system.

---

### What Changes in This Bridge Version

1. **Update Server Changed**: The app now checks for updates at `sunshine-aio.com` instead of GitHub Releases
2. **Secure Authentication**: All update requests are now signed with HMAC-SHA256 for secure communication
3. **APK Integrity**: Update APKs are verified with SHA-256 checksums before installation
4. **Future Updates**: Once on this bridge version, you'll receive all future updates automatically

---

### Step-by-Step Installation Guide

#### Prerequisites

- A computer with ADB (Android Debug Bridge) installed
- A USB cable to connect your Meta Quest headset
- Developer mode enabled on your Quest device

#### Installation Steps

**Step 1: Download the Bridge APK**

Obtain the `RookieOnQuest-v2.4.1-bridge.apk` file from the release assets.

**Step 2: Connect Your Quest to ADB**

1. Put on your Meta Quest headset
2. Enable Developer Mode in the Quest Settings
3. Connect your Quest to your computer via USB
4. On your Quest, allow USB debugging when prompted

**Step 3: Install the Bridge APK**

Open a terminal/command prompt on your computer and run:

```bash
adb install -r RookieOnQuest-v2.4.1-bridge.apk
```

The `-r` flag ensures the existing app is replaced while preserving app data.

**Step 4: Verify Installation**

After installation completes, verify the version in the app:
1. Open Rookie On Quest on your Quest
2. Go to Settings or About section
3. Confirm the version shows "2.4.1-bridge"

**Step 5: Test Update Check**

1. In the app, trigger a manual update check
2. The app should successfully connect to the new Sunshine-AIO gateway
3. If an update is available, you should be able to download and install it

---

### Troubleshooting

**"Update check failed" error**
- Ensure your Quest's date and time are set correctly (automatic)
- Check your internet connection
- Try again in a few minutes

**Installation failed**
- Make sure you have enough storage space
- Try uninstalling the app first: `adb uninstall com.vrpirates.rookieonquest`
- Then install fresh: `adb install RookieOnQuest-v2.4.1-bridge.apk`

**Still having issues?**
- Contact the development team with details of the error
- Check the app logs in `/sdcard/Download/RookieOnQuest/logs/`

---

### What's Next

After installing this bridge version:
- Your app will automatically check for updates from the new Sunshine-AIO gateway
- Future updates will be delivered seamlessly through the app's built-in update system
- No further manual steps will be required

---

*This bridge update was created as part of Epic 9: Secure Private Distribution System*
