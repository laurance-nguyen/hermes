package net.neuralmetrics.projecthermes.utils;

import android.content.Context;
import android.util.DisplayMetrics;

/**
 * Created by hoang on 14/06/2017.
 */

public class DisplayUnitConverter {
    public static int convertDpToPx(float dp, Context context)
    {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float fpixels = metrics.density * dp;
        return (int) (fpixels + 0.5f);
    }
}
