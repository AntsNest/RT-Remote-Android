package kr.co.antsnest.rtremote.antsnest;

/** Pure retry policy shared by the stream Activity and local unit tests. */
public final class RtSessionRecoveryPolicy {
    public static final long[] DELAYS_MS = { 4000, 6000, 9000 };

    private RtSessionRecoveryPolicy() {}

    public static boolean shouldRetry(boolean userInitiatedStop,
                                      boolean retryPending,
                                      int retryCount,
                                      boolean wasConnected,
                                      boolean tunnelActive) {
        if (userInitiatedStop || retryPending || !tunnelActive) {
            return false;
        }
        if (retryCount < 0 || retryCount >= DELAYS_MS.length) {
            return false;
        }
        // The initial Activity retries only after a real stream was connected.
        // A retry Activity may retry a startup-stage failure while Sunshine is
        // still becoming ready after the Windows session transition.
        return wasConnected || retryCount > 0;
    }
}
