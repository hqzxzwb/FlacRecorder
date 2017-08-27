package wenbo.zhu.flac;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class FlacPlayer {
    private static final String TAG = "FlacPlayer";
    private FlacDecoder decoder;
    private AudioTrack audioTrack;

    public FlacPlayer() {
    }

    public boolean init(String inputFile) {
        decoder = new FlacDecoder();
        FlacDecoder.StreamData data = decoder.init(inputFile);
        if (data == null) {
            return false;
        }
        int sampleRate = data.sampleRate;
        int channelConfig = data.channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
        int audioFormat = data.bps == 8 ? AudioFormat.ENCODING_PCM_8BIT : AudioFormat.ENCODING_PCM_16BIT;
        audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelConfig,
                audioFormat,
                AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2,
                AudioTrack.MODE_STREAM
        );
        return true;
    }

    public void play() {
        new Thread() {
            @Override
            public void run() {
                playImpl();
            }
        }.start();
    }

    private void playImpl() {
        audioTrack.play();
        decoder.decode(new FlacDecoder.WriteDataCallback() {
            @Override
            public boolean writeData(byte[] buffer) {
                audioTrack.write(buffer, 0, buffer.length);
                return true;
            }
        });
    }
}
