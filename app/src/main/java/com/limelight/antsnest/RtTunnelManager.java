package com.limelight.antsnest;

import com.limelight.LimeLog;

/**
 * 앱 전역에서 하나뿐인 터널 세션을 관리한다.
 *
 * 터널은 "PC 추가" 화면에서 열리지만, 실제로 쓰이는 곳은 그 뒤의 페어링과
 * 스트리밍 전 과정이다 — Activity 수명에 묶으면 화면이 닫히는 순간 스트림이
 * 죽는다. 그래서 프로세스 수명의 싱글턴으로 두고, 새 PC 로 갈아탈 때만
 * 이전 세션을 접는다.
 *
 * Moonlight 쪽은 이 클래스의 존재를 모른다: 브릿지가 Sunshine 의 실제 포트
 * 번호 그대로 127.0.0.1 에 바인드되므로(rt-tunnel 의 동일 포트 우선 정책),
 * 클라이언트는 "127.0.0.1 에 있는 평범한 Sunshine" 을 볼 뿐이다.
 */
public final class RtTunnelManager {
    /** Sunshine TCP 포트: HTTPS 페어링, HTTP, RTSP. */
    private static final int[] SUNSHINE_TCP = { 47984, 47989, 48010 };
    /** Sunshine UDP 포트: 영상, 제어, 음성, RTSP 미러. */
    private static final int[] SUNSHINE_UDP = { 47998, 47999, 48000, 48010 };

    private static RtTunnel active;
    private static String activeDeviceId;

    private RtTunnelManager() {}

    /**
     * 디바이스 ID 로 터널을 수립하고 Sunshine 포트 전부를 브릿지한다. 블로킹
     * (워커 스레드 전용, 최대 ~45초).
     *
     * @return 성공 여부. 실패 사유는 {@link #lastError()}.
     */
    public static synchronized boolean connect(String deviceId, String password) {
        if (active != null && active.isConnected() && deviceId.equals(activeDeviceId)) {
            return true; // 같은 PC 로의 재연결 요청은 기존 세션을 재사용한다.
        }
        closeLocked();

        RtTunnel tunnel = new RtTunnel();
        if (!tunnel.connect(deviceId, password)) {
            LimeLog.warning("RtTunnel connect failed: " + tunnel.lastError());
            lastError = tunnel.lastError();
            tunnel.close();
            return false;
        }

        // 포트 하나라도 못 열면 스트리밍 어딘가가 반드시 조용히 죽는다.
        // 절반만 되는 세션을 남기느니 여기서 실패로 끝내는 편이 낫다.
        for (int port : SUNSHINE_TCP) {
            int local = tunnel.openTcp(port);
            if (local == 0) {
                lastError = "TCP bridge failed for port " + port;
                LimeLog.warning("RtTunnel: " + lastError);
                tunnel.close();
                return false;
            }
            if (local != port) {
                // 동일 포트 바인드 실패(다른 앱이 점유). Moonlight 은 표준
                // 포트만 알므로 이 세션은 성립할 수 없다.
                lastError = "loopback port " + port + " unavailable (got " + local + ")";
                LimeLog.warning("RtTunnel: " + lastError);
                tunnel.close();
                return false;
            }
        }
        for (int port : SUNSHINE_UDP) {
            int local = tunnel.openUdp(port);
            if (local == 0 || local != port) {
                lastError = "UDP bridge failed for port " + port;
                LimeLog.warning("RtTunnel: " + lastError);
                tunnel.close();
                return false;
            }
        }

        active = tunnel;
        activeDeviceId = deviceId;
        lastError = null;
        LimeLog.info("RtTunnel up: device " + deviceId + " → 127.0.0.1 (Sunshine ports bridged)");
        return true;
    }

    private static String lastError;

    public static synchronized String lastError() {
        return lastError != null ? lastError : (active != null ? active.lastError() : "");
    }

    /** 지금 이 디바이스로 살아 있는 터널이 있는가. */
    public static synchronized boolean isActive(String deviceId) {
        return active != null && active.isConnected() && deviceId.equals(activeDeviceId);
    }

    public static synchronized boolean isActive() {
        return active != null && active.isConnected();
    }

    public static synchronized void close() {
        closeLocked();
    }

    private static void closeLocked() {
        if (active != null) {
            active.close();
            active = null;
            activeDeviceId = null;
        }
    }
}
