package org.ungoogled.ui;

import java.text.Normalizer;
import java.util.*;

/** Query normalization never changes a stored label or the place it identifies. */
final class LabelIndex {
    static String normalize(String text) {
        if(text==null)return "";
        return Normalizer.normalize(text,Normalizer.Form.NFKD).replaceAll("\\p{M}+","")
            .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+"," ");
    }
    static int score(String label,String query) {
        String l=normalize(label),q=normalize(query);
        if(q.isEmpty())return -1;
        if(l.equals(q))return 0;
        if(l.startsWith(q))return 1;
        return l.contains(q)?2:-1;
    }
    static List<Integer> rank(List<String> labels,String query) {
        List<Integer> out=new ArrayList<>();
        for(int i=0;i<labels.size();i++)if(score(labels.get(i),query)>=0)out.add(i);
        out.sort((a,b)->{int order=Integer.compare(score(labels.get(a),query),score(labels.get(b),query));
            return order!=0?order:labels.get(a).compareToIgnoreCase(labels.get(b));});
        return out;
    }
}
