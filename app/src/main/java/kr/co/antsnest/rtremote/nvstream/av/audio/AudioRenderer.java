package kr.co.antsnest.rtremote.nvstream.av.audio;

import kr.co.antsnest.rtremote.nvstream.jni.RtBridge;

public interface AudioRenderer {
    int setup(RtBridge.AudioConfiguration audioConfiguration, int sampleRate, int samplesPerFrame);

    void start();

    void stop();
    
    void playDecodedAudio(short[] audioData);
    
    void cleanup();
}
