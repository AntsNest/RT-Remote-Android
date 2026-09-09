package kr.co.antsnest.rtremote.antsnest;

/**
 * NAT 통과 터널 엔진 (librttunnel.so) 바인딩.
 *
 * PC의 AntsRemote 호스트와 QUIC 세션(직결 P2P 또는 TURN 릴레이)을 맺고,
 * Sunshine의 TCP/UDP 포트를 폰의 127.0.0.1 루프백 포트로 노출한다.
 * Moonlight 코어는 그 루프백 주소로 평소처럼 접속하면 된다 — LAN인지
 * LTE인지, NAT이 몇 겹인지 이 앱은 알 필요가 없다.
 *
 * 사용 순서:
 * <pre>
 *   RtTunnel tunnel = new RtTunnel();
 *   if (!tunnel.connect(deviceId, password)) { show(tunnel.lastError()); return; }
 *   int httpsPort = tunnel.openTcp(47984);
 *   int httpPort  = tunnel.openTcp(47989);
 *   int rtspTcp   = tunnel.openTcp(48010);
 *   int videoUdp  = tunnel.openUdp(47998);
 *   int ctrlUdp   = tunnel.openUdp(47999);
 *   int audioUdp  = tunnel.openUdp(48000);
 *   int rtspUdp   = tunnel.openUdp(48010);
 *   // 이후 Moonlight 접속 대상: 127.0.0.1 + 위 포트들
 *   ...
 *   tunnel.close(); // 스트리밍 종료 시
 * </pre>
 *
 * connect() 는 블로킹(최대 ~45초)이므로 반드시 워커 스레드에서 부를 것.
 */
public class RtTunnel {
    static {
        System.loadLibrary("rttunnel");
    }

    /** 기본 시그널링 서버. 빈 문자열이면 네이티브 쪽 기본값을 쓴다. */
    public static final String DEFAULT_SIGNAL_URL = "wss://cast.antsnest.co.kr/ws";

    public static final int STATE_CONNECTING = 0;
    public static final int STATE_CONNECTED = 1;
    public static final int STATE_FAILED = 2;
    public static final int STATE_CLOSED = 3;

    private long handle;

    /**
     * PC(디바이스 ID)와 터널 세션을 수립한다. 블로킹.
     *
     * @return 성공 여부. 실패 시 {@link #lastError()}에 원인이 남는다.
     */
    public boolean connect(String deviceId, String password) {
        return connect(deviceId, password, "");
    }

    public boolean connect(String deviceId, String password, String signalUrl) {
        handle = nativeConnect(deviceId, password, signalUrl);
        return handle != 0 && nativeState(handle) == STATE_CONNECTED;
    }

    public int state() {
        return handle == 0 ? STATE_CLOSED : nativeState(handle);
    }

    public boolean isConnected() {
        return state() == STATE_CONNECTED;
    }

    public String lastError() {
        return handle == 0 ? "not connected" : nativeLastError(handle);
    }

    /**
     * PC의 TCP 포트를 폰 루프백으로 브릿지한다.
     *
     * @return 127.0.0.1의 로컬 포트. 0이면 실패.
     */
    public int openTcp(int remotePort) {
        return handle == 0 ? 0 : nativeOpenTcp(handle, remotePort);
    }

    /**
     * PC의 UDP 포트를 폰 루프백으로 브릿지한다.
     *
     * @return 127.0.0.1의 로컬 UDP 포트. 0이면 실패.
     */
    public int openUdp(int remotePort) {
        return handle == 0 ? 0 : nativeOpenUdp(handle, remotePort);
    }

    /** 세션과 모든 브릿지를 정리한다. */
    public void close() {
        if (handle != 0) {
            nativeClose(handle);
            handle = 0;
        }
    }

    private static native long nativeConnect(String deviceId, String password, String signalUrl);
    private static native int nativeState(long handle);
    private static native String nativeLastError(long handle);
    private static native int nativeOpenTcp(long handle, int remotePort);
    private static native int nativeOpenUdp(long handle, int remotePort);
    private static native void nativeClose(long handle);
}
