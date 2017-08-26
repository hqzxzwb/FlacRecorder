package wenbo.zhu.flac;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.IntDef;
import android.support.annotation.IntRange;
import android.text.TextUtils;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class uses {@link AudioRecord} to record audio and encode it to flac file.
 * Create an instance with {@link Builder}.
 */
public class FlacRecorder {
    public static final int STATE_INIT = 0;
    public static final int STATE_RUNNING = 1;
    public static final int STATE_FINISHED = 3;
    public static final int STATE_ERROR = 4;

    private static final String TAG = "FlacRecorder";
    private static final int BUFFER_SIZE_RATIO = 2;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BITS_PER_SAMPLE = 16;
    private LinkedBlockingQueue<BufferEntry> bufferQueue = new LinkedBlockingQueue<>();
    private ConcurrentLinkedQueue<BufferEntry> crapBuffers = new ConcurrentLinkedQueue<>();

    private volatile int currentState;
    private int bufferSize;
    private AudioRecord record;
    private Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private Thread readThread = new Thread() {
        @Override
        public void run() {
            AudioRecord record = FlacRecorder.this.record;
            LinkedBlockingQueue<BufferEntry> bufferQueue = FlacRecorder.this.bufferQueue;
            int bufferSize = FlacRecorder.this.bufferSize;
            while (currentState != STATE_ERROR) {
                BufferEntry bufferEntry = getNewBuffer();
                int byteCount = record.read(bufferEntry.buffer, 0, bufferSize);
                if (byteCount < 0) {
                    Log.e(TAG, "Read from AudioRecord error: " + byteCount);
                    bufferEntry.size = -1;  // Signal end to encoder thread.
                } else if (byteCount == 0) {
                    bufferEntry.size = -1;
                } else {
                    bufferEntry.size = byteCount;
                }
                try {
                    bufferQueue.put(bufferEntry);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    returnToCrap(bufferEntry);
                }
                if (bufferEntry.size == -1) {
                    break;
                }
            }
        }
    };
    private Thread encodeThread = new Thread() {
        @Override
        public void run() {
            FlacEncoder encoder = FlacRecorder.this.encoder;
            LinkedBlockingQueue<BufferEntry> bufferQueue = FlacRecorder.this.bufferQueue;
            while (true) {
                final BufferEntry bufferEntry;
                try {
                    bufferEntry = bufferQueue.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
                if (bufferEntry.size == -1) {
                    break;
                }
                boolean processResult = encoder.process(bufferEntry.buffer, bufferEntry.size);
                if (!processResult) {
                    Log.e(TAG, "Encoder error: " + encoder.getState());
                    currentState = STATE_ERROR;
                    notifyError();
                    return;
                }
                returnToCrap(bufferEntry);
            }
            if (!encoder.finish()) {
                Log.e(TAG, "Encoder finish error. Code " + encoder.getState());
            }
        }
    };
    private FlacEncoder encoder = new FlacEncoder();
    private String outFile;
    private int compressLevel;
    private OnErrorListener onErrorListener;

    private FlacRecorder(Builder builder) {
        int minBufferSize = AudioRecord.getMinBufferSize(builder.sampleRateInHz,
                builder.channelConfig, AUDIO_FORMAT);
        int bufferSize = minBufferSize * BUFFER_SIZE_RATIO;
        this.record = new AudioRecord(builder.audioSource,
                builder.sampleRateInHz,
                builder.channelConfig,
                AUDIO_FORMAT,
                bufferSize);
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            this.currentState = STATE_ERROR;
            notifyError();
            return;
        }
        this.bufferSize = bufferSize;
        this.outFile = builder.outFile;
        this.compressLevel = builder.compressionLevel;
    }

    /**
     * Start recording.
     * Check result with {@link #getCurrentState()}.
     * If no error, the state should be {@link #STATE_RUNNING}.
     */
    public void start() {
        if (currentState != STATE_INIT) {
            Log.e(TAG, "Calling start in wrong state " + currentState);
            return;
        }
        AudioRecord record = this.record;

        int sampleRate = record.getSampleRate();
        int channels = record.getChannelCount();
        boolean encoderInitResult = encoder.init(sampleRate, channels, BITS_PER_SAMPLE,
                compressLevel, outFile);
        if (!encoderInitResult) {
            Log.e(TAG, "Encoder init error.");
            this.currentState = STATE_ERROR;
            notifyError();
            return;
        }

        record.startRecording();
        this.currentState = STATE_RUNNING;
        readThread.start();
        encodeThread.start();
    }

    /**
     * Stop recording. The file will be ready a little later.
     * Recorder goes to {@link #STATE_FINISHED} state after this call.
     */
    public void stop() {
        if (currentState != STATE_RUNNING) {
            Log.e(TAG, "Calling stop in wrong state " + currentState);
            return;
        }
        record.stop();
        try {
            // This costs much less time than AudioRecord.stop(). No need to do async.
            encodeThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        this.currentState = STATE_FINISHED;
    }

    /**
     * Get state of Recorder.
     *
     * @return current state.
     */
    @State
    public int getCurrentState() {
        return currentState;
    }

    public void setOnErrorListener(OnErrorListener listener) {
        this.onErrorListener = listener;
    }

    private BufferEntry getNewBuffer() {
        BufferEntry entry = crapBuffers.poll();
        return entry == null ? new BufferEntry() : entry;
    }

    private void returnToCrap(BufferEntry entry) {
        crapBuffers.add(entry);
    }

    private void notifyError() {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                if (onErrorListener != null) {
                    onErrorListener.onError();
                }
            }
        });
    }

    private class BufferEntry {
        byte[] buffer = new byte[bufferSize];
        int size;
    }

    public static class Builder {
        int audioSource = MediaRecorder.AudioSource.MIC;
        int sampleRateInHz = 44100;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int compressionLevel = 5;
        String outFile;

        public Builder() {
        }

        /**
         * Audio source to initialize AudioRecord {@link MediaRecorder.AudioSource}.
         * Default {@link MediaRecorder.AudioSource#MIC}.
         *
         * @param audioSource Audio source.
         * @return this
         * @see AudioRecord#AudioRecord(int, int, int, int, int)
         */
        public Builder audioSource(int audioSource) {
            this.audioSource = audioSource;
            return this;
        }

        /**
         * Sample rate in Hz. Default 44100.
         *
         * @param sampleRateInHz rate
         * @return this
         * @see AudioRecord#AudioRecord(int, int, int, int, int)
         */
        public Builder sampleRateInHz(int sampleRateInHz) {
            this.sampleRateInHz = sampleRateInHz;
            return this;
        }

        /**
         * Channel config for recorder. Default {@link AudioFormat#CHANNEL_IN_MONO}
         *
         * @param channelConfig channelConfig
         * @return this
         * @see AudioRecord#AudioRecord(int, int, int, int, int)
         */
        public Builder channelConfig(int channelConfig) {
            this.channelConfig = channelConfig;
            return this;
        }

        /**
         * Compression level for flac encoder. Default 5.
         *
         * @param compressionLevel Int in range 0 to 8.
         * @return this
         * @see <a href="https://xiph.org/flac/api/group__flac__stream__encoder.html#gae49cf32f5256cb47eecd33779493ac85">FLAC__stream_encoder_set_compression_level</a>
         */
        public Builder compressionLevel(@IntRange(from = 0, to = 8) int compressionLevel) {
            this.compressionLevel = compressionLevel;
            return this;
        }

        /**
         * Full path of out file.
         *
         * @param outFile Out put file.
         * @return this
         */
        public Builder outFile(String outFile) {
            this.outFile = outFile;
            return this;
        }

        public FlacRecorder build() {
            if (TextUtils.isEmpty(outFile)) {
                throw new IllegalArgumentException("Output file must be supplied.");
            }
            if (compressionLevel < 0 || compressionLevel > 8) {
                throw new IllegalArgumentException("Compression level should be between 0 and 8");
            }
            return new FlacRecorder(this);
        }
    }

    public interface OnErrorListener {
        void onError();
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            STATE_INIT,
            STATE_RUNNING,
            STATE_FINISHED,
            STATE_ERROR
    })
    public @interface State {
    }
}
