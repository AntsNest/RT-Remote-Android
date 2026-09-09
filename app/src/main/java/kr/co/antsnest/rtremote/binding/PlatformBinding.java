package kr.co.antsnest.rtremote.binding;

import android.content.Context;

import kr.co.antsnest.rtremote.binding.audio.AndroidAudioRenderer;
import kr.co.antsnest.rtremote.binding.crypto.AndroidCryptoProvider;
import kr.co.antsnest.rtremote.nvstream.av.audio.AudioRenderer;
import kr.co.antsnest.rtremote.nvstream.http.RtCryptoProvider;

public class PlatformBinding {
    public static RtCryptoProvider getCryptoProvider(Context c) {
        return new AndroidCryptoProvider(c);
    }
}
