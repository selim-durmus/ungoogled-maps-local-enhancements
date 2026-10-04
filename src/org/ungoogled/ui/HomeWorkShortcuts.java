package org.ungoogled.ui;

import android.app.Activity;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.WeakHashMap;

/** Local destinations alongside Maps' existing category carousel. No data writes or network calls. */
public final class HomeWorkShortcuts {
    private static final WeakHashMap<Activity, Controller> active = new WeakHashMap<>();
    private static final String MAPS = "com.google.android.maps.MapsActivity";
    private static RouteSession shortcutRoute;
    private HomeWorkShortcuts() {}

    public static void resume(Activity activity) {
        if (!MAPS.equals(activity.getClass().getName())) return;
        pause(activity);
        try {
            SavedStore.load(activity);
            Controller c = new Controller(activity);
            active.put(activity, c);
            c.root.post(c);
        } catch (Throwable error) {
            android.util.Log.w("UA-HomeWork", "Could not attach shortcuts", error);
        }
    }

    public static void pause(Activity activity) {
        Controller old = active.remove(activity);
        if (old != null) old.close();
    }

    /** In-memory ownership of the temporary pin created by one shortcut. Never persisted. */
    static final class RouteSession {
        private static final java.util.regex.Pattern COORDINATES = java.util.regex.Pattern.compile(
                "^\\s*([+-]?\\d{1,3}(?:\\.\\d+)?)\\s*,\\s*([+-]?\\d{1,3}(?:\\.\\d+)?)\\s*$");
        final int taskId;
        final double lat, lng;
        final long launchedAt;
        boolean directionsOpened;

        RouteSession(int taskId, double lat, double lng, long launchedAt) {
            this.taskId = taskId; this.lat = lat; this.lng = lng; this.launchedAt = launchedAt;
        }

        boolean matches(CharSequence text) {
            if (text == null) return false;
            java.util.regex.Matcher m = COORDINATES.matcher(text);
            if (!m.matches()) return false;
            try {
                // The deep link uses seven decimals; Maps displays its pin with six.
                return Math.abs(Double.parseDouble(m.group(1)) - lat) <= 0.00000055
                        && Math.abs(Double.parseDouble(m.group(2)) - lng) <= 0.00000055;
            } catch (NumberFormatException ignored) { return false; }
        }
    }

    private static void openShortcut(Activity activity, SavedStore.Place destination) {
        shortcutRoute = new RouteSession(activity.getTaskId(), destination.lat, destination.lng,
                android.os.SystemClock.elapsedRealtime());
        SavedPlaces.directions(activity, destination);
    }

    private static boolean shown(View view) { return view != null && view.isShown(); }

    private static boolean matchesQuery(View view, RouteSession route) {
        if (view == null) return false;
        if (route.matches(view.getContentDescription())) return true;
        if (view instanceof TextView && route.matches(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                if (matchesQuery(group.getChildAt(i), route)) return true;
        }
        return false;
    }

    private static final class Controller implements Runnable {
        final Activity activity;
        final ViewGroup root;
        final LinearLayout chips;
        ViewGroup carousel;
        int left, top, right, bottom, reserved;
        boolean clip, closed;
        SavedStore.Place lastHome, lastWork;
        int lastInk, lastHeight;
        final int[] origin = new int[2], anchor = new int[2];

        Controller(Activity activity) {
            this.activity = activity;
            root = (ViewGroup) activity.getWindow().getDecorView();
            chips = new LinearLayout(activity);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setGravity(Gravity.CENTER_VERTICAL);
            chips.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            chips.setVisibility(View.GONE);
            root.addView(chips, new FrameLayout.LayoutParams(1, 1));
        }

        int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

        public void run() {
            if (closed) return;
            try { update(); }
            catch (Throwable error) {
                android.util.Log.w("UA-HomeWork", "Shortcuts disabled for this screen", error);
                close();
                return;
            }
            // Only alive while MapsActivity is resumed. No background service or per-frame traversal.
            root.postDelayed(this, 250);
        }

        void update() throws Exception {
            updateShortcutReturn();
            View container = root.findViewById(0x7f0b0166); // below_search_omnibox_container, supported APK
            View nav = root.findViewById(0x7f0b06b9);
            Rect visible = new Rect();
            boolean front = activity.hasWindowFocus() && container != null && container.isShown()
                    && container.getGlobalVisibleRect(visible) && visible.height() >= dp(32)
                    && (nav == null || !nav.isShown());
            ViewGroup found = front ? findCarousel(container) : null;
            SavedStore.Place home = SavedStore.home, work = SavedStore.work;
            if (found == null || (home == null && work == null)) {
                chips.setVisibility(View.GONE);
                releaseCarousel();
                return;
            }
            if (carousel != found) {
                releaseCarousel();
                carousel = found;
                left = found.getPaddingLeft(); top = found.getPaddingTop();
                right = found.getPaddingRight(); bottom = found.getPaddingBottom();
                clip = found.getClipToPadding();
                lastHeight = -1;
            }
            int height = carousel.getHeight();
            if (height < dp(32) || carousel.getWidth() < dp(220)) {
                chips.setVisibility(View.GONE);
                releaseCarousel();
                return;
            }
            TextView sample = findText(carousel);
            // Wait for the real category chip to finish binding before constructing ours.
            if (sample == null) { chips.setVisibility(View.GONE); return; }
            int ink = sample.getCurrentTextColor();
            if (home != lastHome || work != lastWork || ink != lastInk || height != lastHeight) {
                chips.removeAllViews();
                if (home != null) addChip("Home", home, false, ink, height, sample);
                if (work != null) addChip("Work", work, true, ink, height, sample);
                chips.measure(View.MeasureSpec.makeMeasureSpec(carousel.getWidth(), View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                reserved = chips.getMeasuredWidth();
                lastHome = home; lastWork = work; lastInk = ink; lastHeight = height;
            }
            boolean rtl = carousel.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            int wantLeft = left + (rtl ? 0 : reserved), wantRight = right + (rtl ? reserved : 0);
            if (carousel.getPaddingLeft() != wantLeft || carousel.getPaddingRight() != wantRight) {
                carousel.setPadding(wantLeft, top, wantRight, bottom);
                final ViewGroup adjusted = carousel;
                // RecyclerView otherwise retains its old first-item anchor beneath the new padding.
                adjusted.post(() -> {
                    if (!closed && carousel == adjusted) adjusted.scrollBy(rtl ? 100000 : -100000, 0);
                });
            }
            if (!carousel.getClipToPadding()) carousel.setClipToPadding(true);
            root.getLocationOnScreen(origin);
            carousel.getLocationOnScreen(anchor);
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) chips.getLayoutParams();
            int x = anchor[0] - origin[0] + (rtl ? carousel.getWidth() - right - reserved : left);
            int y = anchor[1] - origin[1];
            if (p.width != reserved || p.height != height || p.leftMargin != x || p.topMargin != y) {
                p.gravity = Gravity.TOP | Gravity.LEFT;
                p.width = reserved; p.height = height; p.leftMargin = x; p.topMargin = y;
                chips.setLayoutParams(p);
            }
            chips.setLayoutDirection(carousel.getLayoutDirection());
            chips.setVisibility(View.VISIBLE);
        }

        void addChip(String label, SavedStore.Place destination, boolean work, int ink, int height, TextView sample) throws Exception {
            // Use Maps' own Material Chip and live style values, not a copied background bitmap
            // or a handmade rounded rectangle. Obfuscated field names are pinned to 26.36.04.
            Class<?> chipClass = Class.forName("com.google.android.material.chip.Chip");
            if (!chipClass.isInstance(sample)) throw new IllegalStateException("Unsupported category chip");
            TextView button = (TextView) chipClass.getConstructor(android.content.Context.class).newInstance(sample.getContext());
            Object source = field(sample, "h");
            // Surface color is separate from the foreground fill in Material ChipDrawable.
            java.lang.reflect.Field surface = source.getClass().getDeclaredField("b");
            surface.setAccessible(true);
            surface.set(field(button, "h"), surface.get(source));
            String[][] properties = {
                {"setChipBackgroundColor", "c"}, {"setChipStrokeColor", "e"},
                {"setChipStrokeWidth", "N"}, {"setChipMinHeight", "d"},
                {"setChipStartPadding", "m"}, {"setChipEndPadding", "r"},
                {"setIconStartPadding", "Y"}, {"setIconEndPadding", "Z"},
                {"setTextStartPadding", "n"}, {"setTextEndPadding", "o"},
                {"setRippleColor", "f"}, {"setChipIconSize", "R"}, {"setChipIconTint", "Q"}
            };
            for (String[] property : properties) {
                java.lang.reflect.Field f = source.getClass().getDeclaredField(property[1]);
                f.setAccessible(true);
                chipClass.getMethod(property[0], f.getType()).invoke(button, f.get(source));
            }
            Object shape = source.getClass().getMethod("ad").invoke(source);
            chipClass.getMethod("setShapeAppearanceModel", Class.forName("bviu")).invoke(button, shape);
            chipClass.getMethod("setEnsureMinTouchTargetSize", boolean.class).invoke(button, field(sample,"o"));
            chipClass.getMethod("setCheckable", boolean.class).invoke(button, false);
            chipClass.getMethod("setCloseIconVisible", boolean.class).invoke(button, false);
            chipClass.getMethod("setChipIconResource", int.class).invoke(button, work ? 0x7f08063a : 0x7f080587);
            chipClass.getMethod("setChipIconVisible", boolean.class).invoke(button, true);
            button.setTextAppearance(0x7f150f99); // gmm_chip_text_appearance
            button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sample.getTextSize());
            button.setTypeface(sample.getTypeface());
            button.setTextColor(sample.getTextColors());
            button.setLetterSpacing(sample.getLetterSpacing());
            button.setIncludeFontPadding(sample.getIncludeFontPadding());
            button.setGravity(sample.getGravity());
            button.setFontFeatureSettings(sample.getFontFeatureSettings());
            String variation = sample.getFontVariationSettings();
            if (variation != null) button.setFontVariationSettings(variation);
            button.setElevation(sample.getElevation());
            button.setText(label);
            button.setContentDescription("Directions to " + label);
            button.setClickable(true);
            button.setFocusable(true);
            button.setOnClickListener(v -> openShortcut(activity, destination));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height);
            int gap = dp(6);
            if (carousel.getChildCount() >= 2) {
                int measured = carousel.getChildAt(1).getLeft() - carousel.getChildAt(0).getRight();
                if (measured > 0 && measured < dp(24)) gap = measured;
            }
            p.setMarginEnd(gap);
            chips.addView(button, p);
        }

        void updateShortcutReturn() {
            RouteSession route = shortcutRoute;
            if (route == null || route.taskId != activity.getTaskId() || !activity.hasWindowFocus()) return;
            // Both route preview and active guidance own their UI. Never clear during either.
            if (shown(root.findViewById(0x7f0b0317)) || shown(root.findViewById(0x7f0b06b9))) {
                route.directionsOpened = true;
                return;
            }
            if (!route.directionsOpened) {
                // A failed/deferred launch must not adopt a later, unrelated directions session.
                if (android.os.SystemClock.elapsedRealtime() - route.launchedAt > 30000)
                    shortcutRoute = null;
                return;
            }
            View query = root.findViewById(0x7f0b0a5f); // search_omnibox_text_box
            if (!shown(query)) return;
            if (query.hasFocus()) { shortcutRoute = null; return; } // User is editing/searching.
            View clear = root.findViewById(0x7f0b0a60); // Maps' native clear-selection action
            if (matchesQuery(query, route)) {
                if (shown(clear) && clear.isEnabled()) {
                    // Consume before invoking native UI code, so this happens at most once.
                    shortcutRoute = null;
                    clear.performClick();
                }
            } else {
                // Main map or a different query: this shortcut no longer owns the selection.
                shortcutRoute = null;
            }
        }

        void releaseCarousel() {
            if (carousel != null) {
                carousel.setPadding(left, top, right, bottom);
                carousel.setClipToPadding(clip);
                carousel = null;
            }
        }

        void close() {
            if (closed) return;
            closed = true;
            root.removeCallbacks(this);
            releaseCarousel();
            root.removeView(chips);
        }
    }

    private static Object field(Object object, String name) throws Exception {
        java.lang.reflect.Field f = object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(object);
    }

    private static ViewGroup findCarousel(View view) {
        for (Class<?> type = view.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals("android.support.v7.widget.RecyclerView")) return (ViewGroup) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ViewGroup found = findCarousel(group.getChildAt(i));
                if (found != null && found.isShown()) return found;
            }
        }
        return null;
    }

    private static TextView findText(View view) {
        if (view instanceof TextView) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

}
