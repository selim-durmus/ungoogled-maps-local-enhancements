# Tutto Enhancements for Ungoogled Maps

A local extension for [bearinmindcat/morphe-patches](https://github.com/bearinmindcat/morphe-patches) containing native Home/Work shortcuts, saved-place map markers, and local labels connected to search and map captions. The shortcuts clear their temporary coordinate selection on return; the markers support taps, grouping, smooth movement, natural edge clipping, and icons styled for dark maps.

This repository is also a **Morphe patch source named Tutto Enhancements**. Its MPP adds our features alongside bearinmind's patches in one patching run. The repository preserves both compiled bundles, upstream source, our source, and an offline PC build process.

## Use in Morphe Manager

[Add Tutto Enhancements to Morphe](https://morphe.software/add-source?github=selim-durmus/ungoogled-maps-local-enhancements), or add this repository URL under Sources. Keep bearinmind's source too.

1. In Expert mode select the original Google Maps **26.36.04.973607363** APK/APKM.
2. Select bearinmind's normal Ungoogled Maps patches, including **Offline saved places**, **Change package name**, and **Hide ads and clutter**. Leave **Add microG support** off.
3. Under **Tutto Enhancements**, select **Home, Work, markers and labels**.
4. Patch once. Use the same signing key and package name as your installed app to update without uninstalling.

Tested combination: bearinmind **1.7.4**, Tutto **1.3.0**, Morphe Manager **1.34.0** / Desktop **1.18.1**. One checkbox includes all our features because markers and labels share their controller. The patch validates required upstream code after all patch execution blocks have run; tab order does not establish dependencies. Missing Local saved support and the microG variant fail with a clear message. Future upstream releases still need compatibility review; successful structural checks alone cannot guarantee runtime behavior.

Latest Tutto release: **1.3.1**, fixing marker touch interception during rotation, pinch and dragging. Refresh this source in Morphe to obtain it. The runtime touch fix passed 15 Android emulator regression checks; the full Maps/Manager build combination above was verified with 1.3.0. See [1.3.1 verification](docs/touch-verification-1.3.1.md) for the test scope.

The patch source contains no signing key, Google APK, or saved places. A fresh Morphe installation may have a different key; do not uninstall your current Maps app to bypass a signing conflict. See [Morphe source and development](docs/MORPHE-SOURCE.md).

## Rebuild everything

Clone this repository and extract your encrypted private recovery archive. The archive supplies the exact stock APK, signing material, and portable Windows build tools; those private inputs are deliberately outside Git.

Run from the cloned repository in PowerShell:

    .\build-all.ps1 -RecoveryRoot 'D:\Maps Recovery\recovery-kit'

The script verifies pinned input/tool hashes, applies bearinmind 1.7.4's 21 default patches plus Tutto Enhancements, signs the result, and prints its location under dist. Ad hiding is included. No system-wide Java, Python, Android SDK or Morphe Manager installation is needed. No files are downloaded and no phone is modified. Changed feature source requires rebuilding the Tutto MPP before this command will proceed.

To use the legacy v1.3.0 upstream baseline instead (without the new upstream fixes/clutter controls):

    .\build-all.ps1 -RecoveryRoot 'D:\Maps Recovery\recovery-kit' -From baseline

To test with Java network access denied:

    .\build-all.ps1 -RecoveryRoot 'D:\Maps Recovery\recovery-kit' -VerifyOffline

See [Recovery and Morphe workflow](docs/RECOVERY.md) for archive contents, fresh-machine recovery, version limitations and the distinction between building an APK and recompiling upstream patches.

## Features

- Reads Home/Work from the existing Local saved store. Only configured destinations appear.
- Uses Maps' actual Material Chip widget, live category styling, and bundled Home/Work icons.
- Keeps the category carousel scrollable beside the shortcuts.
- Uses the existing local-saved directions action.
- On Back from a shortcut route, dismisses only the matching temporary coordinate selection using Maps' native clear action.
- Preserves ordinary search and active navigation; adds no network client.
- Shows up to three matching local labels above native search suggestions: exact matches first, then prefixes and substrings. Matching ignores case and accents; refine the query to narrow a larger set. If a landscape keyboard leaves too little room, dismiss it to see local results.
- Draws labels beside individual markers when space permits, and uses labels in marker selection. Label-only places appear too.
- Connects the native Add label button, overflow action and Local saved editor to the same local label store. Supports add, rename and remove without sign-in, and prevents assigning the same normalized label to different places.

Labels use the publisher's existing storage and export format. No migration is required; existing labels become searchable. Removing a label preserves the saved place and its list memberships. Home/Work remain reserved labels with their existing destination behavior. Label entry is local, but the native search field still runs Google's ordinary autocomplete: text typed there may be sent to Google. This extension does not make Maps search offline or private.

Captions avoid our other markers/captions and native controls, but cannot detect Google's road/POI text or building occlusion. Crowded markers group; zoom in to see individual captions. A place with several labels displays one caption, while all aliases remain searchable. The place sheet retains Google's title and may still call its action “Add label”; the local dialog opens the existing label for editing.

The marker extension reads Local saved, draws Home/Work and saved-list icons using Maps' projection, and opens a place when tapped. Its 24 dp circles use a soft palette, dark glyphs, and a slate rim tuned for dark maps; tap targets remain 44 dp. Nearby points form a numbered marker with a place chooser. Partially visible markers are clipped naturally at the screen sides instead of disappearing when their first edge crosses the viewport. Markers follow pan, zoom, rotation and tilt, and refresh after saves change. Position updates run on Android display frames rather than a 33 ms timer; saved-store refresh remains once per second. This improves motion alignment but remains a separate overlay, so exact compositor synchronization is not guaranteed. They are shown on the main browse map and hidden on route/navigation and place-detail screens.

The `v1.0.0` tag preserves the original Home/Work-only checkpoint. Markers and labels do not change `HomeWorkShortcuts.java`. No ETA labels are included. The local layer does not reproduce Google's native label collision, building occlusion, or account-backed saved layer.

## Supported baseline

| Component | Tested value |
| --- | --- |
| Maps | `26.36.04.973607363` / version code `1068763346` |
| Package | `org.ungoogled.android.apps.maps` |
| Upstream patches | v1.7.4 for stock builds; v1.3.0 for the legacy baseline path |
| Java | JDK 17; helper targets Java 8 |
| Python | 3.11+; standard library only |
| Android SDK | Platform 36, Build Tools 36.0.0 |
| Apktool | 3.0.3 |

**A newer Maps version requires porting and testing.** The code uses version-specific resource IDs and obfuscated Chip internals. The build rejects a different Maps version; do not simply remove that check. Even changes to upstream patches on the same Maps version require review and a device smoke test. See [maintenance instructions](docs/MAINTENANCE.md).

## Build

The instructions below describe the lower-level enhancement-only builder. For a complete stock-to-final APK build, use the command above.

Install Python, a JDK with `java` and `javac` on PATH, the Android SDK components above, and the Apktool JAR. Obtain the original Maps APK and patch it with the publisher's Morphe patches, including local saved places and the package name above. Use that fresh output as input; do not feed this extension's own output back into the tool.

Keep the original signing key and its password outside Git. To install over an existing copy while preserving app data, the result must use that copy's signing key. The build verifies that the output and input certificates match. Signing keys are not recoverable from an APK or this repository.

PowerShell example, from the repository root (replace placeholder paths):

```powershell
python .\build.py `
  --input 'C:\path\ungoogled-maps-upstream.apk' `
  --apktool 'C:\tools\apktool_3.0.3.jar' `
  --sdk 'C:\path\Android\Sdk' `
  --keystore 'C:\private\maps-signing.p12' `
  --password-file 'C:\private\signing-password.txt' `
  --key-alias morphe
```

The password file contains the keystore password. If the key password differs, add `--key-password-file` with a separate file. Paths are examples; no key or password is bundled. `--sdk` can instead come from `ANDROID_SDK_ROOT` or `ANDROID_HOME`.

The tool:

1. Checks package/version, the local-saved class signatures, and absence of this extension.
2. Decodes code while keeping resources raw, hooks the three lifecycle callbacks, and redirects the three label editor entry points.
3. Compiles the helpers and runs twelve coordinate-ownership checks, marker grouping checks and label ranking/normalization checks. Compile-time stubs and tests are excluded from the helper DEX.
4. Rebuilds the affected original classes' DEX files (classes.dex and classes7.dex on this baseline), copies those into the original APK, and appends the helper as the next DEX.
5. Verifies all other original APK entry contents are preserved, aligns for 16 KB native-library pages, signs, checks the certificate, and writes an APK and verification report under `dist/`.

Build products stay in `build/`; the script refuses to overwrite an existing work directory or output. For another build, use fresh paths:

```powershell
python .\build.py <same arguments> --work-dir .\build-next --output .\dist\maps-next.apk
```

Keep custom build directories outside Git or add them to `.gitignore` before building. The default `build/` and `dist/` are already ignored. There are no automatic downloads or device installation steps.

## Install and verify

Back up Local saved using its export feature and retain the previous working APK before replacing it. Keep a separate secure backup of the original signing key and password; they are intentionally not in this repository.

After checking the APK on an emulator, install an in-place update:

```powershell
adb -s YOUR_DEVICE_SERIAL install --no-incremental -r .\dist\ungoogled-maps-local-markers.apk
```

Do not uninstall or clear app data as part of this process. If Android reports a signature mismatch, resolve the signing key instead.

Check Home/Work appearance, both destinations, category scrolling, Back cleanup, an ordinary search followed by Directions/Back, and active navigation. Use `adb logcat -s UA-HomeWork` to inspect extension errors. The existing twelve Java checks verify coordinate matching only; they do not replace UI tests.

## Preserved working checkpoint

- Helper source SHA-256: `fcc62f1c8f47ca46eedbd9850348cfabee2a57c1eddd46e9081dba2a0e34480e` (original file bytes before Git line-ending normalization).
- Installed working APK SHA-256: `62dbb34ae8b7e6f84b7fe87d9bbec2ca6517a42748c68de0974a5c9c43af4490`.
- [Installed-build verification](docs/installed-build-verification.json) records the earlier emulator and Pixel checks. These are historical observations for that exact build, not a guarantee for a newly patched APK.
- [Portable rebuild verification](docs/rebuild-verification.json) records a successful build with this repository's tool. Every ZIP entry in that rebuilt APK was byte-identical to the installed checkpoint, including `classes.dex` and `classes12.dex`; the overall APK hash differs because of container metadata. The phone was not reinstalled during this preservation step.
- The original lifecycle change is recorded in [lifecycle-hooks.diff](patches/lifecycle-hooks.diff).

APK bytes may differ across rebuilds because packaging/signing metadata can differ. The preserved Java implementation is the working version; rebuilding does not modify the installed app until you install the result yourself.

## Repository contents and license

`src/` contains the extension, `stubs/` contains compile-time signatures only, `tests/` contains coordinate-ownership, marker geometry and label matching checks, and `build.py` performs the post-patch build. APKs, decompiled Maps classes, Google resources, signing material, private places, and device screenshots are excluded.

GPL-3.0; see [LICENSE](LICENSE). The extension depends on local-saved functionality from bearinmindcat's GPL-licensed Morphe patches. This project is independent of Google and the upstream publisher and is not an endorsement by either.
