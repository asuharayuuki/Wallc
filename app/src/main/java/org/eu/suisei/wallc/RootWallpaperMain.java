package org.eu.suisei.wallc;

import android.app.WallpaperManager;
import android.content.Context;
import android.os.Binder;
import android.os.Build;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.system.Os;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Short-lived worker; its native root launcher establishes UID before ART starts. */
public final class RootWallpaperMain {
    private RootWallpaperMain() {}
    public static void main(String[] args) {
        JSONObject result = new JSONObject();
        String stage = "arguments";
        boolean written = false;
        File stagedImage = null;
        try {
            result.put("rootGranted", true);
            result.put("uid", Process.myUid()); result.put("effectiveUid", Os.geteuid());
            result.put("binderUid", Binder.getCallingUid()); result.put("sdk", Build.VERSION.SDK_INT);
            if (args.length != 3) throw new IllegalArgumentException("Incomplete wallpaper worker arguments");
            int userId = Integer.parseInt(args[0]), target = Integer.parseInt(args[2]);
            if (userId < 0 || userId > 21474) throw new IllegalArgumentException("Invalid Android user ID");
            if (Process.myUid() != 2000 || Os.geteuid() != 2000 || Binder.getCallingUid() != 2000)
                throw new SecurityException("Wallpaper worker credentials were not initialized correctly");
            boolean setting = "set".equals(args[1]);
            if (setting) WallpaperTarget.requireSingle(target);
            else if (!"diagnose".equals(args[1]) || target != 0) throw new IllegalArgumentException("Invalid wallpaper operation");
            stage = "framework";
            Looper.prepareMainLooper();
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = activityThread.getMethod("systemMain").invoke(null);
            Context system = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
            stage = "user-context";
            Context packageContext = (Context) Context.class.getMethod("createPackageContextAsUser", String.class, int.class, UserHandle.class)
                    .invoke(system, "com.android.shell", Context.CONTEXT_IGNORE_SECURITY,
                            UserHandle.getUserHandleForUid(userId * 100000 + 2000));
            result.put("userId", userId);
            // A derived package Context inherits the parent system Context's AppOps
            // package. Build an independent app Context before asking for any services.
            stage = "app-context";
            MethodInfo attribution = new MethodInfo();
            result.put("derivedOpPackage", attribution.opPackage(packageContext));
            Context shell = (Context) AppContextFactory.create(packageContext, thread, Class.forName("android.app.ContextImpl"));
            if (userId != Process.myUid() / 100000) {
                shell = (Context) Context.class.getMethod("createContextAsUser", UserHandle.class, int.class)
                        .invoke(shell, UserHandle.getUserHandleForUid(userId * 100000 + 2000), 0);
            }
            String packageName = shell.getPackageName();
            String opPackage = attribution.opPackage(shell);
            int packageUid = system.getPackageManager().getApplicationInfo(packageName, 0).uid;
            result.put("contextPackage", packageName);
            result.put("callerPackage", opPackage);
            result.put("packageUid", packageUid);
            result.put("uidPackages", java.util.Arrays.toString(system.getPackageManager().getPackagesForUid(Process.myUid())));
            stage = "attribution-validation";
            if (!"com.android.shell".equals(packageName) || !packageName.equals(opPackage) || packageUid != Process.myUid())
                throw new IllegalStateException("Wallpaper attribution mismatch: package=" + packageName
                        + ", opPackage=" + opPackage + ", packageUid=" + packageUid + ", processUid=" + Process.myUid());
            WallpaperManager manager = WallpaperManager.getInstance(shell);
            try {
                result.put("oneUi", Class.forName("android.os.SystemProperties").getMethod("get", String.class, String.class)
                        .invoke(null, "ro.build.version.oneui", "unknown"));
            } catch (Exception ignored) { result.put("oneUi", "unknown"); }
            stage = "capabilities";
            boolean supported = manager.isWallpaperSupported(); result.put("supported", supported);
            boolean allowed = manager.isSetWallpaperAllowed(); result.put("allowed", allowed);
            if (!supported) throw new IOException("Wallpapers are not supported for this user");
            if (!allowed) throw new SecurityException("Wallpaper changes are prohibited by system policy");
            if (setting) {
                stage = "image-input";
                stagedImage = File.createTempFile("wallc-root-", ".png", new File("/data/local/tmp"));
                try (FileOutputStream image = new FileOutputStream(stagedImage)) {
                    ImageTransport.receive(System.in, image);
                }
                int other = target == WallpaperTarget.HOME ? WallpaperManager.FLAG_LOCK : WallpaperManager.FLAG_SYSTEM;
                int before = manager.getWallpaperId(other);
                stage = "write";
                int id;
                try (FileInputStream image = new FileInputStream(stagedImage)) {
                    id = manager.setStream(image, null, false, target);
                }
                if (id <= 0) throw new IOException("Wallpaper service did not return a valid wallpaper ID");
                written = true;
                result.put("target", target); result.put("wallpaperId", id);
                stage = "readback";
                try {
                    readIds(result, manager);
                    if (before > 0 && manager.getWallpaperId(other) != before)
                        result.put("warning", "The other wallpaper ID also changed; verify both wallpaper targets.\n");
                } catch (Exception e) { result.put("warning", "Wallpaper submitted; updated wallpaper IDs could not be read.\n"); }
            } else { stage = "readback"; readIds(result, manager); }
            result.put("stage", "complete"); result.put("ok", true);
        } catch (Throwable e) {
            while (e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null) e = e.getCause();
            try {
                result.put("ok", written); result.put("stage", stage);
                result.put(written ? "warning" : "error", e.getClass().getSimpleName() + ": " + e.getMessage());
            } catch (Exception ignored) {}
        } finally { if (stagedImage != null) stagedImage.delete(); }
        System.out.println(RootProtocol.RESULT + result); System.out.flush();
        System.exit(result.optBoolean("ok") ? 0 : 1);
    }
    private static void readIds(JSONObject result, WallpaperManager manager) throws Exception {
        result.put("homeId", manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM));
        result.put("lockId", manager.getWallpaperId(WallpaperManager.FLAG_LOCK));
    }

    private static final class MethodInfo {
        private final java.lang.reflect.Method getOpPackageName;
        MethodInfo() throws Exception { getOpPackageName = Context.class.getMethod("getOpPackageName"); }
        String opPackage(Context context) throws Exception {
            Object name = getOpPackageName.invoke(context);
            return name == null ? "<null>" : name.toString();
        }
    }
}
