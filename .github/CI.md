# CI

CI runs on pull requests, pushes to `main`, and manual dispatches. It does not
assemble APKs, run Release optimization, start an emulator, or call real vehicle
or server APIs. Android Lint and unit tests still compile the code they analyze.

## Checks

| Job | Tasks |
| --- | --- |
| `quality` | `spotlessCheck`, `lintDebug lintRelease`, `:build-logic:convention:check` |
| `unit-tests` | `testDebugUnitTest :core:domain:test` |
| `ci-required` | Succeeds only when both preceding jobs succeed |

The explicit domain task is required because `core:domain` is a JVM module.
Android Lint errors fail CI; warnings remain visible in its reports. Lint also
checks the app's dependencies. Formatting includes every current module and the
included `build-logic` build, excluding generated files.

## Run locally

Use the checked-in Gradle Wrapper with JDK 25 and Android SDK Platform 37.0.

```sh
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew lintDebug lintRelease --continue
./gradlew :build-logic:convention:check
./gradlew testDebugUnitTest :core:domain:test --continue
```

`spotlessApply` updates formatting locally. CI only checks formatting; it never
commits changes. Spotless and ktlint versions are pinned in
`gradle/libs.versions.toml`, and shared style settings live in `.editorconfig`.

## Operations

- A newer commit cancels an older CI run for the same PR. Runs on `main` and
  manual runs are not cancelled by newer runs.
- Quality and unit tests run independently, each with a 30-minute limit.
- Within `quality`, independent checks continue after another check fails.
- Reports are uploaded on success and failure and retained for seven days.
- Gradle uses one GitHub cache provider. Pull requests only read the cache.
- Gradle Wrapper validation is enabled. Third-party Actions are pinned to commit
  SHAs. No external quality service, build scan, or additional API key is used.

After the workflow is available on GitHub, select `ci-required` as a required
status check in the branch rules for `main`. This is a repository setting and
cannot be enforced by the workflow file itself. Failed, skipped, or cancelled
dependencies must not satisfy this check.
