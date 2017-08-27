package wenbo.zhu.flac;

import android.support.annotation.Keep;
import android.support.annotation.NonNull;

@Keep
public class FlacDecoder {
    private long decoderPointer;
    private long clientDataPointer;
    private WriteDataCallback writeDataCallback;

    static {
        System.loadLibrary("flac_jni");
    }

    public native StreamData init(String inputFile);

    private native boolean nativeDecode();

    public boolean decode(@NonNull WriteDataCallback callback) {
        writeDataCallback = callback;
        boolean r;
        try {
            r = nativeDecode();
        } finally {
            writeDataCallback = null;
        }
        return r;
    }

    private Object newStreamData(long totalSamples, int sampleRate, int channels, int bps) {
        return new StreamData(totalSamples, sampleRate, channels, bps);
    }

    private boolean writeData(byte[] buffer) {
        return writeDataCallback.writeData(buffer);
    }

    public static class StreamData {
        long totalSamples;
        int sampleRate;
        int channels;
        int bps;

        StreamData(long totalSamples, int sampleRate, int channels, int bps) {
            this.totalSamples = totalSamples;
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.bps = bps;
        }
    }

    public interface WriteDataCallback {
        boolean writeData(byte[] buffer);
    }
}
