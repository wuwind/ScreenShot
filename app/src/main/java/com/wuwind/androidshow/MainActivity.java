package com.wuwind.androidshow;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.wuwind.androidshow.media.h264data;
import com.wuwind.androidshow.rtsp.RtspServer;
import com.wuwind.androidshow.screen.ScreenRecord;
import com.wuwind.screenshot.R;

import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by zpf on 2018/3/6
 *
 * MediaProjectionManager流程
 * 1.调用Context.getSystemService()方法即可获取MediaProjectionManager实例
 * 2.调用MediaProjectionManager对象的createScreenCaptureIntent()方法创建一个屏幕捕捉的Intent
 * 3.调用startActivityForResult()方法启动第2步得到的Intent，这样即可启动屏幕捕捉的Intent
 * 4.重写onActivityResult()方法，在该方法中通过MediaProjectionManager对象来获取MediaProjection对象，在该对象中即可获取被捕获的屏幕
 * **/
public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    public static final int REQUEST_CODE_A = 10001;

    private Button start_record,stop_record;

    private TextView line2;

    private MediaProjectionManager mMediaProjectionManager;

    private ScreenRecord mScreenRecord;

    private boolean isRecording = false;

    private static int queuesize = 30;
    public static ArrayBlockingQueue<h264data> h264Queue = new ArrayBlockingQueue<>(queuesize);
    private RtspServer mRtspServer;
    private String RtspAddress;
    private final static int PERMISSIONS_OK = 10001;
    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.RECORD_AUDIO",
            "android.permission.CAMERA",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE" };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        InitView();
        if (Build.VERSION.SDK_INT>22) {
            if (!checkPermissionAllGranted(PERMISSIONS_STORAGE)) {
                //先判断有没有权限 ，没有就在这里进行权限的申请
                ActivityCompat.requestPermissions(MainActivity.this,
                        PERMISSIONS_STORAGE, PERMISSIONS_OK);
            }else{
                init();
            }
        }else{
            init();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_OK:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //这里已经获取到了摄像头的权限，想干嘛干嘛了可以
                    init();
                } else {
                    showWaringDialog();
                }
                break;
            default:
                break;
        }
    }

    private void showWaringDialog() {
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("警告！")
                .setMessage("请前往设置->应用->PermissionDemo->权限中打开相关权限，否则功能无法正常运行！")
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 一般情况下如果用户不授权的话，功能是无法运行的，做退出处理
                        finish();
                    }
                }).show();
    }

    private void init(){
        InitMPManager();
        RtspAddress = displayIpAddress();
        if(RtspAddress != null){
            line2.setText(RtspAddress);
        }
    }

    private boolean checkPermissionAllGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                // 只要有一个权限没有被授予, 则直接返回 false
                return false;
            }
        }
        return true;
    }

    private ServiceConnection mRtspServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mRtspServer = ((RtspServer.LocalBinder)service).getService();
            mRtspServer.addCallbackListener(mRtspCallbackListener);
            mRtspServer.start();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };

    @Override
    protected void onResume(){
        super.onResume();
    }

    private RtspServer.CallbackListener mRtspCallbackListener = new RtspServer.CallbackListener() {

        @Override
        public void onError(RtspServer server, Exception e, int error) {
            // We alert the user that the port is already used by another app.
            if (error == RtspServer.ERROR_BIND_FAILED) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Port already in use !")
                        .setMessage("You need to choose another port for the RTSP server !")
                        .show();
            }
        }

        @Override
        public void onMessage(RtspServer server, int message) {
            if (message==RtspServer.MESSAGE_STREAMING_STARTED) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this,"RTSP STREAM STARTED",Toast.LENGTH_SHORT).show();
                    }
                });
            } else if (message==RtspServer.MESSAGE_STREAMING_STOPPED) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this,"RTSP STREAM STOPPED",Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    };


    public static void putData(byte[] buffer, int type,long ts) {
        if (h264Queue.size() >= queuesize) {
            h264Queue.poll();
        }
        h264data data = new h264data();
        data.data = buffer;
        data.type = type;
        data.ts = ts;
        h264Queue.add(data);
    }

    /**
     * 初始化View
     * **/
    private void InitView(){
        start_record = findViewById(R.id.start_record);
        start_record.setOnClickListener(this);
        stop_record = findViewById(R.id.stop_record);
        stop_record.setOnClickListener(this);
        line2 = (TextView)findViewById(R.id.line2);
    }

    /**
     * 初始化MediaProjectionManager
     * **/
    private void InitMPManager(){
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    }


    /**
     * 开始截屏
     * **/
    private void StartScreenCapture(){
//        if(RtspAddress != null && !RtspAddress.isEmpty()){
            isRecording = true;
            Intent captureIntent = mMediaProjectionManager.createScreenCaptureIntent();
            startActivityForResult(captureIntent, REQUEST_CODE_A);
//            bindService(new Intent(this,RtspServer.class), mRtspServiceConnection, Context.BIND_AUTO_CREATE);
//        }else{
//            Toast.makeText(this,"网络连接异常！",Toast.LENGTH_SHORT).show();
//        }
    }


    /**
     * 停止截屏
     * **/
    private void StopScreenCapture(){
        isRecording = false;
        mScreenRecord.release();
        if (mRtspServer != null)
            mRtspServer.removeCallbackListener(mRtspCallbackListener);
        unbindService(mRtspServiceConnection);
    }


    /**
     *
     * **/
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            MediaProjection mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
            if (mediaProjection == null) {
                Toast.makeText(this, "程序发生错误:MediaProjection@1", Toast.LENGTH_SHORT).show();
                return;
            }
            mScreenRecord = new ScreenRecord(this, mediaProjection);
            mScreenRecord.start();
        } catch (Exception e) {

        }
    }


    @Override
    public void onClick(View view) {
        switch (view.getId())
        {
            case R.id.start_record:
                StartScreenCapture();
                break;
            case R.id.stop_record:
                StopScreenCapture();
                break;
        }
    }


    /**
     * 先判断网络情况是否良好
     * */
    private String displayIpAddress() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
//        WifiInfo info = wifiManager.getConnectionInfo();
        String ipaddress = "";
//        if (info!=null && info.getNetworkId()>-1) {
//            int i = info.getIpAddress();
//            String ip = String.format(Locale.ENGLISH,"%d.%d.%d.%d", i & 0xff, i >> 8 & 0xff,i >> 16 & 0xff,i >> 24 & 0xff);
//            ipaddress += "rtsp://";
//            ipaddress += ip;
//            ipaddress += ":";
//            ipaddress += RtspServer.DEFAULT_RTSP_PORT;
//        }
        return ipaddress;
    }
}
