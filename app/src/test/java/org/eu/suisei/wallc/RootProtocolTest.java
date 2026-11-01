package org.eu.suisei.wallc;

import org.junit.Test;
import static org.junit.Assert.*;

public class RootProtocolTest {
    @Test public void commandPreservesIndependentTarget() {
        assertEquals("exec '/data/app/lib/libwallc_launcher.so' '/data/app/base.apk' 0 set 2",
                RootProtocol.command("/data/app/lib/libwallc_launcher.so", "/data/app/base.apk", 0, "set", 2));
    }
    @Test public void diagnosisCannotWriteWallpaper() {
        assertTrue(RootProtocol.command("/lib.so", "/base.apk", 10, "diagnose", 0).endsWith("10 diagnose 0"));
        assertThrows(IllegalArgumentException.class, () -> RootProtocol.command("/lib.so", "/base.apk", 0, "diagnose", 1));
    }
    @Test public void rejectsCombinedTargetBeforeExecutingRootCommand() {
        assertThrows(IllegalArgumentException.class, () -> RootProtocol.command("/lib.so", "/base.apk", 0, "set", 3));
    }
    @Test public void rejectsCommandInjectionInOperation() {
        assertThrows(IllegalArgumentException.class, () -> RootProtocol.command("/lib.so", "/base.apk", 0, "set; id", 1));
    }
    @Test public void quotesPathsIncludingApostrophesAndShellSubstitutions() {
        assertEquals("'/data/a'\\''b/$(id); x'", RootProtocol.quote("/data/a'b/$(id); x"));
        assertThrows(IllegalArgumentException.class, () -> RootProtocol.quote("bad\0path"));
    }
    @Test public void parsesStructuredResultAroundRootAndRuntimeLogs() {
        String text = "some su output\nWALLC_ROOT_UID=0\nWALLC_RESULT={\"ok\":true}\nmore logs\n";
        assertEquals("{\"ok\":true}", RootProtocol.resultJson(text));
        assertTrue(RootProtocol.rootGranted(text));
    }
    @Test public void permissionDenialCannotBeMistakenForSuccess() {
        String text = "su: permission denied\nnot WALLC_ROOT_UID=0\n";
        assertFalse(RootProtocol.rootGranted(text)); assertNull(RootProtocol.resultJson(text));
    }
    @Test public void supportsCrLfAndUsesLastCompleteResponse() {
        assertEquals("{\"ok\":false}", RootProtocol.resultJson("WALLC_RESULT={}\r\nWALLC_RESULT={\"ok\":false}\r\n"));
    }
    @Test public void rejectsInvalidUserAndRelativeExecutable() {
        assertThrows(IllegalArgumentException.class, () -> RootProtocol.command("lib.so", "/base.apk", 0, "set", 1));
        assertThrows(IllegalArgumentException.class, () -> RootProtocol.command("/lib.so", "/base.apk", -1, "set", 1));
    }
}
