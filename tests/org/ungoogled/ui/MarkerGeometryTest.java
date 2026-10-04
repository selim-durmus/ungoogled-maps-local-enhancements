package org.ungoogled.ui;
import java.util.*;
public final class MarkerGeometryTest {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        check(MarkerGeometry.group(Collections.emptyList(),44,1000,1000).isEmpty(), "empty store");
        check(MarkerGeometry.valid(0,0) && MarkerGeometry.valid(-90,180), "valid coordinates");
        check(!MarkerGeometry.valid(Double.NaN,0) && !MarkerGeometry.valid(91,0)
                && !MarkerGeometry.valid(0,Double.POSITIVE_INFINITY), "invalid coordinates");
        List<MarkerGeometry.Point> points = Arrays.asList(new MarkerGeometry.Point(0,100,100),
                new MarkerGeometry.Point(1,110,100), new MarkerGeometry.Point(2,300,300),
                new MarkerGeometry.Point(3,-30,100), new MarkerGeometry.Point(4,Float.NaN,100));
        List<MarkerGeometry.Group> groups = MarkerGeometry.group(points,44,1000,1000);
        check(groups.size()==2 && groups.get(0).members.equals(Arrays.asList(0,1)), "nearby places remain selectable");
        check(groups.get(0).anchor.index==0, "priority anchor preserved");
        points = Arrays.asList(new MarkerGeometry.Point(0,-10,500),
                new MarkerGeometry.Point(1,1010,500), new MarkerGeometry.Point(2,500,-10),
                new MarkerGeometry.Point(3,500,1010), new MarkerGeometry.Point(4,-22,100),
                new MarkerGeometry.Point(5,1022,100), new MarkerGeometry.Point(6,100,-22),
                new MarkerGeometry.Point(7,100,1022));
        groups=MarkerGeometry.group(points,44,1000,1000);
        Set<Integer> edgeMembers=new HashSet<>();
        for(MarkerGeometry.Group g:groups)edgeMembers.addAll(g.members);
        check(edgeMembers.equals(new HashSet<>(Arrays.asList(0,1,2,3))),
                "partially visible markers remain until fully outside each edge");
        points = new ArrayList<>(); Random random = new Random(7);
        for(int i=0;i<10000;i++) points.add(new MarkerGeometry.Point(i,22+random.nextFloat()*956,22+random.nextFloat()*956));
        groups=MarkerGeometry.group(points,44,1000,1000);
        Set<Integer> included = new HashSet<>();
        for(MarkerGeometry.Group g:groups) for(int member:g.members) check(included.add(member),"duplicate membership");
        check(included.size()==10000,"visible places lost");
        for(int i=0;i<groups.size();i++) for(int j=0;j<i;j++) {
            float dx=groups.get(i).anchor.x-groups.get(j).anchor.x,dy=groups.get(i).anchor.y-groups.get(j).anchor.y;
            check(dx*dx+dy*dy>=44*44,"marker targets overlap");
        }
        System.out.println("Marker geometry checks passed: bounds, priorities, clustering, 10,000-place membership.");
    }
}
