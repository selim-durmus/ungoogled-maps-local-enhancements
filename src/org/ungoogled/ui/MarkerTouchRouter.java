package org.ungoogled.ui;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/** Observe complete window gestures before Android can split pointers between siblings. */
final class MarkerTouchRouter {
    private final Window window;
    private final ViewGroup layer;
    private final int slop;
    private final int[] offset = new int[2];
    private Window.Callback original, callback;
    private MotionEvent down;
    private View target;
    private boolean closed;

    MarkerTouchRouter(Window window, ViewGroup layer) {
        this.window = window;
        this.layer = layer;
        slop = ViewConfiguration.get(layer.getContext()).getScaledTouchSlop();
    }

    void install() {
        if (callback != null || closed) return;
        original = window.getCallback();
        callback = (Window.Callback) Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),
            new Class<?>[]{Window.Callback.class}, (proxy, method, args) -> {
                if (method.getName().equals("dispatchTouchEvent")) return dispatch((MotionEvent) args[0]);
                try { return method.invoke(original, args); }
                catch (InvocationTargetException e) { throw e.getCause(); }
            });
        window.setCallback(callback);
    }

    private View hit(MotionEvent e) {
        if (!layer.isShown()) return null;
        layer.getLocationInWindow(offset);
        float x = e.getX() - offset[0], y = e.getY() - offset[1];
        for (int i = layer.getChildCount() - 1; i >= 0; i--) {
            View child = layer.getChildAt(i);
            if (child.isShown() && child.isEnabled() && child.isClickable()
                && x >= child.getX() && x < child.getX() + child.getWidth()
                && y >= child.getY() && y < child.getY() + child.getHeight()) return child;
        }
        return null;
    }

    private boolean moved(MotionEvent e) {
        if (Math.hypot(e.getX() - down.getX(), e.getY() - down.getY()) > slop) return true;
        for (int i = 0; i < e.getHistorySize(); i++)
            if (Math.hypot(e.getHistoricalX(i) - down.getX(), e.getHistoricalY(i) - down.getY()) > slop) return true;
        return false;
    }

    private boolean dispatch(MotionEvent e) {
        if (closed) return original.dispatchTouchEvent(e);
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            release();
            target = hit(e);
            if (target != null) {
                down = MotionEvent.obtain(e);
                target.setPressed(true);
                return true;
            }
        }
        if (down == null) return original.dispatchTouchEvent(e);
        if (action == MotionEvent.ACTION_CANCEL) { release(); return true; }
        if (e.getPointerCount() > 1 || moved(e) || !target.isShown() || target.getParent() != layer) {
            // The map receives the original DOWN followed by the actual triggering event,
            // including POINTER_DOWN. All later events follow the normal window path.
            // Never re-enter the decor's dispatch while it is iterating touch targets.
            MotionEvent start = down;
            down = null;
            release();
            try { original.dispatchTouchEvent(start); }
            finally { start.recycle(); }
            original.dispatchTouchEvent(e);
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            View clicked = target;
            release();
            clicked.performClick();
        }
        return true;
    }

    private void release() {
        if (target != null) { target.setPressed(false); target = null; }
        if (down != null) { down.recycle(); down = null; }
    }

    void close() {
        closed = true;
        release();
        // Do not overwrite a callback installed by Maps after ours.
        if (callback != null && window.getCallback() == callback) window.setCallback(original);
    }
}
