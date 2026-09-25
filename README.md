# Battery Fox

Android battery health app using a 5-tier diagnostic engine stack to work around
SELinux sandbox restrictions and OEM fragmentation. See the architecture doc for
the full design.

## Status

This repo currently has:
- Domain models (`domain/model/`)
- Diagnostic engines (`core/engine/`, `core/parser/`, `core/oem/`, `core/permissions/`)
- Manifest with the Android 11+ `<queries>` package-visibility declarations
- A placeholder `MainActivity` (no UI yet — Phase 3 / Compose dashboard is next)

Not yet built: the repository layer wiring the engines together, and the UI.

## Before your first build

This repo is missing `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`
— the wrapper *scripts and jar* are binary/generated files that need network access
to fetch, which wasn't available when this skeleton was created. Generate them once:

```bash
# If you have Gradle installed locally (or via Android Studio's bundled Gradle):
gradle wrapper --gradle-version 8.7

# This creates gradlew, gradlew.bat, and gradle/wrapper/gradle-wrapper.jar.
# Commit all three — they're meant to be checked into the repo so anyone
# cloning it can build without installing Gradle separately.
```

After that, `./gradlew assembleDebug` should build the app.

## Notes for GitHub Codespaces / gitbash workflow

- Android builds need the Android SDK, not just Gradle. In Codespaces, either use
  a devcontainer image with the SDK preinstalled, or install command-line tools
  via `sdkmanager` in a setup script.
- `local.properties` (which points Gradle at your SDK path) is gitignored on
  purpose — it's machine-specific. Each environment needs its own.
