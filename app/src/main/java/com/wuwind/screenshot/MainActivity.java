package com.wuwind.screenshot;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.libwuwind.player.VideoUtils;
import com.wuwind.conn.FrpcManager;
import com.wuwind.screenshot.services.ScreenRecordService;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    public void record(View view) {

    }

    private static final String TAG = "MainActivity";

    private TextView mTextView;

    private static final String RECORD_STATUS = "record_status";
    private static final int REQUEST_CODE = 1000;

    private int mScreenWidth;
    private int mScreenHeight;
    private int mScreenDensity;

    /**
     * 是否已经开启视频录制
     */
    private boolean isStarted = false;
    /**
     * 是否为标清视频
     */
    private boolean isVideoSd = true;
    /**
     * 是否开启音频录制
     */
    private boolean isAudio = true;

    // Used to load the 'native-lib' library on application startup.

    private SurfaceView surface;
    private static MediaCodec decoder;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        surface = findViewById(R.id.surface);
        surface.getHolder().addCallback(this);
        if (savedInstanceState != null) {
            isStarted = savedInstanceState.getBoolean(RECORD_STATUS);
        }
        getView();
        getScreenBaseInfo();
        requestPermissions(new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        }, 1);
        checkPermission(this);
    }

    private void checkPermission(Activity activity) {
        // Storage Permissions
        final int REQUEST_EXTERNAL_STORAGE = 1;
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE};

        try {
            //检测是否有写的权限
            int permission = ActivityCompat.checkSelfPermission(this,
                    "android.permission.WRITE_EXTERNAL_STORAGE");
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // 没有写的权限，去申请写的权限，会弹出对话框
                ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void getView() {
        findViewById(R.id.play).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                String path = Environment.getExternalStoragePublicDirectory("Movies") + "/2.h264";
//                VideoUtils.play(path,surface.getHolder().getSurface());
//                ScreenRecordService.time = 0;
//                VideoUtils.show();
                startScreenRecording();
            }
        });
        findViewById(R.id.start_thread).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
        findViewById(R.id.clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                decoder.stop();
                clearDraw();
            }
        });
        findViewById(R.id.stop_record).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopScreenRecording();
            }
        });
        findViewById(R.id.frpc_start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new FrpcManager(MainActivity.this).frpStart();
            }
        });
        findViewById(R.id.home).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addCategory(Intent.CATEGORY_HOME);
                startActivity(intent);
            }
        });
        mTextView = (TextView) findViewById(R.id.init);
        if (isStarted) {
            statusIsStarted();
        } else {
            statusIsStoped();
        }
        mTextView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

//                initMediaDecoder(surface.getHolder().getSurface());
                new Thread(new Runnable() {
                    @Override
                    public void run() {
//                        String path = Environment.getExternalStorageDirectory() + "/1.h264";
                        VideoUtils.init(surface.getHolder().getSurface());

                    }
                }).start();

            }
        });

        RadioGroup radioGroup = (RadioGroup) findViewById(R.id.radio_group);
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // TODO Auto-generated method stub
                switch (checkedId) {
                    case R.id.sd_button:
                        isVideoSd = true;
                        break;
                    case R.id.hd_button:
                        isVideoSd = false;
                        break;

                    default:
                        break;
                }
            }
        });

        CheckBox audioBox = (CheckBox) findViewById(R.id.audio_check_box);
        audioBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // TODO Auto-generated method stub
                isAudio = isChecked;
            }
        });
    }

    public void clearDraw() {
        Canvas canvas = null;
        try {
            canvas = surface.getHolder().lockCanvas(null);
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            Paint p = new Paint();
            canvas.drawColor(Color.WHITE, PorterDuff.Mode.SRC);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (canvas != null) {
                surface.getHolder().unlockCanvasAndPost(canvas);
            }
        }
    }

    /**
     * 开启屏幕录制时的UI状态
     */
    private void statusIsStarted() {
//        mTextView.setText("stop");
//        mTextView.setBackgroundDrawable(getResources().getDrawable(R.drawable.selector_red_bg));
    }

    /**
     * 结束屏幕录制后的UI状态
     */
    private void statusIsStoped() {
//        mTextView.setText("start");
//        mTextView.setBackgroundDrawable(getResources().getDrawable(R.drawable.selector_green_bg));
    }

    /**
     * 获取屏幕相关数据
     */
    private void getScreenBaseInfo() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenWidth = metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;
        mScreenDensity = metrics.densityDpi;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(RECORD_STATUS, isStarted);
    }

    /**
     * 获取屏幕录制的权限
     */
    private void startScreenRecording() {
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent permissionIntent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(permissionIntent, REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // 获得权限，启动Service开始录制
                Intent service = new Intent(this, ScreenRecordService.class);
                service.putExtra("code", resultCode);
                service.putExtra("data", data);
                service.putExtra("audio", isAudio);
                service.putExtra("width", mScreenWidth);
                service.putExtra("height", mScreenHeight);
                service.putExtra("density", mScreenDensity);
                service.putExtra("quality", isVideoSd);
                startService(service);
                // 已经开始屏幕录制，修改UI状态
                isStarted = !isStarted;
                statusIsStarted();
                simulateHome(); // this.finish();  // 可以直接关闭Activity
                Log.i(TAG, "Started screen recording");
            } else {
                Toast.makeText(this, "user_cancelled", Toast.LENGTH_LONG).show();
                Log.i(TAG, "User cancelled");
            }
        }
    }

    /**
     * 关闭屏幕录制，即停止录制Service
     */
    private void stopScreenRecording() {
        // TODO Auto-generated method stub
        Intent service = new Intent(this, ScreenRecordService.class);
        stopService(service);
        isStarted = !isStarted;
    }

    /**
     * 模拟HOME键返回桌面的功能
     */
    private void simulateHome() {
//        Intent intent = new Intent(Intent.ACTION_MAIN);
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        intent.addCategory(Intent.CATEGORY_HOME);
//        this.startActivity(intent);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    private static int mCount = 0;
    private static int TIME_INTERNAL = 1000;

    public static void onFrame(byte[] buf) {
        if (null == decoder)
            return;
        ByteBuffer[] inputBuffers = decoder.getInputBuffers();
        int inputBufferIndex = decoder.dequeueInputBuffer(0);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(buf, 0, buf.length);
            decoder.queueInputBuffer(inputBufferIndex, 0, buf.length, mCount * 1000000 / TIME_INTERNAL, 0);
            mCount++;
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 0);
        while (outputBufferIndex >= 0) {
            decoder.releaseOutputBuffer(outputBufferIndex, true);
            outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 0);
        }
    }

    private void initMediaDecoder(Surface surface) {
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720);
        try {
            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            decoder.configure(mediaFormat, surface, null, 0);
            decoder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    @Override
//    public boolean onKeyDown(int keyCode, KeyEvent event) {
//        // 在这里将BACK键模拟了HOME键的返回桌面功能（并无必要）
//        if (keyCode == KeyEvent.KEYCODE_BACK) {
//            simulateHome();
//            return true;
//        }
//        return super.onKeyDown(keyCode, event);
//    }

}