package com.wuwind.screenshot;

import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.libwuwind.player.VideoUtils;
import com.wuwind.conn.TcpServerThread;

public class ServerActivity extends Activity {

    private SurfaceView surface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);
        surface = findViewById(R.id.surface);
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

        new TcpServerThread(new TcpServerThread.onFrameCallBack() {
            @Override
            public void onFrame(final byte[] data) {
                VideoUtils.input(data);
            }
        }).start();
    }
}
