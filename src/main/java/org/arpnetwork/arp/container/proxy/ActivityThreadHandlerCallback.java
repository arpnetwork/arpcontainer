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

import android.content.Intent;
import android.os.Handler;
import android.os.Message;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.arpnetwork.arp.container.Hook;

public class ActivityThreadHandlerCallback implements Handler.Callback {
    private static final int LAUNCH_ACTIVITY = 100;

    private Handler mBase;

    public ActivityThreadHandlerCallback(Handler base) {
        mBase = base;
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == LAUNCH_ACTIVITY) {
            try {
                // Replace target intent
                Intent intent = (Intent) FieldUtils.readField(msg.obj, "intent", true);
                Intent target = intent.getParcelableExtra(Hook.EXTRA_TARGET_INTENT);
                intent.setComponent(target.getComponent());
            } catch (IllegalAccessException e) {
            }
        }

        mBase.handleMessage(msg);

        return true;
    }
}
