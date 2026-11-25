# Releasing Baytna

Releases are built and signed on GitHub Actions (`.github/workflows/release.yml`) and published as a
GitHub Release with a signed APK (to install directly) and AAB (to upload to Google Play).

## One-time setup

### 1. Register the release app in Firebase
The debug build is `com.baytna.app.debug`; the release build is `com.baytna.app`.
1. Firebase console → project `baytna-c9bf7` → Project settings → Add app → Android.
2. Package name `com.baytna.app`, nickname `Baytna`. Register.
3. Download the new `google-services.json` (it now lists both apps) and replace `app/google-services.json`.
4. After step 2 below, add the release key's SHA-1 and SHA-256 under the `com.baytna.app` app
   (Project settings → Your apps → Add fingerprint). Get them with:
   `keytool -list -v -keystore baytna-release.jks -alias baytna`

### 2. Create the release key (once, keep it forever)
Run on your own computer (keytool ships with any JDK or Android Studio):

    keytool -genkeypair -v -keystore baytna-release.jks -alias baytna \
      -keyalg RSA -keysize 4096 -validity 10000

Back up `baytna-release.jks` and its password somewhere safe. Losing it means installed copies can
never be updated (on Play, with Play App Signing, it is only the upload key and can be reset).
Never commit it.

### 3. Add repository secrets
GitHub → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | output of `base64 -w0 baytna-release.jks` (macOS: `base64 -i baytna-release.jks`) |
| `RELEASE_KEYSTORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | `baytna` |
| `RELEASE_KEY_PASSWORD` | key password (same as the keystore password unless you set another) |

## Cutting a release
Actions → Release → Run workflow → version `0.1.0`, or push a tag `v0.1.0`.
The version code is derived from the version (`1.2.3` → `10203`), so every release needs a higher version.

## Google Play
Upload the `.aab` from the GitHub Release in Play Console (internal testing first). Enable Play App
Signing; then add Play's app-signing SHA-1/SHA-256 (Play Console → App integrity) to the Firebase app
too, or sign-in fails on Play installs.
