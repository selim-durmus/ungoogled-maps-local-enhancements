package org.ungoogled.ui;
import java.util.*;
public final class LabelIndexTest {
    static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    public static void main(String[] args) {
        check(LabelIndex.rank(Arrays.asList("Sinem clinic","My Sinem","Sinem")," sinem ").equals(Arrays.asList(2,0,1)),"exact before prefix before substring");
        check(LabelIndex.score("SİNEM","sinem")==0,"Turkish dotted I");
        check(LabelIndex.score("Café Mum","cafe")==1,"accent matching");
        check(LabelIndex.score("Home","")==-1,"empty query");
        check(LabelIndex.score("Home","work")==-1,"no match");
        check(LabelIndex.score("  My   Gym ","my gym")==0,"spacing");
        check(LabelIndex.rank(Arrays.asList("Gym B","Gym A"),"gym").equals(Arrays.asList(1,0)),"stable alphabetical ties");
        System.out.println("Label matching checks passed.");
    }
}
