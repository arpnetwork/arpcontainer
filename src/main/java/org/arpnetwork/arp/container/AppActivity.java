package org.arpnetwork.arp.container;

import android.app.Activity;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;

public class AppActivity extends Activity {
    public static final String EXTRA_PACKAGE_NAME = "PACKAGE_NAME";

    private App mApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String packageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        App app = AppManager.get(packageName);
        app.bind(this);

        mApp = app;
        mApp.handleCall("onCreate", savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();

        mApp.handleCall("onStart");
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        mApp.handleCall("onRestart");
    }

    @Override
    protected void onResume() {
        super.onResume();

        mApp.handleCall("onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();

        mApp.handleCall("onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();

        mApp.handleCall("onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mApp.handleCall("onDestroy");
        mApp.unload();
        mApp = null;
        System.gc();
    }

    @Override
    public ClassLoader getClassLoader() {
        return mApp != null ? mApp.getClassLoader() : super.getClassLoader();
    }

    @Override
    public AssetManager getAssets() {
        return mApp != null ? mApp.getAssets() : super.getAssets();
    }

    @Override
    public Resources getResources() {
        return mApp != null ? mApp.getResources() : super.getResources();
    }

    @Override
    public Resources.Theme getTheme() {
        return mApp != null ? mApp.getTheme() : super.getTheme();
    }
}
