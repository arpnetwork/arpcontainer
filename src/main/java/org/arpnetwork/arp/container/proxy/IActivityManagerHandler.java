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
import android.content.Intent;

import org.arpnetwork.arp.container.AppActivity;
import org.arpnetwork.arp.container.Hook;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class IActivityManagerHandler implements InvocationHandler {
    private Object mBase;

    public IActivityManagerHandler(Object base) {
        mBase = base;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().equals("startActivity")) {
            int index = 0;
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Intent) {
                    index = i;
                    break;
                }
            }
            Intent raw = (Intent) args[index];

            Intent newIntent = new Intent();
            String pkg = raw.getComponent().getPackageName();
            ComponentName componentName = new ComponentName(pkg, AppActivity.class.getCanonicalName());
            newIntent.setComponent(componentName);
            newIntent.putExtra(Hook.EXTRA_TARGET_INTENT, raw);
            args[index] = newIntent;
        }

        return method.invoke(mBase, args);
    }
}
