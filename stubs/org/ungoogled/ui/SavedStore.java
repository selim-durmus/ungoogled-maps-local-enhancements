package org.ungoogled.ui;
final class SavedStore {
    static class Place { double lat, lng; String name, ftid; final java.util.Set<String> lists = new java.util.LinkedHashSet<>(); }
    static Place home, work;
    static synchronized void load(android.content.Context c) {}
    static synchronized java.util.List<Place> allSaved() { return new java.util.ArrayList<>(); }
}
