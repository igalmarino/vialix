# Contributing to Vialix

Thanks for helping. This page covers the mechanics; the architecture and its reasoning are in
[`ARCHITECTURE.md`](ARCHITECTURE.md). Shared coding-agent instructions live in
[`AGENTS.md`](AGENTS.md); Claude Code loads them through [`CLAUDE.md`](CLAUDE.md).

## Setting up

You need a JDK 25 toolchain and an Android SDK with platform 36 and build-tools 36.0.0. The
[dev container](.devcontainer) has both; otherwise see the *Build and run* section of the README.

```sh
./gradlew assembleFullDebug testFullDebugUnitTest lintFullDebug   # what CI runs on every push (plus the foss flavour)
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
```

The debug build has the application id `com.galmarino.vialix.debug`, so it installs next to a
release build.

## Claude Code and Codex

The dev container installs both CLIs and VS Code extensions. Rebuild the container after changes
under `.devcontainer/`; each CLI's login and settings live in its own persistent Docker volume.
From the repository root, run `claude` or `codex` and sign in on first use. Existing containers can
install the CLIs with `bash .devcontainer/install-claude-code.sh` and
`bash .devcontainer/install-codex.sh`.

Both agents use the same Gradle commands above. `AGENTS.md` holds the shared commands and
conventions; `CLAUDE.md` imports it using [Claude Code's supported import syntax](https://code.claude.com/docs/en/memory#agentsmd).
Keep detailed design notes in `ARCHITECTURE.md` so the shared instructions stay below
[Codex's default 32 KiB limit](https://developers.openai.com/codex/guides/agents-md).
Restart the agent session after changing its instructions. In Claude Code, `/context` shows the
loaded memory files; in either agent, ask it to summarise the repository instructions to check them.

Check installation and authentication with:

```sh
claude --version
claude auth status
codex --version
codex login status
```

Gradle writes to `~/.gradle` and downloads dependencies on the first build. If an agent's sandbox
blocks those operations, approve the specific build command when prompted. Some container hosts
disable unprivileged user namespaces, which prevents Codex's Linux sandbox from starting even
for read-only commands; this requires a host/container sandbox configuration change or approved
execution outside the sandbox.

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
- Update `ARCHITECTURE.md`, `AGENTS.md` and the README where relevant, and add a line
  to `CHANGELOG.md` under *Unreleased*.
- Source files start with the SPDX header (`GPL-3.0-or-later`). Your contributions are licensed
  under the same terms.

## Releasing

Versions follow [semantic versioning](https://semver.org). `versionName` is the version;
`versionCode` goes up by one per release (both in `app/build.gradle.kts`). To release:

Before tagging, exercise the lifecycle paths that JVM tests cannot cover:

- On both a Play Services device/emulator (`full`) and a GMS-free device/emulator (`foss`),
  deny precise location, grant it, revoke it in system settings while Vialix is paused, and grant it
  again. Confirm the puck and route preview recover without restarting the process.
- Turn the system location switch off and on. Confirm the snackbar action opens location settings
  and updates disappear and resume with the switch.
- Start guidance, background and restore the app, then complete and dismiss a simulated trip.
  Confirm the foreground notification, screen-awake state, TTS and location subscriptions stop.
- Begin assigning Home or Work, then open both coordinate and text directions links. Confirm the
  favourite is unchanged, the requested destination opens, and changing app language does not
  replay the consumed link.
- Disconnect networking during search, route preview and map-style loading, then reconnect and
  Retry. Confirm stale responses do not replace the current query, destination or theme.
- Exercise a real drive or emulator route that leaves the planned route; the built-in simulation
  follows its route exactly and cannot validate rerouting.

1. Move the *Unreleased* entries in `CHANGELOG.md` under the new version and date.
2. Bump `versionCode` and `versionName`, merge to `main`.
3. Tag it: `git tag v0.2.0 && git push origin v0.2.0`. The *Release* workflow builds the signed APK
   (from all four `VIALIX_*` repository secrets, see `.github/workflows/release.yml`). It rejects
   a tag that does not equal `v<versionName>`, verifies both APK signatures and embedded versions,
   then attaches the exact APKs, their SHA-256 checksums and the R8 `mapping.txt` files to the
   GitHub release. Ordinary CI continues to build unsigned release APKs to exercise R8.
