# Agent notes — OrienteerRun

## Git: upload changes (do this)

After meaningful app work (features, fixes, version bumps, Play-ready builds):

1. **Commit** the source, tests, and docs — not binary Play bundles.
2. Write a commit message that says **why** (user-facing change), not a file list.
3. Update [`CHANGELOG.md`](CHANGELOG.md) when you bump `versionName` / `versionCode` for a Play upload.
4. **Push** to `origin` on the working branch (`master` unless told otherwise) after the user asks to publish/sync, or when they say to push/upload git changes.
5. Prefer **one commit per shippable change** (or a small logical set). Do not leave weeks of uncommitted Play iterations only as AABs on disk.

Do **not** wait for a pile of `OrienteerRun-*.aab` files to act as version history.

## Releases / Play AABs

- Build: `./gradlew :app:bundleRelease` (see [`PLAY_PUBLISH.md`](PLAY_PUBLISH.md)).
- Copy the signed AAB to `store-assets/releases/OrienteerRun-<versionName>-vc<versionCode>.aab`.
- That folder and `*.aab` are **gitignored**. Keep **only the latest** AAB locally; delete older copies.
- Record what changed in **git + CHANGELOG.md**, not in a stack of bundles.

## Canonical project path

Build and edit from this checkout (`D:\PROJECTS\Running-app-main` / the open workspace). See [`DEVELOPMENT.md`](DEVELOPMENT.md) if a duplicate OneDrive path appears.

## Secrets

Never commit `local.properties`, keystores, Sentry auth tokens, or `orienteer-upload-KEYSTORE-BACKUP.txt`.
