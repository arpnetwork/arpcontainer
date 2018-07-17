package org.arpnetwork.arp.container;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
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

    private Activity mActivity;
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

    public boolean bind(Activity host) {
        host.setTitle(mResources.getString(mPackageInfo.applicationInfo.labelRes));
        try {
            mActivity = mMainActivityClass.newInstance();
            hook(host);
            MethodUtils.invokeMethod(mActivity, true, "attachBaseContext", host);
        } catch (ReflectiveOperationException e) {
            Log.w(TAG, "bind failed. reason: " + e.getMessage());
            return false;
        }

        return true;
    }

    public void handleCall(String name) {
        try {
            MethodUtils.invokeMethod(mActivity, true, name);
        } catch (ReflectiveOperationException e) {
            Log.w(TAG, name + " failed. reason: " + e.getMessage());
        }
    }

    public void handleCall(String name, Bundle savedInstanceState) {
        try {
            MethodUtils.invokeMethod(mActivity, true, name, new Object[]{savedInstanceState}, new Class[]{Bundle.class});
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

    private void hook(Activity host) throws IllegalAccessException {
        hook(host, "mActivityInfo");
        hook(host, "mApplication");
        hook(host, "mFragments");
        hook(host, "mTitle");
        hook(host, "mWindow");
    }

    private void hook(Activity host, String fieldName) throws IllegalAccessException {
        Object value = FieldUtils.readField(host, fieldName, true);
        FieldUtils.writeField(mActivity, fieldName, value, true);
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
