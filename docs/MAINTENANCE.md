# Reapplying after upstream updates

The source checkpoint includes both native Home/Work chips and the Back cleanup. Saved-place map markers are a separate, unimplemented proposal.

## Same Maps version, newer publisher patches

1. Keep the working APK, original signing key, password, and an export of Local saved outside Git.
2. Produce a fresh upstream-patched APK signed with the same key used on the phone.
3. Inspect changes to `SavedStore`, `SavedPlaces`, their lifecycle callbacks, and Maps UI customizations. Confirm there is no upstream Home/Work feature that would duplicate this extension.
4. Run `build.py` against the fresh APK with new build/output paths. ABI checks are structural checks, not a complete compatibility audit.
5. Test the UI cases below on an emulator, then install with `adb install -r` using the same signing key. Do not uninstall the existing phone app.

## New Maps version

Port these bindings before changing the version gate in `build.py`:

| Binding | Current value / behavior |
| --- | --- |
| Main activity | `com.google.android.maps.MapsActivity` |
| Category container | `below_search_omnibox_container`, `0x7f0b0166` |
| Navigation container | `nav_container`, `0x7f0b06b9` |
| Route preview tabs | `directions_mode_tabs`, `0x7f0b0317` |
| Query view | `search_omnibox_text_box`, `0x7f0b0a5f` |
| Native clear action | `search_omnibox_text_clear`, `0x7f0b0a60` |
| Home/Work icons | `0x7f080587` / `0x7f08063a` |
| Chip text appearance | `0x7f150f99` |
| Chip class | `com.google.android.material.chip.Chip` |
| Chip drawable / minimum touch target | Chip fields `h` / `o` |
| Shape type / getter | `bviu`, drawable's inherited `ad()` |

The drawable's copied fields are surface `b`, background `c`, stroke `e`, stroke width `N`, minimum height `d`, chip padding `m/r`, icon padding `Y/Z`, text padding `n/o`, ripple `f`, icon size `R`, and icon tint `Q`. These are obfuscated implementation details; resolve each from the new APK. The current drawable class is `bvay` extending `bvin`, though the helper obtains its actual class from the live Chip.

Search the RecyclerView superclass chain: the actual carousel is a `GmmRecyclerView`. The helper reuses a bound native category chip as the style sample. Wait until it exists instead of inventing fallback cosmetics. Keep the overlay parent's elevation at zero; raising it changes Material elevation-overlay colors.

Confirm the local-saved ABI used by the stubs:

- `SavedStore.load(Context)`, static `SavedStore.home` and `work`.
- `SavedStore.Place.lat/lng` as doubles.
- `SavedPlaces.directions(Context, Place)`.
- `SavedPlaces$Front.onActivityResumed/Paused/Destroyed(Activity)` callbacks.

If a new version separates these classes into different DEX files or changes their signatures, adapt the validation/build code. The current builder supports the lifecycle class moving to a different DEX, but expects the related local-saved classes beside it.

## Behavior that must be preserved

The overlay is attached to the activity decor view because Maps may recreate its own managed UI subtrees. It reserves padding at the beginning of the category carousel, respects RTL layout, and restores the old padding/clip setting on removal. The controller runs every 250 ms only while the activity is resumed.

Back cleanup does not intercept Android Back. A task-scoped, in-memory session records the clicked destination. After route UI has appeared and then disappeared, the helper invokes native clear only if the query still matches the exact destination and is not being edited. Matching allows six-decimal display rounding. Pending launches expire after 30 seconds before directions open. Android process death discards ownership, so cleanup cannot be guaranteed after process recreation.

Do not clear arbitrary coordinate searches, repeatedly click clear, or clear while route preview/navigation is visible.

## Regression checks

- No Home/Work configured: original category row unchanged.
- Home only, Work only, and both: correct chips/destinations; updates after saved labels change.
- Native colors, fonts, touch feedback and padding; category scrolling in portrait/landscape.
- Chips disappear on incompatible screens and return to the main map.
- Shortcut -> directions -> edge Back: clear query and return to main map.
- Manually search the same coordinates -> directions -> Back: preserve the ordinary selection.
- Active navigation remains active; Back returns normally through route preview.
- Editing a different query is never cleared; keyboard and map gestures still work.
- Background/resume and activity recreation do not duplicate overlays or leave carousel padding behind.
- Signing certificate matches the installed app; in-place update preserves Local saved.

After porting, record the new supported versions, fresh APK/source hashes, precise runtime checks, and remaining limitations. Keep the previous working tag available.
