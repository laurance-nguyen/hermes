package net.neuralmetrics.projecthermes.utils;

import android.app.ActivityManager;
import android.content.Context;

/**
 * Created by hoang on 22/06/2017.
 */

public class ServiceRunningUtils {
    public static boolean isMyServiceRunning(Class<?> serviceClass, Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
