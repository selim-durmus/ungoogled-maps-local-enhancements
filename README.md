# Tutto Enhancements for Ungoogled Maps

A local extension for [bearinmindcat/morphe-patches](https://github.com/bearinmindcat/morphe-patches) that adds native Home and Work shortcut chips beside Maps' category carousel. A shortcut opens directions to the destination saved in Local saved and, on Back, clears its temporary coordinate selection.

This repository is also a **Morphe patch source named Tutto Enhancements**. Its MPP adds the shortcuts alongside bearinmind's patches in one patching run. The repository preserves both compiled bundles, upstream source, our source, and an offline PC build process.

## What moved to bearinmind's patch (1.4.0)

bearinmind 2.5.x's **Offline saved places** now covers what our 1.3.x markers and labels did (upstream issue #36), so using both drew every place and label twice. Tutto 1.4.0 keeps only the Home/Work shortcuts. Upstream now provides:

- Home, Work, your labels and every saved list on Maps' own map, drawn by Maps' personal-place layer with each list's icon: a heart for Favorites, a flag for Want to go, a suitcase for Travel plans, **a star for Starred places**, a pin for other lists. Each list has a Hide on map / Show on map switch in Local saved.
- Typing a label (Home, Work or your own) in search and submitting it opens that place; directions to a label work too.
- Maps' Add label prompt and Local saved's label editor store labels on the phone.

Two 1.3.x behaviours have no upstream equivalent: up to three matching local labels shown *as you type* (ours matched prefixes and ignored accents; upstream matches the whole label when submitted), and our numbered grouping of nearby markers (Maps' own layer handles overlaps now). Use tag `v1.3.1` with bearinmind 1.7.4 if you need those.

## Use in Morphe Manager

[Add Tutto Enhancements to Morphe](https://morphe.software/add-source?github=selim-durmus/ungoogled-maps-local-enhancements), or add this repository URL under Sources. Keep bearinmind's source too.

1. In Expert mode select the original Google Maps **26.36.04.973607363** APK/APKM.
2. Select bearinmind's normal Ungoogled Maps patches, including **Offline saved places**, **Change package name**, and **Hide ads and clutter**. Leave **Add microG support** off.
3. Under **Tutto Enhancements**, select **Home and Work shortcuts**. (1.3.x called it "Home, Work, markers and labels".)
4. Patch once. Use the same signing key and package name as your installed app to update without uninstalling.

Built for bearinmind **2.5.5** and Morphe Manager **1.34.0** / Desktop **1.18.1**. The patch validates the upstream code it needs after all patch execution blocks have run; tab order does not establish dependencies. Missing Local saved support and the microG variant fail with a clear message. The 1.4.0 MPP was compiled and its references were checked against bearinmind 2.5.5's extension, but a full patch-and-device run is still to be done. Future upstream releases still need compatibility review.

The patch source contains no signing key, Google APK, or saved places. A fresh Morphe installation may have a different key; do not uninstall your current Maps app to bypass a signing conflict. See [Morphe source and development](docs/MORPHE-SOURCE.md).

## Rebuild everything

Clone this repository and extract your encrypted private recovery archive. The archive supplies the exact stock APK, signing material, and portable Windows build tools; those private inputs are deliberately outside Git.

Run from the cloned repository in PowerShell:

    .uild-all.ps1 -RecoveryRoot 'D:\Maps Recoveryecovery-kit'

The script verifies pinned input/tool hashes, applies bearinmind 2.5.5's default Maps patches plus Tutto Enhancements, signs the result, and prints its location under dist. Ad hiding is included. No system-wide Java, Python, Android SDK or Morphe Manager installation is needed. No files are downloaded and no phone is modified. Changed feature source requires rebuilding the Tutto MPP before this command will proceed.

The legacy upstream v1.3.0 baseline path (`-From baseline`) needs our own markers and labels, so it now runs only from a checkout of tag `v1.3.1`.

To test with Java network access denied:

    .uild-all.ps1 -RecoveryRoot 'D:\Maps Recoveryecovery-kit' -VerifyOffline

See [Recovery and Morphe workflow](docs/RECOVERY.md) for archive contents, fresh-machine recovery, version limitations and the distinction between building an APK and recompiling upstream patches.

## Features

- Reads Home/Work from the existing Local saved store. Only configured destinations appear.
- Uses Maps' actual Material Chip widget, live category styling, and bundled Home/Work icons.
- Keeps the category carousel scrollable beside the shortcuts.
- Uses the existing local-saved directions action.
- On Back from a shortcut route, dismisses only the matching temporary coordinate selection using Maps' native clear action.
- Preserves ordinary search and active navigation; adds no network client.

The `v1.0.0` tag preserves the original Home/Work-only checkpoint; 1.4.0 ships the same `HomeWorkShortcuts.java`. Tags `v1.1.0` to `v1.3.1` hold the markers and labels that bearinmind's patch now replaces.

## Supported baseline

| Component | Tested value |
| --- | --- |
| Maps | `26.36.04.973607363` / version code `1068763346` |
| Package | `org.ungoogled.android.apps.maps` |
| Upstream patches | v2.5.5 for stock builds; v1.3.0 for the legacy baseline path (tag v1.3.1) |
| Java | JDK 17; helper targets Java 8 |
| Python | 3.11+; standard library only |
| Android SDK | Platform 36, Build Tools 36.0.0 |
| Apktool | 3.0.3 |

**A newer Maps version requires porting and testing.** The code uses version-specific resource IDs and obfuscated Chip internals. The build rejects a different Maps version; do not simply remove that check. Even changes to upstream patches on the same Maps version require review and a device smoke test. See [maintenance instructions](docs/MAINTENANCE.md).

## Legacy build (tag v1.3.1)

The instructions below describe the lower-level enhancement-only builder. It adds our own markers and labels, so run it from a checkout of tag `v1.3.1`; on 1.4.0 it stops with that message. For a complete stock-to-final APK build, use the command above.

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

`src/` contains the extension, `stubs/` contains compile-time signatures only, `tests/` contains the coordinate-ownership checks, and `build.py` holds shared build helpers plus the legacy post-patch builder. APKs, decompiled Maps classes, Google resources, signing material, private places, and device screenshots are excluded.

GPL-3.0; see [LICENSE](LICENSE). The extension depends on local-saved functionality from bearinmindcat's GPL-licensed Morphe patches. This project is independent of Google and the upstream publisher and is not an endorsement by either.
