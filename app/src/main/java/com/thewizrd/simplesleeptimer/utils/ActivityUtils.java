package com.thewizrd.simplesleeptimer.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;

import androidx.annotation.AnyRes;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

public class ActivityUtils {
    public static float dpToPx(@NonNull Context context, float valueInDp) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valueInDp, metrics);
    }

    public static boolean isLargeTablet(@NonNull Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    public static boolean isXLargeTablet(@NonNull Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    public static boolean isSmallestWidth(@NonNull Context context, int swdp) {
        return (context.getResources().getConfiguration().smallestScreenWidthDp) >= swdp;
    }

    public static int getOrientation(@NonNull Context context) {
        return (context.getResources().getConfiguration().orientation);
    }

    public static void setStatusBarColor(@NonNull Window window, @ColorInt int color, boolean setColors) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            boolean isLightStatusBar = color != Color.TRANSPARENT && ColorsUtils.isSuperLight(color);
            boolean statBarProtected = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) || !isLightStatusBar;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (isLightStatusBar) {
                    window.getDecorView().setSystemUiVisibility(
                            window.getDecorView().getSystemUiVisibility()
                                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                } else {
                    window.getDecorView().setSystemUiVisibility(
                            window.getDecorView().getSystemUiVisibility()
                                    & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                }
            }

            window.setStatusBarColor((setColors ?
                    (statBarProtected ? color : ColorUtils.blendARGB(color, Color.BLACK, 0.25f))
                    : Color.TRANSPARENT));
        }
    }

    public static void setNavBarColor(@NonNull Window window, @ColorInt int color, boolean setColors) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            boolean isLightNavBar = color != Color.TRANSPARENT && ColorsUtils.isSuperLight(color);
            boolean navBarProtected = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) || !isLightNavBar;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (isLightNavBar) {
                    window.getDecorView().setSystemUiVisibility(
                            window.getDecorView().getSystemUiVisibility()
                                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                } else {
                    window.getDecorView().setSystemUiVisibility(
                            window.getDecorView().getSystemUiVisibility()
                                    & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                }
            }

            window.setNavigationBarColor((setColors ?
                    (navBarProtected ? color : ColorUtils.blendARGB(color, Color.BLACK, 0.25f))
                    : Color.TRANSPARENT));
        }
    }

    public static int getAttrDimension(@NonNull Context activityContext, @AttrRes int resId) {
        final TypedValue value = new TypedValue();
        activityContext.getTheme().resolveAttribute(resId, value, true);

        return TypedValue.complexToDimensionPixelSize(value.data, activityContext.getResources().getDisplayMetrics());
    }

    public static int getAttrValue(@NonNull Context activityContext, @AttrRes int resId) {
        final TypedValue value = new TypedValue();
        activityContext.getTheme().resolveAttribute(resId, value, true);

        return value.data;
    }

    @ColorInt
    public static int getColor(@NonNull Context activityContext, @AttrRes int resId) {
        final TypedArray array = activityContext.getTheme().obtainStyledAttributes(new int[]{resId});
        @ColorInt int color = array.getColor(0, 0);
        array.recycle();

        return color;
    }

    @AnyRes
    public static int getResourceId(@NonNull Context activityContext, @AttrRes int resId) {
        final TypedArray array = activityContext.getTheme().obtainStyledAttributes(new int[]{resId});
        int resourceId = array.getResourceId(0, 0);
        array.recycle();

        return resourceId;
    }
}