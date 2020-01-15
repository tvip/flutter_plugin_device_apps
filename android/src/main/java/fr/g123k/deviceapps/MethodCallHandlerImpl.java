package fr.g123k.deviceapps;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

public class DeviceAppsMethodCallHandler implements MethodChannel.MethodCallHandler {
    private final int SYSTEM_APP_MASK = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
    MethodChannel channel;

    private Activity activity;
    private final AsyncWork asyncWork;

    public DeviceAppsMethodCallHandler() {
        this.asyncWork = new AsyncWork();
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    void startListening(BinaryMessenger messenger) {
        if (channel != null) {
            Log.wtf(TAG, "Setting a method call handler before the last was disposed.");
            stopListening();
        }

        channel = new MethodChannel(messenger, "g123k/device_apps");
        channel.setMethodCallHandler(this);
    }

    /**
     * Clears this instance from listening to method calls.
     *
     * <p>Does nothing if {@link #startListening} hasn't been called, or if we're already stopped.
     */
    void stopListening() {
        if (channel == null) {
            Log.d(TAG, "Tried to stop listening when no MethodChannel had been initialized.");
            return;
        }

        channel.setMethodCallHandler(null);
        channel = null;
    }

    @Override
    public void onMethodCall(MethodCall call, final MethodChannel.Result result) {
        if (activity == null) {
            result.error("NO_ACTIVITY", call.method + " requires a foreground activity.", null);
            return;
        }
        switch (call.method) {
            case "getInstalledApps":
                boolean systemApps = call.hasArgument("system_apps") && (Boolean) (call.argument("system_apps"));
                boolean includeAppIcons = call.hasArgument("include_app_icons") && (Boolean) (call.argument("include_app_icons"));
                boolean includeAppBanners = call.hasArgument("include_app_banners") && (Boolean) (call.argument("include_app_banners"));
                boolean onlyAppsWithLaunchIntent = call.hasArgument("only_apps_with_launch_intent") && (Boolean) (call.argument("only_apps_with_launch_intent"));
                fetchInstalledApps(systemApps, includeAppIcons, includeAppBanners, onlyAppsWithLaunchIntent, new InstalledAppsCallback() {
                    @Override
                    public void onInstalledAppsListAvailable(final List<Map<String, Object>> apps) {
                        if (!activity.isFinishing()) {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    result.success(apps);
                                }
                            });
                        }
                    }
                });
                break;
            case "getApp":
                if (!call.hasArgument("package_name") || TextUtils.isEmpty(call.argument("package_name").toString())) {
                    result.error("ERROR", "Empty or null package name", null);
                } else {
                    String packageName = call.argument("package_name").toString();
                    boolean includeAppIcon = call.hasArgument("include_app_icon") && (Boolean) (call.argument("include_app_icon"));
                    boolean includeAppBanner = call.hasArgument("include_app_banner") && (Boolean) (call.argument("include_app_banner"));
                    result.success(getApp(packageName, includeAppIcon, includeAppBanner));
                }
                break;
            case "isAppInstalled":
                if (!call.hasArgument("package_name") || TextUtils.isEmpty(call.argument("package_name").toString())) {
                    result.error("ERROR", "Empty or null package name", null);
                } else {
                    String packageName = call.argument("package_name").toString();
                    result.success(isAppInstalled(packageName));
                }
                break;
            case "openApp":
                if (!call.hasArgument("package_name") || TextUtils.isEmpty(call.argument("package_name").toString())) {
                    result.error("ERROR", "Empty or null package name", null);
                } else {
                    String packageName = call.argument("package_name").toString();
                    Object classNameArg = call.argument("class_name");
                    String className = null;
                    if (classNameArg != null) {
                        className = classNameArg.toString();
                    }
                    result.success(openApp(packageName, className));
                }
                break;
            default:
                result.notImplemented();
        }
    }

    private void fetchInstalledApps(final boolean includeSystemApps, final boolean includeAppIcons, final boolean includeAppBanners, final boolean onlyAppsWithLaunchIntent, final InstalledAppsCallback callback) {
        asyncWork.run(new Runnable() {

            @Override
            public void run() {
                List<Map<String, Object>> installedApps = getInstalledApps(includeSystemApps, includeAppIcons, includeAppBanners, onlyAppsWithLaunchIntent);

                if (callback != null) {
                    callback.onInstalledAppsListAvailable(installedApps);
                }
            }

        });
    }

    private List<Map<String, Object>> getInstalledApps(boolean includeSystemApps, boolean includeAppIcons, boolean includeAppBanners, boolean onlyAppsWithLaunchIntent) {
        PackageManager packageManager = activity.getPackageManager();
        List<PackageInfo> apps = packageManager.getInstalledPackages(0);
        List<Map<String, Object>> installedApps = new ArrayList<>(apps.size());

        for (PackageInfo pInfo : apps) {
            if (!includeSystemApps && isSystemApp(pInfo)) {
                continue;
            }
            if (onlyAppsWithLaunchIntent
                    && packageManager.getLaunchIntentForPackage(pInfo.packageName) == null
                    && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                    && packageManager.getLeanbackLaunchIntentForPackage(pInfo.packageName) == null)) {
                continue;
            }

            Map<String, Object> map = getAppData(packageManager, pInfo, includeAppIcons, includeAppBanners);
            installedApps.add(map);
        }

        return installedApps;
    }

    private boolean openApp(String packageName, String className) {
        Intent intent = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            intent = activity.getPackageManager().getLeanbackLaunchIntentForPackage(packageName);
        }
        if (intent == null) {
            intent = activity.getPackageManager().getLaunchIntentForPackage(packageName);
        }
        if (intent != null) {
            // null pointer check in case package name was not found
            if (className != null) {
                intent.setClassName(packageName, className);
            }
            System.out.println("start activity with packageName: " + packageName + ", and className: " + className);
            activity.startActivity(intent);
            return true;
        }
        System.out.println("Not found intent for packageName: " + packageName);
        return false;
    }

    private boolean isSystemApp(PackageInfo pInfo) {
        return (pInfo.applicationInfo.flags & SYSTEM_APP_MASK) != 0;
    }

    private boolean isAppInstalled(String packageName) {
        try {
            activity.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private Map<String, Object> getApp(String packageName, boolean includeAppIcon, boolean includeAppBanner) {
        try {
            PackageManager packageManager = activity.getPackageManager();
            return getAppData(packageManager, packageManager.getPackageInfo(packageName, 0), includeAppIcon, includeAppBanner);
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    private Map<String, Object> getAppData(PackageManager packageManager, PackageInfo pInfo, boolean includeAppIcon, boolean includeAppBanners) {
        Map<String, Object> map = new HashMap<>();
        map.put("app_name", pInfo.applicationInfo.loadLabel(packageManager).toString());
        map.put("apk_file_path", pInfo.applicationInfo.sourceDir);
        map.put("package_name", pInfo.packageName);
        map.put("version_code", pInfo.versionCode);
        map.put("version_name", pInfo.versionName);
        map.put("data_dir", pInfo.applicationInfo.dataDir);
        map.put("system_app", isSystemApp(pInfo));
        map.put("install_time", pInfo.firstInstallTime);
        map.put("update_time", pInfo.lastUpdateTime);

        if (includeAppIcon) {
            try {
                Drawable icon = packageManager.getApplicationIcon(pInfo.packageName);
                byte[] image = convertToBytes(getBitmapFromDrawable(icon), Bitmap.CompressFormat.PNG, 100);
                map.put("app_icon", image);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }

        if (includeAppBanners && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT_WATCH) {
            try {
                Drawable banner = packageManager.getApplicationBanner(pInfo.packageName);
                if (banner != null) {
                    byte[] image = convertToBytes(getBitmapFromDrawable(banner), Bitmap.CompressFormat.PNG, 100);
                    map.put("app_banner", image);
                }
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }

        return map;
    }

    private byte[] convertToBytes(Bitmap image, Bitmap.CompressFormat compressFormat, int quality) {
        ByteArrayOutputStream byteArrayOS = new ByteArrayOutputStream();
        image.compress(compressFormat, quality, byteArrayOS);
        return byteArrayOS.toByteArray();
    }

    private Bitmap getBitmapFromDrawable(Drawable drawable) {
        final Bitmap bmp = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bmp);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bmp;
    }
}
