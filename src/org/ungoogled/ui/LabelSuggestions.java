package org.ungoogled.ui;

import android.app.Activity;
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

/**
 * "Your labels" above Maps' search suggestions while typing: Home, Work and Local saved labels
 * matching the query (exact, then prefix, then substring; case and accents ignored). Tapping a row
 * opens the place. Reads Local saved only; bearinmind's patch still handles a submitted label.
 */
public final class LabelSuggestions {
    private static final WeakHashMap<Activity,Search> active=new WeakHashMap<>();
    private LabelSuggestions() {}
    static String key(SavedStore.Place p) {
        return p.ftid!=null&&!p.ftid.isEmpty()?p.ftid:String.format(Locale.US,"%.6f,%.6f",p.lat,p.lng);
    }
    static boolean valid(double lat,double lng) {
        return Double.isFinite(lat)&&Double.isFinite(lng)&&lat>=-90&&lat<=90&&lng>=-180&&lng<=180;
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
            if(valid(m.place.lat,m.place.lng)&&seen.add(key(m.place)))result.add(m);
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
}
