package kr.co.antsnest.rtremote.antsnest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RtSessionRecoveryPolicyTest {
    @Test public void connectedRtStreamRetriesOnAnyTerminationCodePath() {
        assertTrue(RtSessionRecoveryPolicy.shouldRetry(false, false, 0, true, true));
    }

    @Test public void recoveryActivityRetriesSunshineStartupFailure() {
        assertTrue(RtSessionRecoveryPolicy.shouldRetry(false, false, 1, false, true));
    }

    @Test public void initialConnectionFailureIsNotHidden() {
        assertFalse(RtSessionRecoveryPolicy.shouldRetry(false, false, 0, false, true));
    }

    @Test public void userStopNeverRetries() {
        assertFalse(RtSessionRecoveryPolicy.shouldRetry(true, false, 0, true, true));
    }

    @Test public void duplicateCallbackNeverSchedulesTwice() {
        assertFalse(RtSessionRecoveryPolicy.shouldRetry(false, true, 0, true, true));
    }

    @Test public void deadTunnelDoesNotLoopLocally() {
        assertFalse(RtSessionRecoveryPolicy.shouldRetry(false, false, 0, true, false));
    }

    @Test public void retryBudgetIsBounded() {
        assertFalse(RtSessionRecoveryPolicy.shouldRetry(false, false,
                RtSessionRecoveryPolicy.DELAYS_MS.length, true, true));
    }
}
