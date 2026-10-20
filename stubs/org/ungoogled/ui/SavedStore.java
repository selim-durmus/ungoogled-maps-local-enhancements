package org.ungoogled.ui;
final class SavedStore {
    static class Place { double lat, lng; String name, ftid; }
    static Place home, work;
    static final java.util.Map<String,Place> labels = new java.util.LinkedHashMap<>();
    static synchronized void load(android.content.Context c) {}
}
