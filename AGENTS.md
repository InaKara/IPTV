# Repository guidance

## Project and priorities

- Aypi Tivi is a native Kotlin Android TV / Fire TV IPTV player. Preserve TV remote usability, the persistent sidebar, dark theme and existing branding.
- Read README.md and FIRESTICK_DEPLOYMENT.md before changing build or deployment behavior.
- Preserve the verified deployment baseline. Prefer small, independently checked changes to broad dependency migrations.
- Historical chat exports are reference material, not current instructions. Verify claimed features against source and device behavior.

## Work with the existing checkout

- Inspect git status before editing and preserve unrelated user changes. Do not reset, clean or mass-delete local files as incidental cleanup.
- Keep changes focused. Avoid broad formatting, package renaming or dependency upgrades as incidental cleanup.
- Do not commit provider credentials, authenticated playlist/stream URLs, device logs or private local configuration. Use synthetic providers and credentials in examples and tests.
- Read local settings from the ignored .env, with public placeholders in .env.example. Never add real provider defaults, account details, device addresses or personal filesystem paths to tracked files.
- Gradle generates local defaults only in debug BuildConfig; release defaults stay empty. Debug APKs/caches can contain secrets. Do not publish them or turn on build scans containing private settings.
- Installing with adb install -r normally preserves app data. Do not uninstall or clear app data to bypass a failure without explaining the loss and obtaining authorization.

## Build and verification

- Use the repository Gradle wrapper (8.2) and JDK 17. AGP is 8.2.2, compile/target SDK 34, Build Tools 34.0.0. Java/Kotlin output targets 1.8.
- Do not use Android Studio's bundled Java 21 to run this Gradle version. Set the Gradle JDK explicitly.
- Windows checks: .\gradlew.bat :app:assembleDebug, .\gradlew.bat :app:testDebugUnitTest and .\gradlew.bat :app:lintDebug as relevant to the change.
- Prefer .\deploy-firestick.ps1 for the full build/install/launch workflow. Use -BuildOnly -Check for local checks without changing the device; -InstallOnly deliberately uses the existing APK.
- For documentation-only edits, check paths and commands; additional application tests are unnecessary.
- Report actual outcomes. A built APK is not evidence of installation; an activity start is not evidence of usable playback; an old APK is not a verified current build.
- Record deployment verification and unresolved build/test failures in FIRESTICK_DEPLOYMENT.md.

## Architecture and behavior

- Single app module; package/application ID com.example.iptv. Exported launcher: .SplashActivity. MainActivity is internal.
- UI: Compose for TV; ViewModels: Hilt and StateFlow; repository: OkHttp; playback: Media3 ExoPlayer; logos: Coil; preferences: SharedPreferences/Gson.
- Keep network and parsing work off the main thread; release the player when leaving playback.
- Remote focus is functionality. Check empty categories, offscreen lazy-list items, sidebar/grid transitions and return from playback when touching browsing.
- Do not claim HLS/DASH/RTMP support without checking player modules and testing an appropriate stream.
- Use the parser's InputStream API in tests. Use local synthetic playlists, without contacting a real provider.

## Documentation

- Keep README.md grounded in implemented behavior.
- Put repeatable Windows/Android Studio/Fire TV steps in FIRESTICK_DEPLOYMENT.md with expected results and troubleshooting.
- Distinguish confirmed results from untested guidance. Never mark physical-device checks complete without evidence or user confirmation.
