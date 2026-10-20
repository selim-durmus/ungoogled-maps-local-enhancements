package org.ungoogled.ui;
final class SavedStore {
    static class Place { double lat, lng; }
    static Place home, work;
    static synchronized void load(android.content.Context c) {}
}
