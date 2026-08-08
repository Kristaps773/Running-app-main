# Development — canonical project location

## Use this folder only

**Active project (build, edit, commit):**

```
C:\Users\krist\OneDrive\DatorsK\FAILI\PROGRAMMING\Running-app-main
```

- Open this path in **Android Studio** for builds and device installs.
- Apply **all code changes** here unless you explicitly migrate to a single repo elsewhere.
- Debug APK: `app\build\outputs\apk\debug\app-debug.apk`

## Do not edit the duplicate copy

Another checkout exists at:

```
C:\Users\krist\OneDrive\DatorsK\FAILI\Projekti\Running-app-main
```

That copy is **not** the app you install from Android Studio. It can drift (different UI experiments, partial fixes). Changes made there will **not** appear in your phone build until they are ported into **PROGRAMMING**.

| Folder | Role |
|--------|------|
| `FAILI\PROGRAMMING\Running-app-main` | **Canonical** — ship from here |
| `FAILI\Projekti\Running-app-main` | Stale/duplicate — avoid editing |

## Cursor / AI assistants

When using Cursor or other agents:

1. Set the workspace root to `PROGRAMMING\Running-app-main`, **or**
2. Tell the agent: *"The project folder is `C:\Users\krist\OneDrive\DatorsK\FAILI\PROGRAMMING\Running-app-main`."*

If the workspace is opened on `Projekti\Running-app-main`, verify every change lands under **PROGRAMMING** before rebuilding.

## Quick verify before a device test

In Android Studio: **File → Open Recent** (or project title bar) should show `PROGRAMMING\Running-app-main`, not `Projekti\Running-app-main`.
