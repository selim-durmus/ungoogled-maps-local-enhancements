package org.ungoogled.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Screen-space grouping only. Every visible input remains selectable in a group. */
final class MarkerGeometry {
    static boolean valid(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng)
                && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    static final class Point {
        final int index;
        final float x, y;
        Point(int index, float x, float y) { this.index = index; this.x = x; this.y = y; }
    }

    static final class Group {
        final Point anchor;
        final List<Integer> members = new ArrayList<>();
        Group(Point p) { anchor = p; members.add(p.index); }
    }

    private static long cell(int x, int y) { return ((long) x << 32) ^ (y & 0xffffffffL); }

    static List<Group> group(List<Point> points, float spacing, float width, float height) {
        if (!(spacing > 0) || !Float.isFinite(spacing)) throw new IllegalArgumentException("spacing");
        List<Group> result = new ArrayList<>();
        Map<Long, List<Group>> cells = new HashMap<>();
        float radius = spacing / 2, squared = spacing * spacing;
        for (Point p : points) {
            if (!Float.isFinite(p.x) || !Float.isFinite(p.y)
                    || p.x < radius || p.y < radius || p.x > width - radius || p.y > height - radius) continue;
            int cx = (int) Math.floor(p.x / spacing), cy = (int) Math.floor(p.y / spacing);
            Group nearest = null; float distance = squared;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                List<Group> bucket = cells.get(cell(cx + dx, cy + dy));
                if (bucket == null) continue;
                for (Group g : bucket) {
                    float x = p.x - g.anchor.x, y = p.y - g.anchor.y, d = x * x + y * y;
                    if (d < distance) { distance = d; nearest = g; }
                }
            }
            if (nearest != null) nearest.members.add(p.index);
            else {
                Group g = new Group(p); result.add(g);
                long key = cell(cx, cy);
                List<Group> bucket = cells.get(key);
                if (bucket == null) { bucket = new ArrayList<>(); cells.put(key, bucket); }
                bucket.add(g);
            }
        }
        return result;
    }
}
