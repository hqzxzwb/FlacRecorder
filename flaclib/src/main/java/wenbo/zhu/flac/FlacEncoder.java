package wenbo.zhu.flac;

import android.support.annotation.Keep;

@Keep
class FlacEncoder {
    private long encoderPointer;

    static {
        System.loadLibrary("flac_jni");
    }

    native boolean init(int sampleRate,
                        int channels,
                        int bps,
                        int compressionLevel,
                        String fileName);

    native boolean process(byte[] data, int len);

    native boolean finish();

    native int getState();
}
