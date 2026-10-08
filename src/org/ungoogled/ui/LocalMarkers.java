package org.ungoogled.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.lang.reflect.*;
import java.util.*;

/** Local saved-place markers. Reads the existing store; never changes the camera or saved data. */
public final class LocalMarkers {
    private static final String MAP = "com.google.android.apps.gmm.base.views.map.MapViewContainer";
    private static final WeakHashMap<Activity, Controller> active = new WeakHashMap<>();
    private LocalMarkers() {}
    public static void resume(Activity a) {
        if (!"com.google.android.maps.MapsActivity".equals(a.getClass().getName())) return;
        pause(a);
        LocalLabels.resume(a);
        try { SavedStore.load(a); Controller c = new Controller(a); active.put(a,c); c.root.post(c); }
        catch (Throwable t) { android.util.Log.w("UA-Markers","Markers unavailable",t); }
    }
    public static void pause(Activity a) { LocalLabels.pause(a); Controller c=active.remove(a); if(c!=null)c.close(); }
    private static ViewGroup carousel(View v) {
        for(Class<?> t=v.getClass();t!=null;t=t.getSuperclass())if(t.getName().equals("android.support.v7.widget.RecyclerView"))return (ViewGroup)v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){ViewGroup found=carousel(g.getChildAt(i));if(found!=null&&found.isShown())return found;}}
        return null;
    }
    private static boolean shown(View v) { return v!=null && v.isShown(); }
    private static View findMap(View v) {
        if(MAP.equals(v.getClass().getName()) && v.isShown())return v;
        if(v instanceof ViewGroup) { ViewGroup g=(ViewGroup)v; for(int i=0;i<g.getChildCount();i++) {
            View found=findMap(g.getChildAt(i)); if(found!=null)return found;
        }}
        return null;
    }
    private static final class Entry {
        final SavedStore.Place place; final String key,title,caption; final int style; final Object coordinate;
        Entry(SavedStore.Place p,String k,String t,String label,int s,Object c) {place=p;key=k;title=t;caption=label;style=s;coordinate=c;}
    }
    private static final class Controller implements Runnable {
        final Activity activity; final ViewGroup root; final Layer layer; final float density;
        final Method makePoint,project,getSnapshot,getCamera,projectionState; final Field pixelX,pixelY;
        final Constructor<?> legacyProjection; final Map<String,MarkerButton> buttons=new HashMap<>();
        List<Entry> entries=new ArrayList<>(); FrameLayout map; Object renderer,camera; Method legacyCamera;
        boolean closed; long refreshed, masked; int failures, lastGeometry; Object lastCamera;
        final int[] mapOffset=new int[2], layerOffset=new int[2]; final List<Rect> exclusions=new ArrayList<>();
        Controller(Activity a)throws Exception {
            activity=a;root=(ViewGroup)a.getWindow().getDecorView();density=a.getResources().getDisplayMetrics().density;
            layer=new Layer(this);
            Class<?> geo=Class.forName("bjbd"); makePoint=geo.getMethod("F",double.class,double.class);
            project=Class.forName("bjje").getMethod("h",geo);projectionState=Class.forName("bjje").getMethod("j");
            getSnapshot=Class.forName("bjja").getMethod("d");getCamera=Class.forName("bjip").getMethod("g");
            Class<?> pixel=Class.forName("bjbz");pixelX=pixel.getField("b");pixelY=pixel.getField("c");
            legacyProjection=Class.forName("bkth").getConstructor(Class.forName("bjof"));
        }
        boolean bind()throws Exception {
            if(map==null || !map.isAttachedToWindow() || !map.isShown()) {
                View found=findMap(root); if(!(found instanceof FrameLayout))return false;
                map=(FrameLayout)found; renderer=null;
            }
            Object next=map.getClass().getField("e").get(map);boolean legacy=next==null;
            if(legacy)next=map.getClass().getField("d").get(map);
            if(next==null)return false;
            if(next!=renderer) {
                renderer=next;lastCamera=null;legacyCamera=legacy?next.getClass().getMethod("d"):null;
                camera=legacy?null:getCamera.invoke(next);
                android.util.Log.i("UA-Markers","Bound local marker projection");
            }
            if(layer.getParent()!=root) {
                if(layer.getParent() instanceof ViewGroup)((ViewGroup)layer.getParent()).removeView(layer);
                root.addView(layer,new FrameLayout.LayoutParams(-1,-1));
            }
            layer.touch.install();
            return true;
        }
        void refresh()throws Exception {
            LinkedHashMap<String,Entry> next=new LinkedHashMap<>();
            synchronized(SavedStore.class) {
                add(next,SavedStore.home,"Home",0);add(next,SavedStore.work,"Work",1);
                for(SavedStore.Place p:SavedStore.allSaved()) add(next,p,p.name,p.lists.contains("favourites")?2:
                    p.lists.contains("starred")?3:p.lists.contains("want_to_go")?4:5);
                for(SavedStore.Place p:LocalLabels.labeledPlaces())add(next,p,p.name,5);
            }
            entries=new ArrayList<>(next.values());refreshed=SystemClock.uptimeMillis();
        }
        void add(Map<String,Entry> into,SavedStore.Place p,String name,int style)throws Exception {
            if(p==null || !MarkerGeometry.valid(p.lat,p.lng))return;
            String key=p.ftid!=null&&!p.ftid.isEmpty()?p.ftid:Math.round(p.lat*1000000)+","+Math.round(p.lng*1000000);
            if(into.containsKey(key))return;
            String title=name==null||name.trim().isEmpty()?"Saved place":name;
            String label=LocalLabels.caption(p);
            if(!label.isEmpty())title=label;
            else if(style<2)label=title;
            into.put(key,new Entry(p,key,title,label,style,makePoint.invoke(null,p.lat,p.lng)));
        }
        public void run() {
            // Position changes join the display's animation phase before view drawing.
            // A wall-clock timer can leave the overlay several frames behind the map.
            if(closed)return;long delay=0;
            try {
                if(!activity.hasWindowFocus() || !browseMap() ||
                    shown(root.findViewById(0x7f0b0a60)) || shown(root.findViewById(0x7f0b0317)) || shown(root.findViewById(0x7f0b06b9)) || !bind()) {
                    layer.setVisibility(View.GONE);delay=250;
                } else {
                    if(SystemClock.uptimeMillis()-refreshed>=1000)refresh();
                    if(entries.isEmpty()) {layer.setVisibility(View.GONE);delay=250;}
                    else {
                        Object projection=legacyCamera==null?getSnapshot.invoke(camera):legacyProjection.newInstance(legacyCamera.invoke(renderer));
                        map.getLocationOnScreen(mapOffset); layer.getLocationOnScreen(layerOffset);
                        if(SystemClock.uptimeMillis()-masked>200){exclusions.clear();collectControls(root);masked=SystemClock.uptimeMillis();}
                        Object state=projectionState.invoke(projection);
                        int geometry=Objects.hash(refreshed,mapOffset[0],mapOffset[1],root.getWidth(),root.getHeight(),exclusions.hashCode());
                        if(state.equals(lastCamera)&&geometry==lastGeometry){layer.setVisibility(View.VISIBLE);failures=0;root.postOnAnimation(this);return;}
                        lastCamera=state;lastGeometry=geometry;
                        List<MarkerGeometry.Point> pixels=new ArrayList<>();
                        for(int i=0;i<entries.size();i++) {
                            Object p=project.invoke(projection,entries.get(i).coordinate);
                            if(p!=null){float x=pixelX.getFloat(p)+mapOffset[0]-layerOffset[0],y=pixelY.getFloat(p)+mapOffset[1]-layerOffset[1];
                                if(!covered(x,y))pixels.add(new MarkerGeometry.Point(i,x,y));}
                        }
                        update(MarkerGeometry.group(pixels,44*density,root.getWidth(),root.getHeight()));
                        layer.setVisibility(View.VISIBLE);
                    }
                }
                failures=0;
            } catch(Throwable t) {
                layer.setVisibility(View.GONE);delay=1000;
                if(++failures==1)android.util.Log.w("UA-Markers","Marker projection unavailable",t);
                if(failures>=3){close();return;}
            }
            // Keep slow polling only while hidden or recovering from a failed binding.
            // Store refresh and control masks retain their independent, slower cadence.
            if(delay==0)root.postOnAnimation(this);else root.postDelayed(this,delay);
        }
        boolean browseMap() {
            View v=root.findViewById(0x7f0b0166);Rect r=new Rect();
            if(v==null||!v.isShown()||!v.getGlobalVisibleRect(r)||r.height()<32*density)return false;
            ViewGroup row=carousel(v);
            return row!=null&&row.isShown()&&row.getHeight()>=32*density&&row.getChildCount()>0;
        }
        void collectControls(View v) {
            if(v==map || v==layer || !v.isShown())return;
            Rect r=new Rect();
            if(v.hasOnClickListeners() && v.getGlobalVisibleRect(r) && r.width()<root.getWidth()*0.9f && r.height()<root.getHeight()*0.5f){
                r.offset(-layerOffset[0],-layerOffset[1]);r.inset(-(int)(8*density),-(int)(8*density));exclusions.add(r);return;
            }
            if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)collectControls(g.getChildAt(i));}
        }
        boolean covered(float x,float y) {
            float radius=22*density;
            View top=root.findViewById(0x7f0b0166);Rect bounds=new Rect();
            if(top!=null&&top.getGlobalVisibleRect(bounds)&&y-radius<bounds.bottom-layerOffset[1])return true;
            if(y+radius>root.getHeight()-32*density)return true;
            Rect hit=new Rect((int)(x-radius),(int)(y-radius),(int)(x+radius),(int)(y+radius));
            for(Rect r:exclusions)if(Rect.intersects(hit,r))return true;
            return false;
        }
        void update(List<MarkerGeometry.Group> groups) {
            Set<String> keep=new HashSet<>();int size=Math.round(44*density);
            for(MarkerGeometry.Group g:groups) {
                Entry anchor=entries.get(g.anchor.index);keep.add(anchor.key);
                MarkerButton button=buttons.get(anchor.key);
                if(button==null){button=new MarkerButton(this);buttons.put(anchor.key,button);layer.addView(button,new FrameLayout.LayoutParams(size,size));}
                List<Entry> members=new ArrayList<>();for(int i:g.members)members.add(entries.get(i));
                button.update(members);button.setTranslationX(g.anchor.x-size/2f);button.setTranslationY(g.anchor.y-size/2f);
            }
            for(String k:new ArrayList<>(buttons.keySet()))if(!keep.contains(k))layer.removeView(buttons.remove(k));
            layer.captions(groups,entries,exclusions);
        }
        void open(List<Entry> places) {
            if(places.isEmpty())return;
            if(places.size()==1){SavedPlaces.open(activity,places.get(0).place);return;}
            String[] names=new String[places.size()];for(int i=0;i<names.length;i++)names[i]=places.get(i).title;
            new AlertDialog.Builder(activity).setTitle("Saved places here").setItems(names,
                (dialog,which)->SavedPlaces.open(activity,places.get(which).place)).setNegativeButton("Close",null).show();
        }
        void close() {
            closed=true;root.removeCallbacks(this);layer.touch.close();
            if(layer.getParent() instanceof ViewGroup)((ViewGroup)layer.getParent()).removeView(layer);
            buttons.clear();entries.clear();camera=renderer=null;map=null;
        }
    }
    /** Decor overlay avoids renderer child-count invariants; controls are excluded from marker targets. */
    private static final class Layer extends FrameLayout {
        final Controller owner;final MarkerTouchRouter touch;
        final android.text.TextPaint text=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        final List<String> names=new ArrayList<>();final List<Rect> labels=new ArrayList<>();
        Layer(Controller c) {super(c.activity);owner=c;touch=new MarkerTouchRouter(c.activity.getWindow(),this);setClipChildren(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void captions(List<MarkerGeometry.Group> groups,List<Entry> entries,List<Rect> controls) {
            names.clear();labels.clear();float d=owner.density;
            text.setTextSize(12*d);text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            List<Rect> occupied=new ArrayList<>(controls);
            for(MarkerGeometry.Group g:groups)occupied.add(new Rect((int)(g.anchor.x-17*d),(int)(g.anchor.y-17*d),(int)(g.anchor.x+17*d),(int)(g.anchor.y+17*d)));
            for(MarkerGeometry.Group g:groups) {
                Entry entry=entries.get(g.anchor.index);if(g.members.size()!=1||entry.caption.isEmpty())continue;
                String value=android.text.TextUtils.ellipsize(entry.caption,text,130*d,android.text.TextUtils.TruncateAt.END).toString();
                int width=(int)Math.ceil(text.measureText(value)+4*d),height=(int)Math.ceil(18*d);
                int x=(int)g.anchor.x,y=(int)g.anchor.y;
                Rect[] options={new Rect(x+(int)(18*d),y-height/2,x+(int)(18*d)+width,y+height/2),
                    new Rect(x-(int)(18*d)-width,y-height/2,x-(int)(18*d),y+height/2),
                    new Rect(x-width/2,y-(int)(17*d)-height,x+width/2,y-(int)(17*d))};
                for(Rect r:options) {
                    if(r.left<0||r.right>getWidth()||r.top<0||r.bottom>getHeight()-32*d)continue;
                    boolean collision=false;for(Rect other:occupied)if(Rect.intersects(r,other)){collision=true;break;}
                    if(collision)continue;names.add(value);labels.add(r);occupied.add(r);break;
                }
            }
            invalidate();
        }
        protected void dispatchDraw(Canvas c) {
            super.dispatchDraw(c);float d=owner.density;
            for(int i=0;i<names.size();i++) {
                Rect r=labels.get(i);float x=r.left+2*d,y=r.centerY()-(text.ascent()+text.descent())/2;
                text.setStyle(Paint.Style.STROKE);text.setStrokeWidth(3*d);text.setStrokeJoin(Paint.Join.ROUND);text.setColor(0xff172333);
                c.drawText(names.get(i),x,y,text);text.setStyle(Paint.Style.FILL);text.setColor(0xffc5d7ff);c.drawText(names.get(i),x,y,text);
            }
        }
        // Touches reach the native map intact. The window router handles only marker taps;
        // clickable/focusable children retain their accessibility actions.
        public boolean dispatchTouchEvent(MotionEvent e) {
            return false;
        }
    }
    private static final class MarkerButton extends View {
        static final int[] ICONS={0x7f080587,0x7f08063a,0x7f08056a,0x7f08060b,0x7f08056f,0x7f080515};
        // Soft category colors and dark glyphs blend with Maps' night-mode POIs.
        // Only the drawing shrinks; the 44 dp accessible/touch target is unchanged.
        static final int[] COLORS={0xffadc6ff,0xffadc6ff,0xffefa6d3,0xfff6c178,0xff9ec9a6,0xffadc6ff};
        static final int INK=0xff1b2638, RIM=0xff65758c;
        final Controller owner;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        List<Entry> members=new ArrayList<>();Drawable icon;int style=-1,count;
        MarkerButton(Controller c){super(c.activity);owner=c;setClickable(true);setFocusable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setOnClickListener(v->owner.open(new ArrayList<>(members)));}
        void update(List<Entry> next) {
            members=next;String label=next.size()==1?next.get(0).title+", saved place":next.size()+" saved places near "+next.get(0).title;
            if(!label.contentEquals(getContentDescription()==null?"":getContentDescription()))setContentDescription(label);
            int s=next.size()>1?5:next.get(0).style;boolean changed=style!=s||count!=next.size();
            if(style!=s){style=s;icon=getContext().getDrawable(ICONS[s]).mutate();icon.setTint(INK);}
            count=next.size();if(changed)invalidate();
        }
        protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
        protected void onDraw(Canvas canvas) {
            float d=owner.density,x=getWidth()/2f,y=getHeight()/2f;paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x30000000);canvas.drawCircle(x,y+d,12.5f*d,paint);
            paint.setColor(RIM);canvas.drawCircle(x,y,12*d,paint);
            paint.setColor(COLORS[Math.max(0,style)]);canvas.drawCircle(x,y,10*d,paint);
            if(count>1){paint.setColor(INK);paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize((count>99?8:11)*d);canvas.drawText(count>999?"999+":String.valueOf(count),x,y-(paint.ascent()+paint.descent())/2,paint);}
            else if(icon!=null){int s=Math.round(7.5f*d);icon.setBounds((int)x-s,(int)y-s,(int)x+s,(int)y+s);icon.draw(canvas);}
            if(isPressed()||isFocused()){paint.setColor(0x554285f4);canvas.drawCircle(x,y,21*d,paint);}
        }
    }
}
