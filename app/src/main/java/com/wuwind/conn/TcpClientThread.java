package com.wuwind.conn;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by zx315476228 on 17-3-14.
 */

public class TcpClientThread extends Thread {
    private final int port = 6111;
    private String ip = "172.18.6.8";
    private BufferedInputStream inputStream;
    private BufferedOutputStream outputStream;
    private boolean isRuning;
    private Socket socket;
    private onFrameCallBack callBack;
    private long timer_size;
    private Timer timer = new Timer();

    public interface onFrameCallBack {
        void onFrame(byte[] data);
    }

    private TimerTask timerTask = new TimerTask() {
        @Override
        public void run() {
            Log.e("---", "接收速度:" + (timer_size / 1024) + "kb/s");
            timer_size = 0;
        }
    };

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setOnSendCallBack(onFrameCallBack callBack) {
        this.callBack = callBack;
    }

    public interface OnConnCallBack {
        void onConnSuccess(String ip);
    }

    public TcpClientThread(onFrameCallBack callBack) {
        this.callBack = callBack;
    }

    @Override
    public void run() {
        super.run();
        try {
            Log.e("---", "等待连接");
            socket = new Socket(ip, port);
            Log.e("---", "连接成功");
            timer.schedule(timerTask, 0, 1000);
            inputStream = new BufferedInputStream(socket.getInputStream());
            outputStream = new BufferedOutputStream(socket.getOutputStream());
            isRuning = true;
            while (isRuning) {
                int readsize = inputStream.available();
                int ret = 0;
                if (readsize < 4) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }
                byte[] tmpArray = new byte[4];
                do {
                    ret += inputStream.read(tmpArray, ret, 4 - ret);
                } while (ret < 4);
                paseTeacherMessage(tmpArray);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        close();
    }

    private void paseTeacherMessage(byte[] data) {
        int size = bytesToInt(data, 0);//帧大小
        timer_size += size;
        byte[] tmpArray = new byte[size];
        int ret = 0;
        try {
            do {
                ret += inputStream.read(tmpArray, ret, size - ret);
            } while (ret < size);
            callBack.onFrame(tmpArray);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
        }
    }

    public static int bytesToInt(byte[] ary, int offset) {
        int value;
        value = (int) ((ary[offset] & 0xFF)
                | ((ary[offset + 1] << 8) & 0xFF00)
                | ((ary[offset + 2] << 16) & 0xFF0000)
                | ((ary[offset + 3] << 24) & 0xFF000000));
        return value;
    }

    private void closeConn() {

        try {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(byte[] data) {
        if (!isRuning)
            return;
        byte[] content = new byte[data.length + 4];
        System.arraycopy(intToBytes(data.length), 0, content, 0, 4);
        System.arraycopy(data, 0, content, 4, data.length);
        try {
            outputStream.write(content, 0, content.length);
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static byte[] intToBytes(int value) {
        byte[] byte_src = new byte[4];
        byte_src[3] = (byte) ((value & 0xFF000000) >> 24);
        byte_src[2] = (byte) ((value & 0x00FF0000) >> 16);
        byte_src[1] = (byte) ((value & 0x0000FF00) >> 8);
        byte_src[0] = (byte) ((value & 0x000000FF));
        return byte_src;
    }

    public static byte[] long2Bytes(long num) {
        byte[] byteNum = new byte[8];
        for (int ix = 0; ix < 8; ++ix) {
            int offset = 64 - (ix + 1) * 8;
            byteNum[ix] = (byte) ((num >> offset) & 0xff);
        }
        return byteNum;
    }

    public void close() {
        isRuning = false;
        if (null != timer)
            timer.cancel();
        if(null != socket) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        socket = null;
    }
}
