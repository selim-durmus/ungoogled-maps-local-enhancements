package org.ungoogled.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;

/** Local aliases shared by the search header, marker captions and both label editors. */
public final class LocalLabels {
    private static final WeakHashMap<Activity,Search> active=new WeakHashMap<>();
    private LocalLabels() {}
    static String key(SavedStore.Place p) {
        return p.ftid!=null&&!p.ftid.isEmpty()?p.ftid:String.format(Locale.US,"%.6f,%.6f",p.lat,p.lng);
    }
    static String caption(SavedStore.Place p) {
        synchronized(SavedStore.class) {
            for(Map.Entry<String,SavedStore.Place> e:SavedStore.labels.entrySet())
                if(key(e.getValue()).equals(key(p)))return e.getKey();
        }
        return "";
    }
    static List<SavedStore.Place> labeledPlaces() {
        synchronized(SavedStore.class){return new ArrayList<>(SavedStore.labels.values());}
    }
    private static SavedStore.Place existingPlace(SavedStore.Place nativePlace) {
        synchronized(SavedStore.class) {
            String identity=key(nativePlace);
            for(SavedStore.Place p:SavedStore.allSaved())if(key(p).equals(identity))return p;
            for(SavedStore.Place p:SavedStore.labels.values())if(key(p).equals(identity))return p;
            if(SavedStore.home!=null&&key(SavedStore.home).equals(identity))return SavedStore.home;
            if(SavedStore.work!=null&&key(SavedStore.work).equals(identity))return SavedStore.work;
        }
        return nativePlace;
    }
    private static final class Match {
        final String label;final SavedStore.Place place;
        Match(String l,SavedStore.Place p){label=l;place=p;}
    }
    private static List<Match> matches(String query) {
        List<Match> all=new ArrayList<>();List<String> names=new ArrayList<>();
        synchronized(SavedStore.class) {
            if(SavedStore.home!=null)all.add(new Match("Home",SavedStore.home));
            if(SavedStore.work!=null)all.add(new Match("Work",SavedStore.work));
            for(Map.Entry<String,SavedStore.Place> e:SavedStore.labels.entrySet())all.add(new Match(e.getKey(),e.getValue()));
        }
        for(Match m:all)names.add(m.label);
        List<Match> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int i:LabelIndex.rank(names,query)) {
            Match m=all.get(i);
            if(MarkerGeometry.valid(m.place.lat,m.place.lng)&&seen.add(key(m.place)))result.add(m);
        }
        return result;
    }
    public static void resume(Activity a) {
        if(!a.getClass().getName().equals("com.google.android.maps.MapsActivity"))return;
        pause(a);try{SavedStore.load(a);Search s=new Search(a);active.put(a,s);s.root.post(s);}
        catch(Throwable t){android.util.Log.w("UA-Labels","Local label search unavailable",t);}
    }
    public static void pause(Activity a){Search s=active.remove(a);if(s!=null)s.close();}
    private static final class Search implements Runnable,TextWatcher {
        final Activity activity;final ViewGroup root;final LinearLayout header;final float density;
        final int inputId,listId;EditText input;View list;boolean closed;String signature="";
        int left,top,right,bottom,shift;float translation;Rect clip;
        Search(Activity a) {
            activity=a;root=(ViewGroup)a.getWindow().getDecorView();density=a.getResources().getDisplayMetrics().density;
            inputId=a.getResources().getIdentifier("search_omnibox_edit_text","id",a.getPackageName());
            listId=0x7f0b0d2c;header=new LinearLayout(a);header.setOrientation(LinearLayout.VERTICAL);
            header.setBackgroundColor(0xff121212);header.setVisibility(View.GONE);
            root.addView(header,new FrameLayout.LayoutParams(-1,-2));
        }
        int dp(float v){return Math.round(v*density);}
        public void run() {
            if(closed)return;
            try {
                View candidate=root.findViewById(inputId);
                EditText next=candidate instanceof EditText?(EditText)candidate:null;
                if(input!=next){if(input!=null)input.removeTextChangedListener(this);input=next;
                    if(input!=null)input.addTextChangedListener(this);signature="";}
                render();
            } catch(Throwable t){android.util.Log.w("UA-Labels","Local label search stopped",t);close();return;}
            root.postDelayed(this,150);
        }
        public void beforeTextChanged(CharSequence s,int start,int count,int after){}
        public void onTextChanged(CharSequence s,int start,int before,int count){signature="";root.post(this::renderSafely);}
        public void afterTextChanged(Editable e){}
        void renderSafely(){if(!closed)try{render();}catch(Throwable t){close();android.util.Log.w("UA-Labels","Local results unavailable",t);}}
        void restore() {
            if(list!=null){list.setTranslationY(translation);list.setPadding(left,top,right,bottom);list.setClipBounds(clip);}
            list=null;shift=0;signature="";header.setVisibility(View.GONE);
        }
        void render() {
            if(closed)return;
            View next=root.findViewById(listId);
            if(input==null||!input.isShown()||!input.hasFocus()||!activity.hasWindowFocus()||next==null||!next.isShown()) {restore();return;}
            List<Match> found=matches(input.getText().toString());
            if(found.isEmpty()){restore();return;}
            if(next!=list){restore();list=next;translation=list.getTranslationY();clip=list.getClipBounds();
                left=list.getPaddingLeft();top=list.getPaddingTop();right=list.getPaddingRight();bottom=list.getPaddingBottom();}
            int count=Math.min(3,Math.min(found.size(),Math.max(0,(list.getHeight()-dp(100))/dp(58))));
            if(count==0){restore();return;}
            StringBuilder stamp=new StringBuilder(input.getText());
            for(int i=0;i<count;i++){Match m=found.get(i);stamp.append('|').append(m.label).append('|').append(key(m.place)).append('|').append(m.place.name);}
            String current=stamp.toString();
            if(!current.equals(signature)) {
                header.removeAllViews();TextView title=text("Your labels",12,0xffa8c7fa);
                title.setPadding(dp(20),dp(5),dp(20),dp(3));header.addView(title,new LinearLayout.LayoutParams(-1,dp(26)));
                for(int i=0;i<count;i++) {
                    Match m=found.get(i);LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(22),0,dp(16),0);row.setMinimumHeight(dp(58));
                    android.util.TypedValue ripple=new android.util.TypedValue();activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,ripple,true);
                    if(ripple.resourceId!=0)row.setBackgroundResource(ripple.resourceId);
                    ImageView icon=new ImageView(activity);icon.setImageResource(0x7f08066f);icon.setColorFilter(0xffa8c7fa);
                    row.addView(icon,new LinearLayout.LayoutParams(dp(24),dp(24)));
                    LinearLayout words=new LinearLayout(activity);words.setOrientation(LinearLayout.VERTICAL);words.setPadding(dp(18),0,0,0);
                    TextView label=text(m.label,16,0xffe8eaed);label.setTypeface(null,Typeface.BOLD);words.addView(label);
                    words.addView(text(m.place.name==null||m.place.name.isEmpty()?"Saved location":m.place.name,12,0xffbdc1c6));
                    row.addView(words,new LinearLayout.LayoutParams(0,-2,1));row.setContentDescription(m.label+", local labeled place");
                    row.setFocusable(true);row.setOnClickListener(v->{
                        InputMethodManager ime=(InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                        if(ime!=null)ime.hideSoftInputFromWindow(input.getWindowToken(),0);
                        input.clearFocus();restore();SavedPlaces.open(activity,m.place);
                    });header.addView(row,new LinearLayout.LayoutParams(-1,dp(58)));
                }
                View divider=new View(activity);divider.setBackgroundColor(0xff3c4043);header.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
                signature=current;
            }
            int height=dp(27+count*58);int[] pos=new int[2],origin=new int[2];list.getLocationOnScreen(pos);root.getLocationOnScreen(origin);
            header.setTranslationX(pos[0]-origin[0]);header.setTranslationY(pos[1]-origin[1]-shift);
            ViewGroup.LayoutParams lp=header.getLayoutParams();if(lp.width!=list.getWidth()||lp.height!=height){lp.width=list.getWidth();lp.height=height;header.setLayoutParams(lp);}
            shift=height;list.setTranslationY(translation+shift);list.setPadding(left,top,right,bottom+shift);
            Rect bounds=new Rect(0,0,list.getWidth(),Math.max(0,list.getHeight()-shift));if(clip!=null)bounds.intersect(clip);
            list.setClipBounds(bounds);header.setVisibility(View.VISIBLE);
        }
        TextView text(String value,int sp,int color){TextView v=new TextView(activity);v.setText(value);v.setTextSize(sp);v.setTextColor(color);
            v.setSingleLine(true);v.setEllipsize(android.text.TextUtils.TruncateAt.END);return v;}
        void close(){closed=true;root.removeCallbacks(this);if(input!=null)input.removeTextChangedListener(this);restore();root.removeView(header);}
    }
    /** Overflow/controller label action in the pinned version. */
    public static boolean editNative(Object controller) {
        try {
            Class<?> type=Class.forName("atqs");Object selected=type.getField("l").get(controller);
            return editNativePlace(type.getField("a").get(controller),selected);
        }catch(Throwable t){android.util.Log.w("UA-Labels","Cannot identify label controller",t);return false;}
    }
    /** Place-sheet chip action; the hook passes its Activity and selected native place. */
    public static boolean editNativePlace(Object activity,Object selected) {
        try {
            Activity a=(Activity)activity;Class<?> place=Class.forName("oku");
            Class<?> wrapper=Class.forName("awvj");
            if(wrapper.isInstance(selected))selected=wrapper.getMethod("a").invoke(selected);
            if(!place.isInstance(selected))return false;
            SavedStore.Place nativePlace=SavedPlaces.place((String)place.getMethod("bz").invoke(selected),
                place.getMethod("p").invoke(selected),place.getMethod("q").invoke(selected));
            if(nativePlace==null||!MarkerGeometry.valid(nativePlace.lat,nativePlace.lng))return false;
            SavedStore.load(a);SavedStore.Place p=existingPlace(nativePlace);List<String> labels=SavedStore.labelsFor(p);
            if(labels.size()>1){List<String> choices=new ArrayList<>(labels);choices.add("Add another label");
                new AlertDialog.Builder(a,SavedPlaces.dialogTheme(a)).setTitle("Labels").setItems(choices.toArray(new String[0]),
                    (d,i)->edit(a,p,i<labels.size()?labels.get(i):null)).setNegativeButton("Cancel",null).show();}
            else edit(a,p,labels.isEmpty()?null:labels.get(0));
            return true;
        }catch(Throwable t){android.util.Log.w("UA-Labels","Cannot identify place for label",t);return false;}
    }
    public static void edit(Activity a,SavedStore.Place p,String old) {
        SavedStore.load(a);EditText field=new EditText(a);field.setSingleLine(true);field.setText(old==null?"":old);
        field.setHint("Label, like Gym");FrameLayout box=new FrameLayout(a);int pad=Math.round(20*a.getResources().getDisplayMetrics().density);
        box.setPadding(pad,pad/2,pad,0);box.addView(field);
        AlertDialog.Builder builder=new AlertDialog.Builder(a,SavedPlaces.dialogTheme(a)).setTitle(old==null?"Add label":"Edit label")
            .setView(box).setPositiveButton("Save",null).setNegativeButton("Cancel",null);
        if(old!=null)builder.setNeutralButton("Remove",(d,w)->{SavedStore.removeLabel(a,old);changed(a);});
        AlertDialog dialog=builder.create();
        dialog.setOnShowListener(d->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=field.getText().toString().trim();if(value.isEmpty()){field.setError("Enter a label");return;}
            synchronized(SavedStore.class){for(Map.Entry<String,SavedStore.Place> e:SavedStore.labels.entrySet())
                if(!e.getKey().equals(old)&&LabelIndex.normalize(e.getKey()).equals(LabelIndex.normalize(value))&&!key(e.getValue()).equals(key(p))) {
                    field.setError("This label is already used for another place");return;
                }
                if(value.equalsIgnoreCase("home"))SavedStore.setHome(a,SavedStore.aliasOf(p));
                else if(value.equalsIgnoreCase("work"))SavedStore.setWork(a,SavedStore.aliasOf(p));
                else SavedStore.setLabel(a,value,p);
                if(old!=null&&!old.equals(value))SavedStore.removeLabel(a,old);
            }
            changed(a);dialog.dismiss();
        });});dialog.show();
    }
    private static void changed(Activity a) {
        if(a.getClass().getName().equals("org.ungoogled.ui.YouActivity"))try {
            java.lang.reflect.Method render=a.getClass().getDeclaredMethod("render");render.setAccessible(true);render.invoke(a);
        }catch(Exception e){android.util.Log.w("UA-Labels","Local saved refresh unavailable",e);}
    }
}
