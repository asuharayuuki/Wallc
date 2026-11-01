package org.eu.suisei.wallc;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Stage the complete image before calling WallpaperManager; reject truncated stdin. */
public final class ImageTransport {
    private static final long MAX_BYTES = 96L * 1024 * 1024;
    private ImageTransport() {}
    public static void send(InputStream source, long length, OutputStream destination) throws IOException {
        checkLength(length);
        new DataOutputStream(destination).writeLong(length);
        copy(source, destination, length);
    }
    public static void receive(InputStream source, OutputStream destination) throws IOException {
        long length = new DataInputStream(source).readLong();
        checkLength(length); copy(source, destination, length);
    }
    private static void checkLength(long length) throws IOException {
        if (length <= 0 || length > MAX_BYTES) throw new IOException("Image payload length is invalid or exceeds 96 MiB");
    }
    private static void copy(InputStream source, OutputStream destination, long length) throws IOException {
        byte[] buffer = new byte[16384];
        long remaining = length;
        while (remaining > 0) {
            int count = source.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (count < 0) throw new EOFException("Image transfer ended before completion; wallpaper was not submitted");
            destination.write(buffer, 0, count); remaining -= count;
        }
    }
}
