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
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;

public class AppActivity extends Activity {
    public static final String EXTRA_PACKAGE_NAME = "PACKAGE_NAME";
    public static final String EXTRA_CLASS_NAME = "CLASS_NAME";

    private App mApp;
    private Activity mTarget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String packageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        String className = getIntent().getStringExtra(EXTRA_CLASS_NAME);
        App app = AppManager.get(packageName);
        mTarget = app.createActivity(className, this);

        mApp = app;
        handleCall("onCreate", savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();

        handleCall("onStart");
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        handleCall("onRestart");
    }

    @Override
    protected void onResume() {
        super.onResume();

        handleCall("onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();

        handleCall("onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();

        handleCall("onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        handleCall("onDestroy");
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

    private void handleCall(String name) {
        mApp.handleCall(mTarget, name);
    }

    private void handleCall(String name, Bundle savedInstanceState) {
        mApp.handleCall(mTarget, name, savedInstanceState);
    }
}
