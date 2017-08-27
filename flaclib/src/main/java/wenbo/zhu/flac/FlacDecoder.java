package wenbo.zhu.flac;

import android.support.annotation.Keep;

@Keep
public class FlacDecoder {
    private long decoderPointer;
    private WriteDataCallback writeDataCallback;

    static {
        System.loadLibrary("flac_jni");
    }

    public native StreamData init(String inputFile);

    private Object newStreamData(long totalSamples, int sampleRate, int channels, int bps) {
        return new StreamData(totalSamples, sampleRate, channels, bps);
    }

    public void setWriteDataCallback(WriteDataCallback writeDataCallback) {
        this.writeDataCallback = writeDataCallback;
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
        boolean writeData();
    }
}
