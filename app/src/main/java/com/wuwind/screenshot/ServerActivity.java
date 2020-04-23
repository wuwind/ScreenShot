package com.wuwind.screenshot;

import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.libwuwind.player.VideoUtils;
import com.wuwind.conn.TcpClientThread;

public class ServerActivity extends Activity {

    private SurfaceView surface;
    private TcpClientThread tcpClientThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);
        surface = findViewById(R.id.surface);
        findViewById(R.id.finish).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VideoUtils.deInit();
                tcpClientThread.close();
                finish();
            }
        });
        surface.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        VideoUtils.init(surface.getHolder().getSurface());
                    }
                }).start();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });


        tcpClientThread = new TcpClientThread(new TcpClientThread.onFrameCallBack() {
            @Override
            public void onFrame(byte[] data) {
                VideoUtils.input(data);
            }
        });
        tcpClientThread.start();
    }

}
