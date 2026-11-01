package org.eu.suisei.wallc;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Application-scoped Root tasks survive Activity recreation without duplicate writes. */
public final class WallpaperController {
    private final Context context;
    private final RootExecutor root;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Set<Runnable> listeners = new HashSet<>();
    private boolean busy;
    private String status = "Root access required", message = "Authorize Wallc in your root manager.";
    private Bundle diagnostics = new Bundle();
    private long completionId;
    private int activeTarget, completedTarget;
    private boolean lastSucceeded;
    public WallpaperController(Context context) {
        this.context = context.getApplicationContext(); root = new RootExecutor(this.context);
    }
    public void addListener(Runnable listener) { listeners.add(listener); listener.run(); }
    public void removeListener(Runnable listener) { listeners.remove(listener); }
    public boolean ready() { return !busy && diagnostics.getBoolean("ok"); }
    public boolean busy() { return busy; }
    public String status() { return status; }
    public String message() { return message; }
    public Bundle diagnostics() { return new Bundle(diagnostics); }
    public long completionId() { return completionId; }
    public int activeTarget() { return activeTarget; }
    public int completedTarget() { return completedTarget; }
    public boolean lastSucceeded() { return lastSucceeded; }
    private void changed() { for (Runnable listener : new HashSet<>(listeners)) listener.run(); }

    public void authorize() {
        if (busy) return;
        busy = true;
        activeTarget = 0;
        status = "Requesting root access";
        message = "Waiting for root authorization and wallpaper capability checks.";
        changed();
        worker.execute(() -> {
            Bundle result = root.run(null, 0);
            main.post(() -> {
                busy = false; diagnostics = result;
                status = result.getBoolean("ok") ? "Ready" : result.getBoolean("rootGranted")
                        ? "Wallpaper service unavailable" : "Root authorization or startup failed";
                message = result.getBoolean("ok") ? "Root access granted; wallpaper service ready."
                        : result.getString("error", "Unable to connect to the wallpaper service");
                lastSucceeded = result.getBoolean("ok"); completedTarget = 0; completionId++;
                changed();
            });
        });
    }
    public void apply(Uri uri, int target, boolean crop, int width, int height) {
        WallpaperTarget.requireSingle(target);
        if (!ready() || uri == null) return;
        busy = true;
        activeTarget = target;
        status = "Applying " + WallpaperTarget.label(target).toLowerCase(java.util.Locale.ROOT) + " wallpaper";
        message = "Preparing the image and submitting it to the wallpaper service.";
        changed();
        worker.execute(() -> {
            File file = null; Bundle result;
            try {
                file = ImagePreparer.prepare(context, uri, crop, width, height);
                result = root.run(file, target);
            } catch (Exception | OutOfMemoryError e) {
                result = new Bundle(); result.putString("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally { if (file != null) file.delete(); }
            Bundle finished = result;
            main.post(() -> {
                busy = false;
                if (finished.getBoolean("ok")) {
                    diagnostics.putAll(finished);
                    status = WallpaperTarget.label(target) + " wallpaper submitted";
                    message = "Wallpaper ID: " + finished.getInt("wallpaperId") + ".\n"
                            + finished.getString("warning", "");
                } else {
                    boolean wasReady = diagnostics.getBoolean("ok");
                    diagnostics.putAll(finished); diagnostics.putBoolean("ok", wasReady);
                    status = "Wallpaper update failed"; message = finished.getString("error", "Unknown error");
                }
                lastSucceeded = finished.getBoolean("ok"); completedTarget = target; completionId++;
                changed();
            });
        });
    }
}
