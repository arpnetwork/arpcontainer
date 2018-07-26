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
import android.util.Log;

import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

public class App {
    private static final String TAG = "App";

    private Context mContext;
    private String mApkPath;
    private File mOptimizedDirectory;

    private PackageInfo mPackageInfo;
    private ActivityInfo mMainActivityInfo;

    private AssetManager mAM;
    private Resources mResources;

    private LoadTask mLoadTask;
    private boolean mLoaded;

    public interface OnAppLoadedListener {
        void onAppLoaded(App app, boolean loaded);
    }

    public App(Context context, String apkPath) {
        mContext = context;
        mApkPath = apkPath;
    }

    public void load(OnAppLoadedListener listener) {
        if (mLoaded || mLoadTask != null) {
            throw new IllegalStateException();
        }

        mLoadTask = new LoadTask(this, listener);
        mLoadTask.execute();
    }

    public void unload() {
        if (mLoaded) {
            AppManager.remove(this);
            mLoaded = false;
        }
    }

    public void cancel() {
        if (mLoadTask != null) {
            mLoadTask.cancel(true);
            mLoadTask = null;
        } else if (mLoaded) {
            unload();
        }
    }

    public void start(Context context) {
        try {
            Class<?> clazz = Class.forName(mMainActivityInfo.name);
            Intent intent = new Intent(context, clazz);
            context.startActivity(intent);
        } catch (ClassNotFoundException e) {
        }
    }

    public String getApkPath() {
        return mApkPath;
    }

    public File getOptimizedDirectory() {
        return mOptimizedDirectory;
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

    public boolean isLoading() {
        return mLoadTask != null;
    }

    public boolean isLoaded() {
        return mLoaded;
    }

    public ActivityInfo getActivityInfo(String className) {
        for (ActivityInfo info : mPackageInfo.activities) {
            if (info.name.equals(className)) {
                return info;
            }
        }
        return null;
    }

    public CharSequence loadLabel(ComponentInfo info) {
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

    private void onAppLoaded() {
        mLoadTask = null;
        AppManager.put(this);
        mLoaded = true;

        mOptimizedDirectory = mContext.getDir(getPackageName(), Context.MODE_PRIVATE);
    }

    private boolean load() {
        return loadPackageInfo() && loadResources() && Hook.init(mContext, this);
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
            mResources = new Resources(mAM, res.getDisplayMetrics(), res.getConfiguration());
        } catch (ReflectiveOperationException e) {
            Log.e(TAG, "load resources failed. reason: " + e.getMessage());

            return false;
        }

        return true;
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

    private static class LoadTask extends AsyncTask<Void, Void, Boolean> {
        private WeakReference<App> mApp;
        private OnAppLoadedListener mListener;

        public LoadTask(App app, OnAppLoadedListener listener) {
            mApp = new WeakReference<>(app);
            mListener = listener;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            App app = mApp.get();
            return app != null && app.load();
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            if (!isCancelled()) {
                App app = mApp.get();
                if (app != null) {
                    if (aBoolean) {
                        app.onAppLoaded();
                    }

                    mListener.onAppLoaded(app, aBoolean);
                }
            }
        }
    }
}
