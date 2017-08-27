package wenbo.zhu.flac;

import android.util.Log;

public class FlacPlayer {
    private static final String TAG = "FlacPlayer";
    private FlacDecoder decoder;

    public FlacPlayer() {
    }

    public boolean init(String inputFile) {
        decoder = new FlacDecoder();
        FlacDecoder.StreamData data = decoder.init(inputFile);
        return data != null;
    }
}
