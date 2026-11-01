package org.eu.suisei.wallc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class ImageTransportTest {
    @Test public void transfersBinaryImageWithoutInterpretingShellCharacters() throws Exception {
        byte[] image = new byte[50000];
        for (int i = 0; i < image.length; i++) image[i] = (byte) i;
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        ImageTransport.send(new ByteArrayInputStream(image), image.length, wire);
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        ImageTransport.receive(new ByteArrayInputStream(wire.toByteArray()), received);
        assertArrayEquals(image, received.toByteArray());
    }
    @Test public void detectsTruncatedImageBeforeWallpaperSubmission() throws Exception {
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        new DataOutputStream(wire).writeLong(500);
        wire.write(new byte[100]);
        assertThrows(EOFException.class, () -> ImageTransport.receive(new ByteArrayInputStream(wire.toByteArray()), new ByteArrayOutputStream()));
    }
    @Test public void rejectsUnboundedInputLength() throws Exception {
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        new DataOutputStream(wire).writeLong(Long.MAX_VALUE);
        assertThrows(IOException.class, () -> ImageTransport.receive(new ByteArrayInputStream(wire.toByteArray()), new ByteArrayOutputStream()));
    }
    @Test public void rejectsEmptyImage() {
        assertThrows(IOException.class, () -> ImageTransport.send(new ByteArrayInputStream(new byte[0]), 0, new ByteArrayOutputStream()));
    }
    @Test public void detectsSourceThatEndsBeforeDeclaredLength() {
        assertThrows(EOFException.class, () -> ImageTransport.send(new ByteArrayInputStream(new byte[10]), 100, new ByteArrayOutputStream()));
    }
}
