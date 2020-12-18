package com.aurora.store.util;

import android.content.Context;
import android.os.Build;
import android.widget.ImageView;

public class CompatUtil {

    public static void setImageVector(Context context, ImageView img, int resId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            img.setImageDrawable(context.getDrawable(resId));
        } else {
            img.setImageResource(resId);
        }
    }
}
