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

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.taobao.android.dexposed.DexposedBridge;
import com.taobao.android.dexposed.XC_MethodHook;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.arpnetwork.arp.container.proxy.ActivityThreadHandlerCallback;
import org.arpnetwork.arp.container.proxy.IActivityManagerHandler;
import org.arpnetwork.arp.container.proxy.IPackageManagerHandler;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

public class Hook {
    private static final String TAG = "Hook";

    public static final String EXTRA_TARGET_INTENT = "TARGET_INTENT";

    public static boolean init(final Context context, final App app) {
        try {
            patchActivityManager(context);

            patchPackageMananger(context, app);

            patchActivityThread();

            patchClassLoader(context.getClassLoader(), app);

            DexposedBridge.findAndHookMethod(Instrumentation.class, "callActivityOnCreate", Activity.class, Bundle.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    patchResources((Activity) param.args[0], app);
                }
            });
        } catch (ReflectiveOperationException e) {
            Log.e(TAG, "init failed. reason: " + e.getMessage());

            return false;
        }

        return true;
    }

    private static void patchActivityManager(Context context) throws ReflectiveOperationException {
        Class<?> activityManagerClass = Class.forName("android.app.ActivityManager");
        Class<?> activityManagerInterface = Class.forName("android.app.IActivityManager");
        Class<?> singletonClass = Class.forName("android.util.Singleton");
        Field instanceField = FieldUtils.getField(singletonClass, "mInstance", true);

        Object singleton = FieldUtils.readStaticField(activityManagerClass, "IActivityManagerSingleton", true);
        Object rawAM = FieldUtils.readField(instanceField, singleton);

        Object proxy = Proxy.newProxyInstance(
                context.getClassLoader(),
                new Class[]{activityManagerInterface},
                new IActivityManagerHandler(rawAM));
        FieldUtils.writeField(instanceField, singleton, proxy);

        Log.i(TAG, "ActivityManager patched.");
    }

    private static void patchPackageMananger(Context context, App app) throws ReflectiveOperationException {
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Class<?> packageManagerInterface = Class.forName("android.content.pm.IPackageManager");

        Field sPackageManagerField = FieldUtils.getField(activityThreadClass, "sPackageManager", true);
        Object sPackageManager = FieldUtils.readStaticField(sPackageManagerField);
        Object proxy = Proxy.newProxyInstance(
                context.getClassLoader(),
                new Class[]{packageManagerInterface},
                new IPackageManagerHandler(sPackageManager, app));
        FieldUtils.writeStaticField(sPackageManagerField, proxy);

        Log.i(TAG, "PackageManager patched.");
    }

    private static void patchActivityThread() throws ReflectiveOperationException {
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Object currentActivityThread = FieldUtils.readStaticField(activityThreadClass, "sCurrentActivityThread", true);
        Handler mH = (Handler) FieldUtils.readField(currentActivityThread, "mH", true);
        FieldUtils.writeField(mH, "mCallback", new ActivityThreadHandlerCallback(mH), true);

        Log.i(TAG, "ActivityThread patched.");
    }

    private static void patchClassLoader(ClassLoader cl, App app) throws ReflectiveOperationException {
        // Object pathList = cl.pathList;
        // String dexPath = app.getApkPath();
        // File optimizedDirectory = app.getOptimizedDirectory();
        // pathList.addDexPath(dexPath, optimizedDirectory);

        Object pathList = FieldUtils.readField(cl, "pathList", true);
        String dexPath = app.getApkPath();
        File optimizedDirectory = app.getOptimizedDirectory();
        MethodUtils.invokeMethod(pathList, true, "addDexPath", dexPath, optimizedDirectory);

        Log.i(TAG, "ClassLoader patched.");
    }

    private static void patchResources(Activity activity, App app) throws ReflectiveOperationException {
        ActivityInfo activityInfo = app.getActivityInfo(activity.getClass().getName());

        // Update Theme & Resources
        ActivityInfo ai = (ActivityInfo) FieldUtils.readField(activity, "mActivityInfo", true);
        ai.icon = activityInfo.getIconResource();
        ai.logo = activityInfo.getLogoResource();
        ai.nonLocalizedLabel = activityInfo.nonLocalizedLabel;
        ai.labelRes = activityInfo.labelRes;
        ai.applicationInfo.nonLocalizedLabel = activityInfo.applicationInfo.nonLocalizedLabel;
        ai.applicationInfo.labelRes = activityInfo.applicationInfo.labelRes;
        ai.theme = activityInfo.theme;
        if (ai.theme == 0) {
            ai.theme = activityInfo.applicationInfo.theme;
        }
        Resources resources = app.getResources();
        Resources.Theme theme = resources.newTheme();
        theme.setTo(activity.getApplicationContext().getTheme());
        if (ai.theme > 0) {
            theme.applyStyle(ai.theme, true);
        }
        FieldUtils.writeField(activity, "mResources", resources, true);
        FieldUtils.writeField(activity, "mTheme", theme, true);

        // Update title
        activity.setTitle(app.loadLabel(activityInfo));

        Log.i(TAG, "resources patched. activity: " + activity.getLocalClassName());
    }
}
