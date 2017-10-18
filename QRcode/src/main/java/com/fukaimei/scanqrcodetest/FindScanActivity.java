package com.fukaimei.scanqrcodetest;

import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.widget.Toast;

import com.fukaimei.scanqrcodetest.zxing.camera.CameraManager;
import com.fukaimei.scanqrcodetest.zxing.decoding.CaptureActivityHandler;
import com.fukaimei.scanqrcodetest.zxing.decoding.InactivityTimer;
import com.fukaimei.scanqrcodetest.zxing.view.ViewfinderView;
import com.google.zxing.Result;

public class FindScanActivity extends Activity implements SurfaceHolder.Callback {
    private final static String TAG = "FindScanActivity";
    private CaptureActivityHandler mHandler;
    private ViewfinderView vv_finder;
    private boolean hasSurface = false;
    private InactivityTimer mTimer;
    private MediaPlayer mPlayer;
    private boolean bBeep;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_find_scan);
        // 获取相机的动态权限
        cameraPermissions();
        CameraManager.init(getApplication(), CameraManager.QR_CODE);
        vv_finder = (ViewfinderView) findViewById(R.id.vv_finder);
        mTimer = new InactivityTimer(this);
    }

    // 定义获取相机的动态权限
    private void cameraPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    android.Manifest.permission.CAMERA}, 1);
        }
    }

    /**
     * 重写onRequestPermissionsResult方法
     * 获取动态权限请求的结果,再开启相机扫码
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            CameraManager.init(getApplication(), CameraManager.QR_CODE);
        } else {
            Toast.makeText(this, "用户拒绝了权限", Toast.LENGTH_SHORT).show();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SurfaceView sv_scan = (SurfaceView) findViewById(R.id.sv_scan);
        SurfaceHolder surfaceHolder = sv_scan.getHolder();
        if (hasSurface) {
            initCamera(surfaceHolder);
        } else {
            surfaceHolder.addCallback(this);
        }
        bBeep = true;
        AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            bBeep = false;
        }
        initBeepSound();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mHandler != null) {
            mHandler.quitSynchronously();
            mHandler = null;
        }
        CameraManager.get().closeDriver();
    }

    @Override
    protected void onDestroy() {
        mTimer.shutdown();
        super.onDestroy();
    }

    public void handleDecode(Result result, Bitmap barcode) {
        mTimer.onActivity();
        beepAndVibrate();
        String resultString = result.getText();
        if (resultString == null || resultString.length() <= 0) {
            Toast.makeText(this, "Scan failed or result is null", Toast.LENGTH_SHORT).show();
        } else {
            String desc = String.format("barcode width=%d,height=%d",
                    barcode.getWidth(), barcode.getHeight());
//            Toast.makeText(this, desc, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, ScanResultActivity.class);
            intent.putExtra("result", resultString);
            startActivity(intent);
        }
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        try {
            CameraManager.get().openDriver(surfaceHolder);
            if (mHandler == null) {
                mHandler = new CaptureActivityHandler(this, null, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
    }

    public ViewfinderView getViewfinderView() {
        return vv_finder;
    }

    public Handler getHandler() {
        return mHandler;
    }

    public void drawViewfinder() {
        vv_finder.drawViewfinder();
    }

    private void initBeepSound() {
        if (bBeep && mPlayer == null) {
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            mPlayer = new MediaPlayer();
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayer.setOnCompletionListener(beepListener);
            AssetFileDescriptor file = getResources().openRawResourceFd(R.raw.beep);
            try {
                mPlayer.setDataSource(file.getFileDescriptor(),
                        file.getStartOffset(), file.getLength());
                file.close();
                mPlayer.setVolume(0.1f, 0.1f);
                mPlayer.prepare();
            } catch (IOException e) {
                e.printStackTrace();
                mPlayer = null;
            }
        }
    }

    private static final long VIBRATE_DURATION = 200L;

    private void beepAndVibrate() {
        if (bBeep && mPlayer != null) {
            mPlayer.start();
        }
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        vibrator.vibrate(VIBRATE_DURATION);
    }

    private final OnCompletionListener beepListener = new OnCompletionListener() {
        public void onCompletion(MediaPlayer mPlayer) {
            mPlayer.seekTo(0);
        }
    };

}