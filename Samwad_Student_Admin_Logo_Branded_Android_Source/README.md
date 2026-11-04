# Samwad LMS Android — Student and Admin apps

## Status / release gate

- Complete Android Studio source package (two Android application flavors).
- Automated cloud build workflows for **two debug APKs** and optional **two signed release APKs**.
- **No APK is included**. A build must run in Android Studio or GitHub Actions.
- **No Android device testing or production sign-off has been completed.** Do not publicly distribute.
- Both apps currently load `https://preview--samwad-learning-feed.apper.so` (the existing connected preview). Replace with the approved production hostname before public release.
- Original Samwad logo is not yet embedded as a production launcher icon; current launcher artwork is a temporary tricolour placeholder.

## Apps

| Variant | Package ID | Login URL |
|---|---|---|
| Student | `in.samwad.learning.student` | `https://preview--samwad-learning-feed.apper.so/student/login` |
| Admin | `in.samwad.learning.admin` | `https://preview--samwad-learning-feed.apper.so/admin/login` |

The project is a secure-ish **online Android WebView wrapper**, not a separate native backend. It does not add offline playback, copyright protection, server-side quiz grading or enhanced database authorisation. All access decisions must continue to be enforced by the LMS backend.

## Fastest route: build both debug APKs with GitHub Actions

1. Create a **private GitHub repository** for this project and upload **all unzipped contents**, including `.github/workflows/`.
2. GitHub → **Actions** → **Build Samwad Student and Admin APKs** → **Run workflow**.
3. When successful, download both workflow artifacts:
   - `samwad-student-debug-apk` → `app-student-debug.apk`
   - `samwad-admin-debug-apk` → `app-admin-debug.apk`
4. Install on two Android test accounts/devices (or a test device with both apps). Android may ask permission to install from an unknown source. Only enable this for your trusted build.
5. Do not publicly distribute a debug APK. It is signed automatically with a development key.

**If the workflow is unavailable**, confirm the repository includes the `.github` folder (some Windows archive tools hide dot-directories), that Actions is enabled, and that the default branch is `main` or `master`.

## Android Studio / Windows local build

- Install Android Studio and Android SDK Platform 35, Build Tools 35.0.0, Android SDK Platform Tools, and JDK 17.
- Install Gradle **8.9** on PATH (this package does not include `gradlew`, the Gradle wrapper JAR or Gradle distribution).
- Open this folder in Android Studio or PowerShell and run `./build-apks.ps1`.
- Produced APKs: `app/build/outputs/apk/student/debug/app-student-debug.apk` and `app/build/outputs/apk/admin/debug/app-admin-debug.apk`.
- Android Studio Build Variants: `studentDebug`, `adminDebug`.

## Signed releases (owner-held signing key)

Create your **own** signing keystore and keep its private key and passwords offline. Do not send keystore or password in ChatGPT.

```powershell
keytool -genkeypair -v -keystore samwad-release.jks -alias samwad -keyalg RSA -keysize 3072 -validity 10000
```

For manual Windows signing, set `SAMWAD_KEYSTORE_PATH`, `SAMWAD_KEYSTORE_PASSWORD`, `SAMWAD_KEY_ALIAS`, and `SAMWAD_KEY_PASSWORD` environment variables, then run:

```powershell
gradle :app:assembleStudentRelease :app:assembleAdminRelease
```

Expected signed APKs: `app/build/outputs/apk/student/release/app-student-release.apk` and `app/build/outputs/apk/admin/release/app-admin-release.apk`. Verify signatures with Android SDK `apksigner verify --print-certs` on each.

For CI release signing, register your key as **GitHub Actions encrypted secrets** (not repository files): `SAMWAD_KEYSTORE_BASE64`, `SAMWAD_KEYSTORE_PASSWORD`, `SAMWAD_KEY_ALIAS`, `SAMWAD_KEY_PASSWORD`. Restrict the `android-release` GitHub Environment to approved maintainers. Run **Build signed Samwad release APKs** manually; review every release before external distribution. Save the keystore backup securely: losing it can stop updates to an APK installed outside Google Play.

## Device acceptance checklist (must run before distribution)

- [ ] Device running Android 8+ installs both APKs and app icons appear.
- [ ] Student credentials authenticate on the phone and log out correctly; admin account cannot access student-only pages.
- [ ] Admin sign-in works; student account cannot access `/admin` routes or APIs.
- [ ] Administrator invites a test student, publishes a class with an actual lesson and enrols that student.
- [ ] Enrolled student views only their lessons/materials; unenrolled student cannot fetch them with direct URLs/API requests.
- [ ] Student A cannot view student B's enrolment, progress, quiz attempts or profile information.
- [ ] Video plays fullscreen and returns to lesson; PDFs download or open on actual devices.
- [ ] External URLs and hosted OAuth / third-party authentication tested; providers may refuse embedded WebView sign-in.
- [ ] No HTTP requests, TLS bypass, file:// access, or WebView debugging in release.
- [ ] Back navigation, rotation, slow connection, offline error, session expiry and revoked access behave sensibly.
- [ ] Accessibility and responsive layout tested on different Android screen sizes.
- [ ] App's launcher icon replaced with approved source logo. Production domain and all privacy links are approved.
- [ ] Production LMS fixes browser-tamperable quiz results, permission scoping and private lesson media before student rollout.
- [ ] Operator approves student privacy notice, deletion process, parental/guardian consent requirements, and Play Store / local distribution policies.

**Release status remains BLOCKED pending acceptance tests and security remediation.**


## Exact official Samwad logo used as Android icon

The app **does not use the previous three-bar placeholder**. Both Student and Admin
launchers now use the same official logo from the connected Samwad LMS.

The website publishes the logo at:
`https://preview--samwad-learning-feed.apper.so/samwad-icon.svg`

GitHub Actions automatically fetches this SVG, decodes its embedded original logo,
and generates all five Android launcher icon densities plus the adaptive icon.
The workflow intentionally **fails if the logo cannot be obtained**; it does not
silently ship an incorrect image.

For offline or reproducible builds, download `samwad-icon.svg` from the link
above and save it as `branding/samwad-icon.svg`, then run:

```bash
python3 -m pip install Pillow
python3 scripts/prepare_icons.py
gradle :app:assembleStudentDebug :app:assembleAdminDebug
```

The icon asset is derived from the exact original brand image. These changes do
not themselves produce a signed APK; compilation, device tests and security
release approval are separate steps.
