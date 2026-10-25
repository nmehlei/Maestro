# About this fork

This repository is an unofficial fork of [Maestro](https://github.com/mobile-dev-inc/Maestro).
It is not the official Maestro and is not affiliated with or endorsed by mobile.dev. The change
it carries is intended to be proposed upstream.

Maestro is licensed under the Apache License 2.0 (see [LICENSE](LICENSE)); so is this fork.

## What it changes

One option for `maestro test`:

```sh
maestro --device <serial> test --android-user <id> flow.yaml
```

With `--android-user`, every app-targeting Android command acts on that Android user, for
example a work profile, instead of the primary user. This makes it possible to test flows that
depend on the user's accounts, such as a first-time Google sign-in, in a fresh profile without
touching the device's primary accounts.

- `launchApp` starts the app's first launcher activity with `am start --user <id>` from the
  shell. Maestro's on-device driver runs in the primary user and cannot start activities in
  another one. Launch arguments are passed as intent extras (`--es`, `--ez`, `--ei`, `--el`,
  `--ed`).
- `stopApp`, `killApp`, `clearState`, permissions (`pm grant`/`pm revoke`, `appops`) and
  `openLink` get `--user <id>`.
- The "is the app installed" check looks at that user. Maestro's own driver apps are still
  looked up in the primary user.

Taps, text input, assertions and the view hierarchy need no change: a work profile shares the
primary user's screen. Without `--android-user`, Maestro behaves exactly like upstream. The
option exists only on `maestro test`; other commands reject it rather than silently acting on
the primary user.

### Constraint: Double launch arguments

Older Android versions' `am` (e.g. Android 12) has no `--ed`, so `Double` launch arguments
cannot be passed to an app in another user there. Such a launch fails with a message asking to
pass the value as a String. The type is never converted silently.

### Changed files

Modified upstream files:

- `maestro-client/src/main/java/maestro/drivers/AndroidDriver.kt`
- `maestro-client/src/main/java/maestro/android/AndroidAppFiles.kt`
- `maestro-cli/src/main/java/maestro/cli/command/TestCommand.kt`
- `maestro-cli/src/main/java/maestro/cli/session/MaestroSessionManager.kt`
- `README.md` (fork notice); upstream's `.github/workflows` are removed in this fork

Added files: `AndroidUserCommands.kt`, `AndroidUserOption.kt` and their tests,
`AndroidDriverAndroidUserTest.kt`, `AndroidUserCliOptionTest.kt`, this file, `.fork/` and
`.github/workflows/fork-*.yaml`.

## Releases

Releases are tagged `cli-<upstream version>-android-user.<n>`, e.g. `cli-2.6.1-android-user.1`,
and contain `maestro.zip` (same layout as upstream's) and its SHA-256 checksum. The CLI reports
the upstream version number.

## Following upstream

The branch `android-user` is the upstream release tag named in `.fork/upstream-tag` plus this
fork's commits. Upstream's own workflows are removed on this branch; only `fork-*` workflows run.

The scheduled workflow `Fork upstream sync` checks daily for a newer upstream release
(`cli-X.Y.Z`). It rebases this fork's commits onto it with `.fork/rebase-onto.sh`, runs the
tests, pushes the branch together with the tag `cli-X.Y.Z-android-user.1`, and publishes the
release. If anything fails, it opens an issue and publishes nothing new; a failed release can be
retried with the `Fork release` workflow's "Run workflow" button.

Pushing rebased commits that touch `.github/workflows` needs the repository secret
`UPSTREAM_SYNC_TOKEN`: a fine-grained personal access token with Contents and Workflows
read/write on this repository (`GITHUB_TOKEN` cannot push workflow changes). Without it, the sync
reports new upstream releases as issues instead of upgrading.

To upgrade by hand:

```sh
git fetch https://github.com/mobile-dev-inc/Maestro.git 'refs/tags/cli-X.Y.Z:refs/tags/cli-X.Y.Z'
.fork/rebase-onto.sh cli-X.Y.Z   # exit 2: conflict, resolve by rebasing manually
./gradlew test
git push --force-with-lease origin HEAD:android-user
git tag cli-X.Y.Z-android-user.1 && git push origin cli-X.Y.Z-android-user.1   # publishes the release
```

## Problems

Report problems with this fork in this repository's issues, not upstream.
