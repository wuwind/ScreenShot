package com.wuwind.conn;

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by zx315476228 on 17-3-14.
 */

public class TcpDataServerThread extends Thread {
    private final String TAG = TcpDataServerThread.class.getSimpleName();
    private final int PORT = 6111;
    private BufferedOutputStream dataOutputStream;
    private boolean isStart = true;
    private Socket mClientSocket;

    public void close() {
        isStart = false;
    }

    @Override
    public void run() {
        super.run();
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            while (isStart) {
                Log.e(TAG, "accept");
                Socket clientSocket = serverSocket.accept();
                initSocket(clientSocket);
            }
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initSocket(Socket clientSocket) {
        Log.e(TAG, "initSocket");
        try {
            closeClient();
            mClientSocket = clientSocket;
            dataOutputStream = new BufferedOutputStream(clientSocket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
            closeClient();
        }
    }

    private void closeClient() {
        if (null != dataOutputStream) {
            try {
                dataOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (null != mClientSocket) {
            try {
                mClientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mClientSocket = null;
        dataOutputStream = null;
    }

    public void sendMessage(byte[] data) {
        if (null == dataOutputStream)
            return;
        byte[] content = new byte[data.length + 4];
        System.arraycopy(intToBytes(data.length), 0, content, 0, 4);
        System.arraycopy(data, 0, content, 4, data.length);
        try {
            dataOutputStream.write(content, 0, content.length);
            dataOutputStream.flush();
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

}
