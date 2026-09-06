package com.limelight.preferences;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.antsnest.RtTunnelManager;
import com.limelight.antsnest.RtDiagnostics;
import com.limelight.antsnest.DiagnosticOutbox;
import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerManagerService;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.utils.Dialog;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

public class AddComputerManually extends Activity {
    private TextView hostText;
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private final LinkedBlockingQueue<String> computersToAdd = new LinkedBlockingQueue<>();
    private Thread addThread;

    /** rtremote:// 딥링크로 열렸는가 — 그때는 닫기 전에 목록을 직접 띄워야 한다. */
    private boolean fromDeepLink = false;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, final IBinder binder) {
            managerBinder = ((ComputerManagerService.ComputerManagerBinder)binder);
            startAddThread();
        }

        public void onServiceDisconnected(ComponentName className) {
            joinAddThread();
            managerBinder = null;
        }
    };

    private boolean isWrongSubnetSiteLocalAddress(String address) {
        try {
            InetAddress targetAddress = InetAddress.getByName(address);
            if (!(targetAddress instanceof Inet4Address) || !targetAddress.isSiteLocalAddress()) {
                return false;
            }

            // We have a site-local address. Look for a matching local interface.
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InterfaceAddress addr : iface.getInterfaceAddresses()) {
                    if (!(addr.getAddress() instanceof Inet4Address) || !addr.getAddress().isSiteLocalAddress()) {
                        // Skip non-site-local or non-IPv4 addresses
                        continue;
                    }

                    byte[] targetAddrBytes = targetAddress.getAddress();
                    byte[] ifaceAddrBytes = addr.getAddress().getAddress();

                    // Compare prefix to ensure it's the same
                    boolean addressMatches = true;
                    for (int i = 0; i < addr.getNetworkPrefixLength(); i++) {
                        if ((ifaceAddrBytes[i / 8] & (1 << (i % 8))) != (targetAddrBytes[i / 8] & (1 << (i % 8)))) {
                            addressMatches = false;
                            break;
                        }
                    }

                    if (addressMatches) {
                        return false;
                    }
                }
            }

            // Couldn't find a matching interface
            return true;
        } catch (Exception e) {
            // Catch all exceptions because some broken Android devices
            // will throw an NPE from inside getNetworkInterfaces().
            e.printStackTrace();
            return false;
        }
    }

    /**
     * "디바이스ID/비밀번호" 입력을 알아본다. 아니면 null.
     *
     * 디바이스 ID 는 순수 숫자(공백 허용: "167 874 886")다. IP:포트나 호스트
     * 이름과 절대 겹치지 않도록, 슬래시가 정확히 하나 있고 앞부분이 숫자로만
     * 이루어졌을 때만 터널 경로로 본다.
     */
    private String parseTunnelInput(String rawUserInput) {
        int slash = rawUserInput.indexOf('/');
        if (slash <= 0 || slash != rawUserInput.lastIndexOf('/')) {
            return null;
        }
        String id = rawUserInput.substring(0, slash).replace(" ", "");
        String password = rawUserInput.substring(slash + 1);
        if (id.isEmpty() || password.isEmpty() || !id.matches("[0-9]{6,12}")) {
            return null;
        }
        return id + "/" + password;
    }

    private URI parseRawUserInputToUri(String rawUserInput) {
        try {
            // Try adding a scheme and parsing the remaining input.
            // This handles input like 127.0.0.1:47989, [::1], [::1]:47989, and 127.0.0.1.
            URI uri = new URI("moonlight://" + rawUserInput);
            if (uri.getHost() != null && !uri.getHost().isEmpty()) {
                return uri;
            }
        } catch (URISyntaxException ignored) {}

        try {
            // Attempt to escape the input as an IPv6 literal.
            // This handles input like ::1.
            URI uri = new URI("moonlight://[" + rawUserInput + "]");
            if (uri.getHost() != null && !uri.getHost().isEmpty()) {
                return uri;
            }
        } catch (URISyntaxException ignored) {}

        return null;
    }

    private void doAddPc(String rawUserInput) throws InterruptedException {
        boolean wrongSiteLocal = false;
        boolean invalidInput = false;
        boolean success;
        int portTestResult;

        SpinnerDialog dialog = SpinnerDialog.displayDialog(this, getResources().getString(R.string.title_add_pc),
            getResources().getString(R.string.msg_add_pc), false);

        // ── 디바이스 ID 경로 (NAT 통과 터널) ──────────────────────────
        //
        // 입력이 "숫자ID/비밀번호" 꼴이면 IP 가 아니라 AntsRemote 디바이스
        // ID 다. librttunnel 로 PC 와 P2P/TURN 세션을 맺고 Sunshine 포트를
        // 127.0.0.1 에 그대로 얹은 뒤, 아래의 기존 추가 흐름에 127.0.0.1 을
        // 넣는다 — 이 지점 이후는 LAN 추가와 완전히 같은 코드가 돈다.
        //
        //   167874886/mypassword      (수동 입력)
        //   rtremote://tunnel?device=167874886&pw=...&pin=4821  (딥링크)
        String tunnelTarget = parseTunnelInput(rawUserInput);
        if (tunnelTarget != null) {
            String[] parts = tunnelTarget.split("/", 2);
            long tunnelStartedAt = System.currentTimeMillis();
            RtDiagnostics.record("initial_tunnel_connect", "device=" + parts[0]);
            boolean tunnelUp = RtTunnelManager.connect(parts[0], parts[1]);
            if (!tunnelUp) {
                dialog.dismiss();
                String nativeReason = RtTunnelManager.lastError();
                final String reason = DiagnosticOutbox.redact(nativeReason == null ? "상세 응답 없음" : nativeReason)
                        .replace(parts[1], "[redacted]");
                RtDiagnostics.record("initial_tunnel_failed", "device=" + parts[0] + " elapsedMs=" +
                        (System.currentTimeMillis() - tunnelStartedAt) + " reason=" + reason);
                RtDiagnostics.upload(this, "initial_tunnel_failed");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Dialog.displayDialog(AddComputerManually.this,
                                getResources().getString(R.string.conn_error_title),
                                "원격 터널 연결 실패: " + reason + "\n\nPC의 RT 호스트 실행 상태와 인터넷 연결을 확인해 주세요. " +
                                        "오류 기록은 기기에 보관하고 네트워크 연결 시 서버 전송을 재시도합니다.", false);
                    }
                });
                return;
            }
            // 터널이 열렸다 — 이제 PC 는 127.0.0.1 의 Sunshine 이다.
            rawUserInput = "127.0.0.1";
            RtDiagnostics.record("initial_tunnel_connected", "elapsedMs=" + (System.currentTimeMillis() - tunnelStartedAt));
        }

        try {
            ComputerDetails details = new ComputerDetails();

            // Check if we parsed a host address successfully
            URI uri = parseRawUserInputToUri(rawUserInput);
            if (uri != null && uri.getHost() != null && !uri.getHost().isEmpty()) {
                String host = uri.getHost();
                int port = uri.getPort();

                // If a port was not specified, use the default
                if (port == -1) {
                    port = NvHTTP.DEFAULT_HTTP_PORT;
                }

                details.manualAddress = new ComputerDetails.AddressTuple(host, port);
                success = managerBinder.addComputerBlocking(details);
                if (!success){
                    wrongSiteLocal = isWrongSubnetSiteLocalAddress(host);
                }
            } else {
                // Invalid user input
                success = false;
                invalidInput = true;
            }
        } catch (InterruptedException e) {
            // Propagate the InterruptedException to the caller for proper handling
            dialog.dismiss();
            throw e;
        } catch (IllegalArgumentException e) {
            // This can be thrown from OkHttp if the host fails to canonicalize to a valid name.
            // https://github.com/square/okhttp/blob/okhttp_27/okhttp/src/main/java/com/squareup/okhttp/HttpUrl.java#L705
            e.printStackTrace();
            success = false;
            invalidInput = true;
        }

        // Keep the SpinnerDialog open while testing connectivity
        if (!success && !wrongSiteLocal && !invalidInput) {
            // Run the test before dismissing the spinner because it can take a few seconds.
            portTestResult = MoonBridge.testClientConnectivity(ServerHelper.CONNECTION_TEST_SERVER, 443,
                    MoonBridge.ML_PORT_FLAG_TCP_47984 | MoonBridge.ML_PORT_FLAG_TCP_47989);
        } else {
            // Don't bother with the test if we succeeded or the IP address was bogus
            portTestResult = MoonBridge.ML_TEST_RESULT_INCONCLUSIVE;
        }

        dialog.dismiss();

        if (tunnelTarget != null && !success) {
            RtDiagnostics.record("initial_sunshine_failed", "invalidInput=" + invalidInput +
                    " wrongSubnet=" + wrongSiteLocal + " connectivityResult=" + portTestResult);
            RtDiagnostics.upload(this, "initial_sunshine_failed");
        }

        if (invalidInput) {
            Dialog.displayDialog(this, getResources().getString(R.string.conn_error_title), getResources().getString(R.string.addpc_unknown_host), false);
        }
        else if (wrongSiteLocal) {
            Dialog.displayDialog(this, getResources().getString(R.string.conn_error_title), getResources().getString(R.string.addpc_wrong_sitelocal), false);
        }
        else if (!success) {
            String dialogText;
            if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0)  {
                dialogText = getResources().getString(R.string.nettest_text_blocked);
            }
            else {
                dialogText = getResources().getString(R.string.addpc_fail);
            }
            Dialog.displayDialog(this, getResources().getString(R.string.conn_error_title), dialogText, false);
        }
        else {
            AddComputerManually.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                Toast.makeText(AddComputerManually.this, getResources().getString(R.string.addpc_success), Toast.LENGTH_LONG).show();

                // 딥링크로 왔으면 목록을 직접 띄우고 닫는다.
                //
                // 그냥 닫으면 아래에 아무것도 없어 앱이 끝난다. 앞에서 넘겨둔
                // PIN 과 자동 짝짓기 주소를 받아 갈 화면이 바로 PcView 다 —
                // 띄우지 않으면 그 준비가 전부 헛것이 된다.
                if (fromDeepLink) {
                    Intent list = new Intent(AddComputerManually.this, com.limelight.PcView.class);
                    list.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(list);
                }

                if (!isFinishing()) {
                    // Close the activity
                    AddComputerManually.this.finish();
                }
                }
            });
        }

    }

    private void startAddThread() {
        addThread = new Thread() {
            @Override
            public void run() {
                while (!isInterrupted()) {
                    try {
                        String computer = computersToAdd.take();
                        doAddPc(computer);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        };
        addThread.setName("UI - AddComputerManually");
        addThread.start();
    }

    private void joinAddThread() {
        if (addThread != null) {
            addThread.interrupt();

            try {
                addThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();

                // InterruptedException clears the thread's interrupt status. Since we can't
                // handle that here, we will re-interrupt the thread to set the interrupt
                // status back to true.
                Thread.currentThread().interrupt();
            }

            addThread = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        Dialog.closeDialogs();
        SpinnerDialog.closeDialogs(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (managerBinder != null) {
            joinAddThread();
            unbindService(serviceConnection);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UiHelper.setLocale(this);

        setContentView(R.layout.activity_add_computer_manually);

        UiHelper.notifyNewRootView(this);

        this.hostText = findViewById(R.id.hostTextView);
        hostText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        hostText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                        (keyEvent != null &&
                                keyEvent.getAction() == KeyEvent.ACTION_DOWN &&
                                keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    return handleDoneEvent();
                }
                else if (actionId == EditorInfo.IME_ACTION_PREVIOUS) {
                    // This is how the Fire TV dismisses the keyboard
                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(hostText.getWindowToken(), 0);
                    return false;
                }

                return false;
            }
        });

        // ── rtremote:// 딥링크 ────────────────────────────────────────
        //
        // 앤츠톡에서 "그래픽 제어" 를 누르면 이 앱이 이 경로로 열린다. 사용자가
        // 주소를 옮겨 적게 하면 아무도 안 쓴다 — 앤츠톡은 이미 그 PC 의 메시
        // 주소를 알고 있으므로 그대로 넘겨받는다.
        //
        //   rtremote://100.77.1.5
        //
        // 주소를 채우고 바로 추가를 시작한다. 그 뒤 흐름(페어링·앱 목록)은
        // 손으로 추가했을 때와 완전히 같다 — 새 경로를 만들지 않는 것이
        // 고장날 곳을 늘리지 않는 유일한 방법이다.
        android.net.Uri link = getIntent() != null ? getIntent().getData() : null;
        if (link != null && "rtremote".equals(link.getScheme())) {
            fromDeepLink = true;

            // ── rtremote://tunnel?device=167874886&pw=...&pin=4821 ──
            //
            // 외부망용 새 형태: IP 대신 AntsRemote 디바이스 ID 를 받아
            // NAT 통과 터널을 거쳐 접속한다. 기존 rtremote://<ip> 형태는
            // 그대로 아래에서 처리된다 (LAN 직결).
            if ("tunnel".equals(link.getHost())) {
                String device = null, pw = null, linkedPin = null;
                try {
                    device = link.getQueryParameter("device");
                    pw = link.getQueryParameter("pw");
                    linkedPin = link.getQueryParameter("pin");
                } catch (Exception ignored) {}

                if (linkedPin != null && linkedPin.matches("[0-9]{4}")) {
                    com.limelight.PcView.setPresetPin(linkedPin);
                    // 터널 성립 후의 PC 주소는 언제나 루프백이다.
                    com.limelight.PcView.setAutoPairAddress("127.0.0.1");
                }
                if (device != null && pw != null && !device.isEmpty() && !pw.isEmpty()) {
                    hostText.setText(device.replace(" ", "") + "/" + pw);
                    hostText.post(new Runnable() {
                        @Override
                        public void run() {
                            handleDoneEvent();
                        }
                    });
                }
            } else {
            // 앤츠톡이 PIN 도 같이 정해서 넘긴다.
            //
            //   rtremote://100.77.1.5?pin=4821
            //
            // 원래는 이 앱이 PIN 을 만들어 화면에 띄우고, 사람이 그걸 PC 의
            // Sunshine 웹 화면에 옮겨 적어야 짝이 맺어졌다. 옮겨 적기는
            // 자동화의 반대말이다 — 앤츠톡은 이미 그 PC 의 주소도 알고,
            // 콘솔에 PIN 을 대신 넣어줄 창구도 갖고 있다. 양쪽에 같은 값을
            // 알려주면 사람이 낄 자리가 없어진다.
            // 딥링크로 열렸음을 기억한다.
            //
            // 평소 이 화면은 PcView 위에 얹혀 있어서, 추가가 끝나고 닫히면 그
            // 아래 목록으로 돌아가고 거기서 짝짓기가 이어진다. 그런데 딥링크로
            // 열면 이 화면 하나뿐이라 닫는 순간 앱이 통째로 끝난다 — 쓰는
            // 사람에게는 "컴퓨터만 추가하고 꺼진다" 로 보인다(실측 2026-08-21).
            // 아래에서 PcView 를 직접 띄운다.
            fromDeepLink = true;

            String linkedPin = null;
            try { linkedPin = link.getQueryParameter("pin"); } catch (Exception ignored) {}
            if (linkedPin != null && linkedPin.matches("[0-9]{4}")) {
                com.limelight.PcView.setPresetPin(linkedPin);
                // 짝짓기도 우리가 시작한다. 사람이 카드를 눌러야 시작되게
                // 두면 자동이 아니다 — 콘솔은 PIN 을 24초 동안만 PC 에
                // 밀어넣는데, 그 사이에 목록이 뜨고 사용자가 알아보고 손가락을
                // 올려야 맞아떨어진다. 실제로 12번 시도가 전부 헛돌았다.
                com.limelight.PcView.setAutoPairAddress(link.getHost());
            }

            String linkedHost = link.getHost();
            if (linkedHost != null && !linkedHost.isEmpty()) {
                if (link.getPort() != -1) {
                    linkedHost = linkedHost + ":" + link.getPort();
                }
                hostText.setText(linkedHost);
                hostText.post(new Runnable() {
                    @Override
                    public void run() {
                        handleDoneEvent();
                    }
                });
            }
            } // end: legacy rtremote://<ip> path
        }

        findViewById(R.id.addPcButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleDoneEvent();
            }
        });

        // Bind to the ComputerManager service
        bindService(new Intent(AddComputerManually.this,
                    ComputerManagerService.class), serviceConnection, Service.BIND_AUTO_CREATE);
    }

    // Returns true if the event should be eaten
    private boolean handleDoneEvent() {
        String hostAddress = hostText.getText().toString().trim();

        if (hostAddress.length() == 0) {
            Toast.makeText(AddComputerManually.this, getResources().getString(R.string.addpc_enter_ip), Toast.LENGTH_LONG).show();
            return true;
        }

        computersToAdd.add(hostAddress);
        return false;
    }
}
