/*
 * Copyright 2018 ARP Network
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
