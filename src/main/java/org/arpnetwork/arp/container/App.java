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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

import dalvik.system.DexClassLoader;

public class App {
    private static final String TAG = "App";

    private Context mContext;
    private String mApkPath;

    private PackageInfo mPackageInfo;
    private ActivityInfo mMainActivityInfo;
    private Class<Activity> mMainActivityClass;
    private DexClassLoader mClassLoader;

    private AssetManager mAM;
    private Resources mResources;
    private Resources.Theme mTheme;

    public interface OnAppLoadedListener {
        void onAppLoaded(App app, boolean loaded);
    }

    public App(Context context, String apkPath) {
        mContext = context;
        mApkPath = apkPath;
    }

    public void load(OnAppLoadedListener listener) {
        new LoadTask(this, listener).execute();
    }

    public void unload() {
        AppManager.remove(this);
    }

    public void start(Context context) {
        Intent intent = new Intent(context, AppActivity.class);
        intent.putExtra(AppActivity.EXTRA_PACKAGE_NAME, getPackageName());
        context.startActivity(intent);
    }

    public Activity createActivity(String className, Activity host) {
        try {
            Class clazz = mMainActivityClass;
            if (className != null) {
                clazz = mClassLoader.loadClass(className);
            }
            Activity activity = (Activity) clazz.newInstance();
            hook(activity, host);
            MethodUtils.invokeMethod(activity, true, "attachBaseContext", host);
            return activity;
        } catch (ReflectiveOperationException e) {
            Log.w(TAG, "bind failed. reason: " + e.getMessage());
        }

        return null;
    }

    public void handleCall(Activity activity, String name) {
        try {
            MethodUtils.invokeMethod(activity, true, name);
        } catch (ReflectiveOperationException e) {
            Log.w(TAG, name + " failed. reason: " + e.getMessage());
        }
    }

    public void handleCall(Activity activity, String name, Bundle savedInstanceState) {
        try {
            MethodUtils.invokeMethod(activity, true, name, new Object[]{savedInstanceState}, new Class[]{Bundle.class});
        } catch (ReflectiveOperationException e) {
            Log.w(TAG, name + " failed. reason: " + e.getMessage());
        }
    }

    public String getPackageName() {
        return mPackageInfo != null ? mPackageInfo.packageName : null;
    }

    public AssetManager getAssets() {
        return mAM;
    }

    public Resources getResources() {
        return mResources;
    }

    public Resources.Theme getTheme() {
        return mTheme;
    }

    public ClassLoader getClassLoader() {
        return mClassLoader;
    }

    private void onAppLoaded() {
        AppManager.put(this);
    }

    private boolean load() {
        return loadPackageInfo() && loadDex() && loadResources();
    }

    private boolean loadPackageInfo() {
        PackageManager pm = mContext.getPackageManager();
        mPackageInfo = pm.getPackageArchiveInfo(mApkPath, PackageManager.GET_ACTIVITIES);
        mMainActivityInfo = getMainActivity();

        return mPackageInfo != null && mMainActivityInfo != null;
    }

    private boolean loadResources() {
        try {
            mAM = AssetManager.class.newInstance();
            MethodUtils.invokeMethod(mAM, "addAssetPath", mApkPath);
            Resources res = mContext.getResources();
            Resources.Theme theme = mContext.getTheme();
            mResources = new Resources(mAM, res.getDisplayMetrics(), res.getConfiguration());
            mTheme = mResources.newTheme();
            mTheme.setTo(theme);
        } catch (ReflectiveOperationException e) {
            Log.e(TAG, "load resources failed. reason: " + e.getMessage());
            return false;
        }

        return true;
    }

    private boolean loadDex() {
        mClassLoader = new DexClassLoader(
                mApkPath,
                getDir("app"),
                getDir("app_lib"),
                mContext.getClassLoader());
        try {
            mMainActivityClass = (Class<Activity>) mClassLoader.loadClass(mMainActivityInfo.name);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "load dex failed. reason: " + e.getMessage());
        }

        return mMainActivityClass != null;
    }

    private void hook(Activity activity, Activity host) throws IllegalAccessException {
        hook(activity, host, "mApplication");
        hook(activity, host, "mFragments");
        hook(activity, host, "mWindow");
        hook(activity, host, "mMainThread");

        // mActivityInfo
        ActivityInfo ai = getActivityInfo(activity.getClass().getName());
        FieldUtils.writeField(activity, "mActivityInfo", ai, true);

        // mTitle
        host.setTitle(loadLabel(ai));
        hook(activity, host, "mTitle");

        // mInstrumentation
        Instrumentation instrumentation = (Instrumentation) FieldUtils.readField(host, "mInstrumentation", true);
        instrumentation = new AppInstrumentation(instrumentation, mPackageInfo.packageName);
        FieldUtils.writeField(activity, "mInstrumentation", instrumentation, true);
    }

    private void hook(Activity activity, Activity host, String fieldName) throws IllegalAccessException {
        Object value = FieldUtils.readField(host, fieldName, true);
        FieldUtils.writeField(activity, fieldName, value, true);
    }

    private ActivityInfo getMainActivity() {
        try {
            @SuppressLint("PrivateApi")
            Class<?> clazz = Class.forName("android.content.pm.PackageParser");
            Object packageParser = ConstructorUtils.invokeConstructor(clazz);
            Object packageObj = MethodUtils.invokeMethod(packageParser, "parsePackage", new Object[]{new File(mApkPath), 0});
            List activities = (List) FieldUtils.readField(packageObj, "activities");
            for (Object data : activities) {
                List<IntentFilter> filters = (List<IntentFilter>) FieldUtils.readField(data, "intents");
                for (IntentFilter filter : filters) {
                    for (int i = 0; i < filter.countActions(); i++) {
                        if (filter.getAction(i).equals(Intent.ACTION_MAIN) &&
                                filter.getCategory(i).equals(Intent.CATEGORY_LAUNCHER)) {
                            return (ActivityInfo) FieldUtils.readField(data, "info");
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            Log.e(TAG, "find main activity failed. reason: " + e.getMessage());
        }

        return null;
    }

    private ActivityInfo getActivityInfo(String className) {
        for (ActivityInfo info : mPackageInfo.activities) {
            if (info.name.equals(className)) {
                return info;
            }
        }
        return null;
    }

    private CharSequence loadLabel(ComponentInfo info) {
        if (info.nonLocalizedLabel != null) {
            return info.nonLocalizedLabel;
        }
        ApplicationInfo ai = info.applicationInfo;
        CharSequence label;
        if (info.labelRes != 0) {
            label = mResources.getText(info.labelRes);
            if (label != null) {
                return label;
            }
        }
        if (ai.nonLocalizedLabel != null) {
            return ai.nonLocalizedLabel;
        }
        if (ai.labelRes != 0) {
            label = mResources.getText(ai.labelRes);
            if (label != null) {
                return label;
            }
        }
        return info.name;
    }

    private String getDir(String name) {
        return mContext.getDir(name, Context.MODE_PRIVATE).getAbsolutePath();
    }

    private static class LoadTask extends AsyncTask<Void, Void, Boolean> {
        private WeakReference<App> mApp;
        private WeakReference<OnAppLoadedListener> mListener;

        public LoadTask(App app, OnAppLoadedListener listener) {
            mApp = new WeakReference<>(app);
            mListener = new WeakReference<>(listener);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            App app = mApp.get();
            return app != null && app.load();
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            App app = mApp.get();

            if (aBoolean && app != null) {
                app.onAppLoaded();
            }

            OnAppLoadedListener listener = mListener.get();
            if (listener != null) {
                listener.onAppLoaded(app, aBoolean);
            }
        }
    }
}
