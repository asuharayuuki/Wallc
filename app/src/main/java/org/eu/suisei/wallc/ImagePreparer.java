package org.eu.suisei.wallc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class ImagePreparer {
    private ImagePreparer() {}

    public static Bitmap decode(Context context, Uri uri, int edge, long pixels) throws IOException {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.getContentResolver(), uri),
                (decoder, info, source) -> {
                    int[] size = ImageGeometry.boundedSize(info.getSize().getWidth(), info.getSize().getHeight(), edge, pixels);
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    decoder.setTargetSize(size[0], size[1]);
                    // ImageDecoder applies EXIF orientation and rejects incomplete images by default.
                });
    }

    public static File prepare(Context context, Uri uri, boolean crop, int screenWidth, int screenHeight) throws IOException {
        Bitmap decoded = decode(context, uri, 8192, 16_000_000L);
        Bitmap cropped = null, opaque = null;
        File file = null;
        boolean complete = false;
        try {
            Bitmap source = decoded;
            if (crop) {
                int[] bounds = ImageGeometry.centerCrop(decoded.getWidth(), decoded.getHeight(), screenWidth, screenHeight);
                cropped = Bitmap.createBitmap(decoded, bounds[0], bounds[1], bounds[2] - bounds[0], bounds[3] - bounds[1]);
                source = cropped;
            }
            // PNG keeps image quality; flatten transparency to black for predictable wallpapers.
            if (source.hasAlpha()) {
                opaque = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(opaque);
                canvas.drawColor(Color.BLACK);
                canvas.drawBitmap(source, 0, 0, null);
                source = opaque;
            }
            file = File.createTempFile("wallc-", ".png", context.getCacheDir());
            try (FileOutputStream output = new FileOutputStream(file)) {
                if (!source.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("Failed to encode wallpaper image");
            }
            complete = true;
            return file;
        } finally {
            if (opaque != null) opaque.recycle();
            if (cropped != null && cropped != decoded) cropped.recycle();
            decoded.recycle();
            if (!complete && file != null) file.delete();
        }
    }
}
