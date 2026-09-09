package kr.co.antsnest.rtremote.antsnest;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.util.*;
import static org.junit.Assert.*;

public class DiagnosticOutboxTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private DiagnosticOutbox outbox() { return new DiagnosticOutbox(new File(folder.getRoot(), "outbox")); }
    @Test public void offlineReportSurvivesRestartAndRetries() throws Exception {
        String name = outbox().add("offline error");
        assertFalse(outbox().deliver(name, body -> false));
        assertEquals(Collections.singletonList(name), outbox().pending());
        assertTrue(outbox().deliver(name, body -> { assertEquals("offline error", body); return true; }));
        assertTrue(outbox().pending().isEmpty());
    }
    @Test public void transportExceptionRetainsEvidence() throws Exception {
        String name = outbox().add("error");
        try { outbox().deliver(name, body -> { throw new IOException("offline"); }); fail("expected failure"); }
        catch (IOException expected) { }
        assertEquals(Collections.singletonList(name), outbox().pending());
    }
    @Test public void laterCrashDoesNotOverwriteEarlierOne() throws Exception {
        String first = outbox().add("first");
        String second = outbox().add("second");
        assertEquals(new HashSet<>(Arrays.asList(first, second)), new HashSet<>(outbox().pending()));
        assertTrue(outbox().deliver(second, body -> true));
        assertEquals(Collections.singletonList(first), outbox().pending());
    }
    @Test public void repeatedDeliveryOfDeletedFileDoesNotSend() throws Exception {
        String name = outbox().add("report");
        outbox().deliver(name, body -> true);
        assertTrue(outbox().deliver(name, body -> { fail("already sent"); return false; }));
    }
    @Test(expected = IllegalArgumentException.class) public void cannotReadOutsideOutbox() throws Exception {
        outbox().deliver("../private", body -> true);
    }
    @Test public void credentialsAreRedactedButStackFramesRemain() {
        String safe = DiagnosticOutbox.redact("rtremote://host?pw=test-password&pin=123456&token=test-token\nAuthorization: Bearer test-bearer\n at Rt.java:42");
        for (String secret : Arrays.asList("test-password", "123456", "test-token", "test-bearer")) assertFalse(safe.contains(secret));
        assertTrue(safe.contains("at Rt.java:42"));
    }
}
