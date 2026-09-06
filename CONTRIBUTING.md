# Contributing to Vialix

Thanks for helping. This page covers the mechanics; the architecture and its reasoning are in
[`CLAUDE.md`](CLAUDE.md), which is the project's design notes (written for an AI coding assistant,
readable by anyone).

## Setting up

You need a JDK 25 toolchain and an Android SDK with platform 36 and build-tools 36.0.0. The
[dev container](.devcontainer) has both; otherwise see the *Build and run* section of the README.

```sh
./gradlew assembleFullDebug testFullDebugUnitTest lintFullDebug   # what CI runs on every push (plus the foss flavour)
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
```

The debug build has the application id `com.galmarino.vialix.debug`, so it installs next to a
release build.

## Making a change

- Branch from `main`, open a pull request. CI must be green: unit tests, lint (zero errors) and
  the R8 release build.
- Formatting is checked by ktlint in CI (rules in `.editorconfig`). Fix most findings with
  `ktlint --format "app/src/**/*.kt"` ([ktlint CLI](https://github.com/pinterest/ktlint/releases), 1.8.0).
- A new dependency needs an entry in `ThirdPartyLicenses.kt` (a unit test checks the version
  catalog against it) so the in-app licences screen stays complete.
- Keep domain logic UI-free and unit-tested (`app/src/test`, plain JUnit 4 on the JVM). Anything
  that needs Android stays thin and is wired in `AppGraph`.
- Every user-facing string goes into `res/values/strings.xml` **and** the four translations
  (`values-de`, `values-es`, `values-fr`, `values-it`); a missing one is a lint error. British
  spelling in the base strings.
- Dependency versions live only in `gradle/libs.versions.toml`. Ferrostar and MapLibre Compose move
  together, and AGP / Kotlin / Compose mirror what Ferrostar is built against: check its release
  notes before bumping any of them.
- Update `CLAUDE.md` and the README where they describe the behaviour you changed, and add a line
  to `CHANGELOG.md` under *Unreleased*.
- Source files start with the SPDX header (`GPL-3.0-or-later`). Your contributions are licensed
  under the same terms.

## Releasing

Versions follow [semantic versioning](https://semver.org). `versionName` is the version;
`versionCode` goes up by one per release (both in `app/build.gradle.kts`). To release:

1. Move the *Unreleased* entries in `CHANGELOG.md` under the new version and date.
2. Bump `versionCode` and `versionName`, merge to `main`.
3. Tag it: `git tag v0.2.0 && git push origin v0.2.0`. The *Release* workflow builds the signed APK
   (from the `VIALIX_*` repository secrets, see `.github/workflows/release.yml`) and attaches it,
   with the R8 `mapping.txt`, to a GitHub release.
