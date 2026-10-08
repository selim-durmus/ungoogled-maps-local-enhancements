package org.ungoogled.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Runs production routing against real Android window/view dispatch, without Maps or user data. */
public final class MarkerTouchActivity extends Activity {
    FrameLayout root, layer;
    View marker;
    MarkerTouchRouter router;
    final List<String> seen = new ArrayList<>(), sent = new ArrayList<>();
    final StringBuilder report = new StringBuilder();
    int clicks, checks;
    long time;
    float mx, my;

    public void onCreate(Bundle state) {
        super.onCreate(state);
        View map = new View(this) {
            public boolean onTouchEvent(MotionEvent e) {
                int[] xy=new int[2];getLocationInWindow(xy);
                MotionEvent copy=MotionEvent.obtain(e);copy.offsetLocation(xy[0],xy[1]);
                seen.add(describe(copy));copy.recycle();return true;
            }
        };
        setContentView(map);
        root = (FrameLayout) getWindow().getDecorView();
        root.post(() -> {
            try {
                setup(true);
                root.post(() -> {
                    try {
                        reset(); rotation(true);
                        boolean firstFails = !seen.equals(sent);
                        reset(); rotation(false);
                        boolean secondFails = !seen.equals(sent);
                        check(firstFails || secondFails, "legacy regression reproduced");
                        report.append("Legacy marker-first failure: ").append(firstFails)
                            .append(", marker-second failure: ").append(secondFails).append('\n');
                        root.removeView(layer);
                        setup(false);
                        root.post(this::verify);
                    } catch (Throwable t) { finishReport(t); }
                });
            } catch (Throwable t) { finishReport(t); }
        });
    }

    void setup(boolean legacy) {
        layer = legacy ? new LegacyLayer() : new FrameLayout(this) {
            public boolean dispatchTouchEvent(MotionEvent e) { return false; }
        };
        marker = new View(this);
        marker.setClickable(true);
        marker.setFocusable(true);
        marker.setOnClickListener(v -> clicks++);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(100,100);
        p.leftMargin = 80; p.topMargin = 300;
        layer.addView(marker,p);
        root.addView(layer,new FrameLayout.LayoutParams(-1,-1));
        if (!legacy) { router = new MarkerTouchRouter(getWindow(),layer); router.install(); }
    }

    void reset() {
        seen.clear(); sent.clear(); clicks = 0;
        time = SystemClock.uptimeMillis();
        int[] xy = new int[2]; marker.getLocationInWindow(xy);
        mx = xy[0] + 50; my = xy[1] + 50;
    }

    void rotation(boolean markerFirst) {
        float x = markerFirst ? mx : mx + 250;
        float second = markerFirst ? mx + 250 : mx;
        send(0,x,my);
        send(5 | (1<<8),x,my,second,my);
        send(2,x+30,my-50,second-30,my+50);
        send(2,x+60,my-90,second-60,my+90);
        send(6 | (1<<8),x+60,my-90,second-60,my+90);
        send(1,x+60,my-90);
    }

    void verify() {
        try {
            reset(); rotation(true); check(seen.equals(sent) && clicks==0,"rotation: marker first, exact full stream");
            reset(); rotation(false); check(seen.equals(sent) && clicks==0,"rotation: marker second, exact full stream");
            reset(); send(0,mx,my); send(5|(1<<8),mx,my,mx+10,my+10);
            send(2,mx-80,my-80,mx+150,my+150); send(6|(1<<8),mx-80,my-80,mx+150,my+150); send(1,mx-80,my-80);
            check(seen.equals(sent) && clicks==0,"pinch: both fingers on marker initially");
            reset(); send(0,mx,my); send(2,mx+150,my); send(1,mx+200,my);
            check(seen.equals(sent) && clicks==0,"one finger drag from marker");
            reset(); send(0,mx,my); send(2,mx+1,my); send(1,mx+1,my);
            check(seen.isEmpty() && clicks==1 && !marker.isPressed(),"marker tap with jitter");
            reset(); send(0,mx,my); send(3,mx,my);
            check(seen.isEmpty() && clicks==0 && !marker.isPressed(),"cancel before handoff");
            reset(); send(0,mx,my); send(5|(1<<8),mx,my,mx+200,my); send(3,mx,my,mx+200,my);
            check(seen.equals(sent) && clicks==0,"cancel after handoff");
            reset(); send(0,mx+250,my); send(1,mx+250,my);
            check(seen.equals(sent) && clicks==0,"ordinary map tap");
            reset(); send(0,mx,my); layer.setVisibility(View.GONE); send(1,mx,my);
            check(seen.equals(sent) && clicks==0,"hidden overlay releases gesture"); layer.setVisibility(View.VISIBLE);
            reset(); marker.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null);
            check(clicks==1,"accessibility click");
            reset(); send(0,mx,my); send(1,mx,my); send(0,mx,my); send(1,mx,my);
            check(clicks==2 && seen.isEmpty(),"successive marker taps");
            Window.Callback wrapper=getWindow().getCallback();
            reset(); send(0,mx,my); router.close();
            check(!marker.isPressed() && getWindow().getCallback()!=wrapper,"close clears pending tap and restores callback");
            reset(); send(0,mx,my); send(1,mx,my);
            check(seen.equals(sent) && clicks==0,"touches after close");
            router=new MarkerTouchRouter(getWindow(),layer); router.install();
            getWindow().setCallback(this); router.close();
            check(getWindow().getCallback()==this,"close preserves later callback replacement");
            finishReport(null);
        } catch(Throwable t) { finishReport(t); }
    }

    void check(boolean ok,String name) {
        if(!ok)throw new AssertionError(name+" sent="+sent+" received="+seen+" clicks="+clicks);
        checks++; report.append("PASS ").append(name).append('\n');
    }
    void finishReport(Throwable error) {
        if(router!=null)router.close();
        report.append(error==null?"SUCCESS "+checks+" checks":"FAIL "+android.util.Log.getStackTraceString(error));
        try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"result.txt"))) {
            out.write(report.toString().getBytes("UTF-8"));
        } catch(Exception e) { throw new RuntimeException(e); }
    }
    String describe(MotionEvent e) {
        String s=e.getAction()+":"+e.getDownTime()+":"+e.getEventTime();
        for(int i=0;i<e.getPointerCount();i++)s+="/"+e.getPointerId(i)+","+e.getX(i)+","+e.getY(i);
        return s;
    }
    void send(int action,float...xy) {
        int n=xy.length/2;
        MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[n];
        MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[n];
        for(int i=0;i<n;i++) {
            props[i]=new MotionEvent.PointerProperties();props[i].id=7+i*4;props[i].toolType=1;
            coords[i]=new MotionEvent.PointerCoords();coords[i].x=xy[i*2];coords[i].y=xy[i*2+1];coords[i].pressure=1;
        }
        MotionEvent e=MotionEvent.obtain(time,time+sent.size()*16,action,n,props,coords,0,0,1,1,0,0,0x1002,0);
        sent.add(describe(e));getWindow().getCallback().dispatchTouchEvent(e);e.recycle();
    }

    /** Original v1.3.0 dispatch logic: retained only to prove this regression. */
    final class LegacyLayer extends FrameLayout {
        final int slop; MotionEvent down; boolean forwarding,drag;
        LegacyLayer(){super(MarkerTouchActivity.this);slop=ViewConfiguration.get(getContext()).getScaledTouchSlop();}
        void releaseTouch(){if(down!=null){down.recycle();down=null;}drag=false;}
        public boolean dispatchTouchEvent(MotionEvent e) {
            if(forwarding)return false;
            if(e.getActionMasked()==0)releaseTouch();
            boolean handled=super.dispatchTouchEvent(e);
            if(e.getActionMasked()==0&&handled)down=MotionEvent.obtain(e);
            if(e.getActionMasked()==1||e.getActionMasked()==3)releaseTouch();
            return handled;
        }
        public boolean onInterceptTouchEvent(MotionEvent e) {
            if(down!=null&&(e.getPointerCount()>1||Math.hypot(e.getX()-down.getX(),e.getY()-down.getY())>slop)){drag=true;return true;}
            return false;
        }
        public boolean onTouchEvent(MotionEvent e) {
            if(!drag||down==null)return false;
            MotionEvent start=down;down=null;forwarding=true;
            try {root.dispatchTouchEvent(start);root.dispatchTouchEvent(e);}
            finally {forwarding=false;start.recycle();drag=false;}
            return true;
        }
    }
}
