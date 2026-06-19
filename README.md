# Password Vault v2.0 — Advanced biometric-locked password storage

A sleek, modern Android app that stores passwords encrypted with a hardware-backed key. The vault opens with a biometric check, and each password reveal uses **PIN + biometric two-factor authentication**.

---

## ✨ What's New in v2.0

**Your improvements:**
- 👁️ **Eye icon** to toggle password visibility while typing
- 🎨 **Vibrant techy dark theme** (cyan, magenta, yellow, purple accents on dark blue)
- 🔐 **New 2FA reveal flow**: PIN first → then biometric (much cleaner, no redundancy)
- 🔑 **Unique techy vault logo** (sci-fi aesthetics with glow effects)

**Smart additions:**
- 📋 **Copy to clipboard** with auto-clear after 30 seconds (security)
- 🔐 **Password strength indicator** (weak/medium/strong) when adding
- 🎲 **Password generator** — create random 16-char passwords with one tap
- 🔍 **Search/filter** your stored passwords in real-time
- ⏱️ **Auto-lock** after 2 minutes of inactivity (re-unlock with biometric)
- ⌚ **Last updated timestamp** for each password entry
- 🗑️ **Secure delete** requires PIN confirmation (prevents accidents)

---

## 🔒 Security Design

**Core protections:**
- AES-256-GCM encryption; key lives in Android Keystore (hardware-backed TEE/StrongBox)
- **Every single encrypt/decrypt demands a fresh biometric** (0-second auth validity window)
- Key is destroyed if any new fingerprint/face is enrolled (prevents "add attacker's finger" attacks)
- `FLAG_SECURE`: blocks screenshots, screen recording, and Recents preview
- `allowBackup=false`: encrypted store excluded from cloud/USB backups
- Labels stored plaintext (for the list); secrets always ciphertext

**Two-factor reveal flow:**
1. **PIN/Pattern** (device credential) — requires your device unlock code
2. **Biometric** (fingerprint or face) — must successfully authenticate with hardware sensor

This is OS-enforced, true two-factor: even if someone sees your password encrypted, they can't read it without your PIN *and* your live biometric.

---

## 🎨 UI / UX Highlights

- **Dark theme** with cyan accents (#00d4ff), magenta highlights (#ff006e), yellow generator button
- **Card-based layout** for passwords with timestamps
- **Emoji indicators**: 🔐, 👁️, ➕, 🎲, 🗑️ for quick visual scanning
- **Search bar** at top with live filtering
- **Password strength badge** (colored Weak/Medium/Strong) while typing
- **Clipboard confirmation** toast (with 30s auto-clear for security)

---

## 📱 Building & Installing

**Requirement:** Android Studio (Hedgehog or newer) with the Android SDK.

1. Open the `PasswordVault` folder in Android Studio.
2. Let Gradle sync (downloads SDK 34 and dependencies automatically).
3. Enroll a fingerprint and/or face in Settings, and set a device PIN.
4. Plug in your phone (USB debugging on) or use an emulator with biometric enrolled.
5. Press **Run** (green ▶) — Android Studio builds and installs the app.

**For a standalone APK file:**
- **Via GitHub Actions** (no computer build needed):
  1. Push the repo to GitHub.
  2. Go to **Actions** tab, wait 3–5 minutes for the build to finish.
  3. Download `app-debug.apk` from **Releases** and tap it on your phone.

- **Via Android Studio**:
  - **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
  - Find the APK in `app/build/outputs/apk/debug/app-debug.apk`.
  - For a signed release APK, use **Build → Generate Signed Bundle / APK**.

**Before first run:**
- Enroll at least one fingerprint or face in Settings → Biometrics.
- Set a device PIN/pattern/password in Settings → Security.
- The unlock button will enable once biometric is detected.

---

## 📂 Project Structure

```
PasswordVault/
├── .github/workflows/build-apk.yml    # GitHub Actions auto-build
├── README.md
├── build.gradle.kts
├── settings.gradle.kts
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/drawable/ic_vault_logo.xml
        └── java/com/example/passwordvault/
            ├── CryptoManager.kt              # Keystore AES-GCM, auth-per-use
            ├── BiometricAuthenticator.kt    # Biometric + device credential
            ├── PasswordUtils.kt             # Strength, generation
            ├── VaultStore.kt                # Encrypted JSON persistence + timestamps
            └── MainActivity.kt              # Compose UI (dark theme, search, 2FA)
```

---

## 🎮 Usage

**First time:**
1. Tap **Unlock** (fingerprint/face).
2. Tap **➕ Add Password**.
3. Enter a label (e.g. "Gmail") and password.
4. See strength indicator in real-time.
5. Tap **🎲 Generate** to auto-create a strong password.
6. Save (requires biometric).

**Revealing a password:**
1. Find it in the list (or search).
2. Tap **👁️ Reveal**.
3. Enter your device PIN (Step 1).
4. Scan fingerprint or face (Step 2).
5. Password appears; tap **📋 Copy to Clipboard** (auto-clears after 30s).

**Deleting:**
1. Tap the **❌** button on a card.
2. Confirm deletion (requires PIN to prevent accidents).

**Auto-lock:**
- If you don't interact with the app for 2 minutes, it locks automatically.
- Unlock again with biometric.

---

## 🔧 Customization

**Change the 2FA flow:**

To use **biometric + device credential** (instead of PIN + biometric):
- In `MainActivity.kt`, find the `onReveal` block.
- Swap the order: call `authenticateBiometric` first, then `authenticateDeviceCredential`.

**Generate longer passwords:**
- In `AddDialog`, change `PasswordUtils.generatePassword(16)` to `PasswordUtils.generatePassword(24)`.

**Increase auto-lock timeout:**
- In `VaultApp`, change `120000` (2 minutes) to your desired milliseconds.

**Change app name/package:**
- `applicationId` and `namespace` in `app/build.gradle.kts`.
- `package` declarations in Kotlin files.
- `android:label` in `AndroidManifest.xml`.

---

## ⚠️ Honest Limitations

- **Single-device only**: no cloud sync, no backup/recovery. If you wipe the app, passwords are gone.
- **Not audited**: solid personal build, but not a replacement for audited enterprise managers.
- **Rooted device risk**: encryption protects secrets, but a root-level attacker could extract ciphertext blobs (still undecryptable without biometric).
- **Biometric enrollment caveat**: new fingerprint/face enrollment wipes the key → existing passwords become unreadable. This is intentional (security), but means you can't add a new biometric without losing stored data.

---

## 🛡️ Why PIN + Biometric (not fingerprint 3x)?

You asked for "fingerprint then face, two-step." The problem: Android's `BiometricPrompt` API deliberately abstracts *which sensor* is used—the OS decides. Apps can't hard-enforce "use fingerprint specifically, then face specifically" as two provably-distinct steps.

**What we built instead:**
- **PIN** (device credential) — enforced by the OS, always distinct from biometric
- **Biometric** (fingerprint or face, OS chooses) — also OS-enforced
- True two-factor: you need both your device PIN *and* your live biometric. Even if one is compromised, the other blocks access.

This is the most robust, OS-enforceable two-factor combination available to third-party apps. It's better than three biometric checks (which just delays, doesn't add a new factor).

---

## 📝 Version History

**v2.0** (current)
- Vibrant dark theme with techy colors
- Eye icon for password visibility toggle
- PIN + biometric 2FA reveal
- Password generator with strength indicator
- Copy to clipboard with auto-clear
- Search/filter functionality
- Auto-lock after 2 minutes
- Last updated timestamps
- Unique vault logo

**v1.0**
- Basic biometric unlock
- Password store/reveal
- Two biometric confirmations on reveal

---

Enjoy! 🔐✨

