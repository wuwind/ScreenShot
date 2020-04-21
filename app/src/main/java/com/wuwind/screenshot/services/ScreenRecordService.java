package com.wuwind.screenshot.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;

import com.libwuwind.player.VideoUtils;
import com.wuwind.conn.TcpSendThread;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenRecordService extends Service {

    private static final String TAG = "ScreenRecordingService";

    private int mScreenWidth;
    private int mScreenHeight;
    private int mScreenDensity;
    private int mResultCode;
    private Intent mResultData;
    /**
     * 是否为标清视频
     */
    private boolean isVideoSd;
    /**
     * 是否开启音频录制
     */
    private boolean isAudio;

    private MediaProjection mMediaProjection;
    private MediaRecorder mMediaRecorder;
    private VirtualDisplay mVirtualDisplay;

    private TcpSendThread tcpSendThread;

    private MediaCodec mEncoder;
    private Surface mSurface;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service onCreate() is called");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service onStartCommand() is called");

        mResultCode = intent.getIntExtra("code", -1);
        mResultData = intent.getParcelableExtra("data");
        mScreenWidth = intent.getIntExtra("width", 720);
        mScreenHeight = intent.getIntExtra("height", 1280);
        mScreenDensity = intent.getIntExtra("density", 1);
        isVideoSd = intent.getBooleanExtra("quality", true);
        isAudio = intent.getBooleanExtra("audio", true);

        tcpSendThread = new TcpSendThread(new TcpSendThread.OnConnCallBack() {
            @Override
            public void onConnSuccess(String ip) {
                Log.i(TAG, "TcpSendThread onConnSuccess");
            }
        });
        tcpSendThread.start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                mMediaProjection = createMediaProjection();
//        mMediaRecorder = createMediaRecorder();
                try {
                    prepareEncoder();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mVirtualDisplay = createVirtualDisplay(); // 必须在mediaRecorder.prepare() 之后调用，否则报错"fail to get surface"
                recordVirtualDisplay();
//        mMediaRecorder.start();
            }
        }).start();


        return Service.START_NOT_STICKY;
    }

    private AtomicBoolean mQuit = new AtomicBoolean(false);
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    public byte[] configbyte;

    private void recordVirtualDisplay() {
        while (!mQuit.get()) {
            int eobIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US);
            switch (eobIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d(TAG, "VideoSenderThread,MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED");
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
//                    LogTools.d("VideoSenderThread,MediaCodec.INFO_TRY_AGAIN_LATER");
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d(TAG, "VideoSenderThread,MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:" +
                            mEncoder.getOutputFormat().toString());
//                    sendAVCDecoderConfigurationRecord(0, mEncoder.getOutputFormat());
                    break;
                default:
                    Log.d(TAG, "VideoSenderThread,MediaCode,eobIndex=" + eobIndex);
                    Log.d(TAG, "VideoSenderThread,MediaCode,mBufferInfo.flags=" + mBufferInfo.flags);
                    Log.d(TAG, "VideoSenderThread,MediaCode,mBufferInfo.size=" + mBufferInfo.size);
                    Log.d(TAG, "VideoSenderThread,MediaCode,mBufferInfo.offset=" + mBufferInfo.offset);

                    ByteBuffer outputBuffer = mEncoder.getOutputBuffers()[eobIndex];
                    byte[] outData = new byte[mBufferInfo.size];
                    outputBuffer.get(outData);
                    if (mBufferInfo.flags == 2) {
                        configbyte = new byte[mBufferInfo.size];
                        configbyte = outData;
                    } else if (mBufferInfo.flags == 1) {
                        byte[] keyframe = new byte[mBufferInfo.size + configbyte.length];
                        System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
                        System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);
//                        MainActivity.putData(keyframe,1,mBufferInfo.presentationTimeUs*1000L);
//                        if(outputStream != null){
//                            outputStream.write(keyframe, 0, keyframe.length);
//                        }
                        save(keyframe);
                        Log.e(TAG, "keyframe");
                    } else {
//                        MainActivity.putData(outData,2,mBufferInfo.presentationTimeUs*1000L);
//                        if(outputStream != null){
//                            outputStream.write(outData, 0, outData.length);
//                        }
                        save(outData);
                    }

//                    if (startTime == 0) {
//                        startTime = mBufferInfo.presentationTimeUs / 1000;
//                    }
                    /**
                     * we send sps pps already in INFO_OUTPUT_FORMAT_CHANGED
                     * so we ignore MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                     */
//                    if (mBufferInfo.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG && mBufferInfo.size != 0) {
//                        ByteBuffer realData = mEncoder.getOutputBuffers()[eobIndex];
//                        realData.position(mBufferInfo.offset + 4);
//                        realData.limit(mBufferInfo.offset + mBufferInfo.size);
//                        byte[] datas = new byte[mBufferInfo.size];
//                        realData.get(datas);
//                        save(datas);
////                        sendRealData((mBufferInfo.presentationTimeUs / 1000) - startTime, realData);
//                    }
                    mEncoder.releaseOutputBuffer(eobIndex, false);
                    break;
            }
        }
    }

    FileOutputStream out;
    public long time;
    public boolean isPause = false;

    private boolean save(byte[] bytes) {
//        MainActivity.onFrame(bytes);
//        if(time > 0) {
////            Log.e(TAG, "save-----------:"+bytes.length);
////            Log.e(TAG, "save-----------:"+bytes[0]+"  "+bytes[bytes.length-1]);
//            return false;
//        }
//        if(isPause) {
//            return false;
//        }
            time ++;
        tcpSendThread.sendMessage(bytes);
//            VideoUtils.input(bytes);

//        try {
//            if (out == null) {
//                File f = new File(Environment.getExternalStoragePublicDirectory("Movies") + "/2.h264");
//                if (f.exists()) {
//                    f.delete();
//                    try {
//                        f.createNewFile();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//                out = new FileOutputStream(f);
//                time = System.currentTimeMillis();
//                Log.e(TAG, "--------save-----------" + f.getAbsolutePath());
//            }
//            if (System.currentTimeMillis() - time > 10000) {
//                out.flush();
//                out.close();
//                Log.e(TAG, "--------close-----------");
//                return false;
//            }
//            out.write(bytes);
//            VideoUtils.input(bytes);
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//            try {
//                out.close();
//            } catch (IOException ex) {
//                ex.printStackTrace();
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//            try {
//                out.close();
//            } catch (IOException ex) {
//                ex.printStackTrace();
//            }
//        }
        return true;
    }

    private MediaProjection createMediaProjection() {
        Log.i(TAG, "Create MediaProjection");
        return ((MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE)).getMediaProjection(mResultCode, mResultData);
    }

    private MediaRecorder createMediaRecorder() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        Date curDate = new Date(System.currentTimeMillis());
        String curTime = formatter.format(curDate).replace(" ", "");
        String videoQuality = "HD";
        if (isVideoSd) videoQuality = "SD";

        Log.i(TAG, "Create MediaRecorder");
        MediaRecorder mediaRecorder = new MediaRecorder();
        if (isAudio) mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setOutputFile(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + videoQuality + curTime + ".mp4");
        mediaRecorder.setVideoSize(mScreenWidth, mScreenHeight);  //after setVideoSource(), setOutFormat()
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);  //after setOutputFormat()
        if (isAudio)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);  //after setOutputFormat()
        int bitRate;
        if (isVideoSd) {
            mediaRecorder.setVideoEncodingBitRate(mScreenWidth * mScreenHeight);
            mediaRecorder.setVideoFrameRate(30);
            bitRate = mScreenWidth * mScreenHeight / 1000;
        } else {
            mediaRecorder.setVideoEncodingBitRate(5 * mScreenWidth * mScreenHeight);
            mediaRecorder.setVideoFrameRate(60); //after setVideoSource(), setOutFormat()
            bitRate = 5 * mScreenWidth * mScreenHeight / 1000;
        }
        try {
            mediaRecorder.prepare();
        } catch (IllegalStateException | IOException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "Audio: " + isAudio + ", SD video: " + isVideoSd + ", BitRate: " + bitRate + "kbps");

        return mediaRecorder;
    }

    private Surface createSurface() {
        SurfaceTexture mSurfaceTexture = new SurfaceTexture(123);
        Surface mSurface = new Surface(mSurfaceTexture);
        mSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                Log.e(TAG, "onFrameAvailable");
            }
        });
        return mSurface;
    }

    // parameters for the encoder
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 30; // 30 fps
    private static final int IFRAME_INTERVAL = 2; // 2 seconds between I-frames
    private static final int TIMEOUT_US = 10000;

    private void prepareEncoder() throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mScreenWidth, mScreenHeight);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 6000000);
//        format.setInteger(MediaFormat.KEY_BIT_RATE, mScreenWidth*mScreenHeight);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
//        format.setInteger(MediaFormat.KEY_FRAME_RATE, 20);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
//        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        Log.d(TAG, "created video format: " + format);
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mSurface = mEncoder.createInputSurface();
        Log.d(TAG, "created input surface: " + mSurface);
        mEncoder.start();
    }

    //    MediaProjection -> VirtualDisplay -> MediaCodec
//    MediaCodec -> Surface(created by myself) -> Surface(Created by MediaCodec)
    private VirtualDisplay createVirtualDisplay() {
        Log.i(TAG, "Create VirtualDisplay");
        return mMediaProjection.createVirtualDisplay(TAG, mScreenWidth, mScreenHeight, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mSurface, null, null);
//                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mMediaRecorder.getSurface(), null, null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Service onDestroy");
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mMediaRecorder != null) {
            mMediaRecorder.setOnErrorListener(null);
            mMediaProjection.stop();
            mMediaRecorder.reset();
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}