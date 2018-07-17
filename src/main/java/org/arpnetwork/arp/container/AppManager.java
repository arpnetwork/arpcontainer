package org.arpnetwork.arp.container;

import java.util.LinkedHashMap;

public class AppManager {
    private static LinkedHashMap<String, App> sApps = new LinkedHashMap<>();

    public static void put(App app) {
        sApps.put(app.getPackageName(), app);
    }

    public static App get(String packageName) {
        return sApps.get(packageName);
    }

    public static void remove(App app) {
        remove(app.getPackageName());
    }

    public static void remove(String packageName) {
        sApps.remove(packageName);
    }
}
