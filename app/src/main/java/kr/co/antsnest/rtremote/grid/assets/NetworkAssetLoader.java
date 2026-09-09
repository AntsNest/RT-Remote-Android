package kr.co.antsnest.rtremote.grid.assets;

import android.content.Context;

import kr.co.antsnest.rtremote.RtLog;
import kr.co.antsnest.rtremote.binding.PlatformBinding;
import kr.co.antsnest.rtremote.nvstream.http.NvHTTP;
import kr.co.antsnest.rtremote.utils.ServerHelper;

import java.io.IOException;
import java.io.InputStream;

public class NetworkAssetLoader {
    private final Context context;
    private final String uniqueId;

    public NetworkAssetLoader(Context context, String uniqueId) {
        this.context = context;
        this.uniqueId = uniqueId;
    }

    public InputStream getBitmapStream(CachedAppAssetLoader.LoaderTuple tuple) {
        InputStream in = null;
        try {
            NvHTTP http = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(tuple.computer),
                    tuple.computer.httpsPort, uniqueId, tuple.computer.serverCert,
                    PlatformBinding.getCryptoProvider(context));
            in = http.getBoxArt(tuple.app);
        } catch (IOException ignored) {}

        if (in != null) {
            RtLog.info("Network asset load complete: " + tuple);
        }
        else {
            RtLog.info("Network asset load failed: " + tuple);
        }

        return in;
    }
}
