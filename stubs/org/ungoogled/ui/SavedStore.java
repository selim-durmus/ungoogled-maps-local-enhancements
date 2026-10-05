package org.ungoogled.ui;
final class SavedStore {
    static class Place { double lat, lng; String name, ftid; final java.util.Set<String> lists = new java.util.LinkedHashSet<>(); }
    static Place home, work;
    static final java.util.Map<String,Place> labels = new java.util.LinkedHashMap<>();
    static synchronized void setLabel(android.content.Context c, String name, Place p) {}
    static synchronized void removeLabel(android.content.Context c, String name) {}
    static synchronized void setHome(android.content.Context c, Place p) {}
    static synchronized void setWork(android.content.Context c, Place p) {}
    static Place aliasOf(Place p) { return p; }
    static java.util.List<String> labelsFor(Place p) { return new java.util.ArrayList<>(); }
    static synchronized void load(android.content.Context c) {}
    static synchronized java.util.List<Place> allSaved() { return new java.util.ArrayList<>(); }
}
