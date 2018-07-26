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

package org.arpnetwork.arp.container.proxy;

import android.content.ComponentName;

import org.arpnetwork.arp.container.App;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class IPackageManagerHandler implements InvocationHandler {
    private Object mBase;
    private WeakReference<App> mApp;

    public IPackageManagerHandler(Object base, App app) {
        mBase = base;
        mApp = new WeakReference<>(app);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().equals("getActivityInfo")) {
            ComponentName componentName = (ComponentName) args[0];

            App app = mApp.get();
            if (app != null) {
                return app.getActivityInfo(componentName.getClassName());
            }
        }

        return method.invoke(mBase, args);
    }
}
