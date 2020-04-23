package com.libwuwind.player;

import android.view.Surface;

/**
 * Created by wuhf on 2020/4/14.
 * Description ：
 */
public class VideoUtils {

    static {
        System.loadLibrary("avutil-55");
        System.loadLibrary("swresample-2");
        System.loadLibrary("avcodec-57");
        System.loadLibrary("avformat-57");
        System.loadLibrary("swscale-4");
        System.loadLibrary("postproc-54");
        System.loadLibrary("avfilter-6");
        System.loadLibrary("avdevice-57");
        System.loadLibrary("yuv");
        System.loadLibrary("native-lib");
    }

    public static native void input(byte[] datas);

    public static native void init(Surface surface);

    public static native void deInit();

    public static native void show();
}
