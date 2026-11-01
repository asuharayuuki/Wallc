package org.eu.suisei.wallc;

import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Direct su access: each command has its own process, streams, and deadline. */
public final class RootExecutor {
    private final String apk, launcher;
    private final int userId;
    private final ExecutorService io = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "wallc-root-io"); thread.setDaemon(true); return thread;
    });
    public RootExecutor(Context context) {
        apk = context.getApplicationInfo().sourceDir;
        launcher = new File(context.getApplicationInfo().nativeLibraryDir, "libwallc_launcher.so").getAbsolutePath();
        userId = Process.myUid() / 100000;
    }
    public Bundle run(File image, int target) {
        Bundle result = new Bundle();
        java.lang.Process process = null;
        Future<String> output = null;
        Future<?> input = null;
        try {
            if (!new File(launcher).isFile()) throw new IOException("Native root launcher is missing; reinstall the complete APK");
            String command = RootProtocol.command(launcher, apk, userId, image == null ? "diagnose" : "set", target);
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            java.lang.Process child = process;
            output = io.submit(() -> {
                StringBuilder text = new StringBuilder();
                try (InputStreamReader reader = new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8)) {
                    char[] buffer = new char[2048]; int count;
                    while ((count = reader.read(buffer)) != -1) {
                        text.append(buffer, 0, count);
                        if (text.length() > 65536) text.delete(0, text.length() - 65536);
                    }
                }
                return text.toString();
            });
            input = io.submit(() -> {
                try (java.io.OutputStream stream = child.getOutputStream()) {
                    if (image != null) try (FileInputStream source = new FileInputStream(image)) {
                        ImageTransport.send(source, image.length(), stream);
                    }
                }
                return null;
            });
            if (!process.waitFor(60, TimeUnit.SECONDS)) throw new IOException("Root authorization or wallpaper operation timed out");
            String text = output.get(3, TimeUnit.SECONDS);
            result.putBoolean("rootGranted", RootProtocol.rootGranted(text));
            result.putInt("exitCode", process.exitValue());
            String json = RootProtocol.resultJson(text);
            if (json != null) {
                JSONObject object = new JSONObject(json);
                for (Iterator<String> keys = object.keys(); keys.hasNext();) {
                    String key = keys.next(); Object value = object.get(key);
                    if (value instanceof Boolean) result.putBoolean(key, (Boolean) value);
                    else if (value instanceof Number) result.putInt(key, ((Number) value).intValue());
                    else result.putString(key, value.toString());
                }
            } else {
                result.putString("stage", "root-launch");
                result.putString("error", "Root worker failed to start (exit code " + process.exitValue() + ").\n"
                        + (text.trim().isEmpty() ? "Authorize Wallc in your root manager." : text.trim()));
            }
        } catch (Exception e) {
            result.putBoolean("ok", false);
            result.putString("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (process != null) {
                process.destroyForcibly();
                try { process.getOutputStream().close(); } catch (IOException ignored) {}
                try { process.getInputStream().close(); } catch (IOException ignored) {}
            }
            if (output != null) output.cancel(true);
            if (input != null) input.cancel(true);
        }
        return result;
    }
}
