package org.arpnetwork.arp.container;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import org.apache.commons.lang3.reflect.MethodUtils;

public class AppInstrumentation extends Instrumentation {
    private static final String TAG = AppInstrumentation.class.getSimpleName();

    Instrumentation mHost;
    String mPackageName;

    public AppInstrumentation(Instrumentation host, String packageName) {
        mHost = host;
        mPackageName = packageName;
    }

    /**
     * Execute a startActivity call made by the application.  The default
     * implementation takes care of updating any active {@link ActivityMonitor}
     * objects and dispatches this call to the system activity manager; you can
     * override this to watch for the application to start an activity, and
     * modify what happens when it does.
     * <p>
     * <p>This method returns an {@link ActivityResult} object, which you can
     * use when intercepting application calls to avoid performing the start
     * activity action but still return the result the application is
     * expecting.  To do this, override this method to catch the call to start
     * activity so that it returns a new ActivityResult containing the results
     * you would like the application to see, and don't call up to the super
     * class.  Note that an application is only expecting a result if
     * <var>requestCode</var> is &gt;= 0.
     * <p>
     * <p>This method throws {@link android.content.ActivityNotFoundException}
     * if there was no Activity found to run the given Intent.
     *
     * @param who           The Context from which the activity is being started.
     * @param contextThread The main thread of the Context from which the activity
     *                      is being started.
     * @param token         Internal token identifying to the system who is starting
     *                      the activity; may be null.
     * @param target        Which activity is performing the start (and thus receiving
     *                      any result); may be null if this call is not being made
     *                      from an activity.
     * @param intent        The actual Intent to start.
     * @param requestCode   Identifier for this request's result; less than zero
     *                      if the caller is not expecting a result.
     * @param options       Addition options.
     * @return To force the return of a particular result, return an
     * ActivityResult object containing the desired data; otherwise
     * return null.  The default implementation always returns null.
     * @throws android.content.ActivityNotFoundException
     * @see Activity#startActivity(Intent)
     * @see Activity#startActivityForResult(Intent, int)
     * @see Activity#startActivityFromChild
     */
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle options) {

        ComponentName componentName = intent.getComponent();

        intent = new Intent();
        intent.setClass(who, AppActivity.class);
        intent.putExtra(AppActivity.EXTRA_PACKAGE_NAME, mPackageName);
        intent.putExtra(AppActivity.EXTRA_CLASS_NAME, componentName.getClassName());
        try {
            return (ActivityResult) MethodUtils.invokeMethod(mHost, "execStartActivity", who, contextThread, token, target, intent, requestCode, options);
        } catch (ReflectiveOperationException e) {
            Log.w(TAG, "execStartActivity failed. reason: " + e.getMessage());
        }

        return null;
    }
}
