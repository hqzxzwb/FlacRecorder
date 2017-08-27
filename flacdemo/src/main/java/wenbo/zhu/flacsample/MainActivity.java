package wenbo.zhu.flacsample;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import wenbo.zhu.flac.FlacPlayer;
import wenbo.zhu.flac.FlacRecorder;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSION_RECORD = 1;
    private FlacRecorder workingRecorder;

    private File file;

    private TextView stateTextView;
    private Button startButton;
    private Button stopButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        stateTextView = (TextView) findViewById(R.id.txtState);
        startButton = (Button) findViewById(R.id.btnStart);
        stopButton = (Button) findViewById(R.id.btnStop);

        File dir = getExternalFilesDir("record");
        dir.mkdirs();
        file = new File(dir, "recording.flac");
    }

    private void recordAudio() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED) {
            recordAudioUnchecked();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_PERMISSION_RECORD);
        }
    }

    private void recordAudioUnchecked() {
        FlacRecorder recorder = new FlacRecorder.Builder()
                .audioSource(MediaRecorder.AudioSource.MIC)
                .sampleRateInHz(44100)
                .channelConfig(AudioFormat.CHANNEL_IN_MONO)
                .compressionLevel(5)
                .outFile(file.getAbsolutePath())
                .build();
        recorder.setOnErrorListener(new FlacRecorder.OnErrorListener() {
            @Override
            public void onError() {
                stateTextView.setText("录音出错");
                setButtonState(true);
            }
        });
        recorder.start();
        workingRecorder = recorder;
        stateTextView.setText("录音中……");
        setButtonState(false);
    }

    private void setButtonState(boolean canStart) {
        startButton.setEnabled(canStart);
        stopButton.setEnabled(!canStart);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_RECORD) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                recordAudioUnchecked();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void start(View view) {
        recordAudio();
    }

    public void stop(View view) {
        workingRecorder.stop();
        stateTextView.setText("录音完成");
        setButtonState(true);
    }

    public void play(View view) {
        MediaPlayer mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void playWithDecoder(View view) {
        FlacPlayer player = new FlacPlayer();
        if (!player.init(file.getAbsolutePath())) {
            Log.e(TAG, "Player init error.");
            return;
        }
    }
}
