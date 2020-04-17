#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <pthread.h>
#include <android/log.h>
#include <ffmpeg/libavutil/time.h>

//编码
#include "libavcodec/avcodec.h"
//封装格式处理
#include "libavformat/avformat.h"
//像素处理
#include "libswscale/swscale.h"

#include "libavfilter/avfilter.h"
#include "libavfilter/buffersrc.h"
#include "libavfilter/buffersink.h"
#include "libavutil/opt.h"

#include "android/native_window.h"
#include "android/native_window_jni.h"

#include "libyuv.h"

#include "queue0.h"

#include "libswresample/swresample.h"

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wmissing-noreturn"
#define LOGE(FORMAT, ...) __android_log_print(ANDROID_LOG_ERROR, "wuhf",  FORMAT, ##__VA_ARGS__)
#define LOGI(FORMAT, ...) __android_log_print(ANDROID_LOG_INFO, "wuhf",  FORMAT, ##__VA_ARGS__)

#define MAX_STREAM 4

#define MIN_SLEEP_TIME_US 1000ll
#define AUDIO_TIME_ADJUST_US -200000ll

char *chars = NULL;
int chars_len = 0;


typedef struct Player {
    AVFormatContext *input_format_ctx;
    int video_stream_idx;
    int audio_stream_idx;
    int stream_no;
    AVCodecContext *codecCtx[MAX_STREAM];
    ANativeWindow *window;
    char useFilter;
    AVFilterContext *buffersrc_ctx;
    AVFilterContext *buffersink_ctx;
    int showWidth;
    int showHeight;
    int rate;
    Queue *queues[MAX_STREAM];
    pthread_t thread_read_from_stream;
    pthread_t decode_threads[MAX_STREAM];
    //JNI
    jobject audioTrack;
    jmethodID audio_track_write_mid;

    SwrContext *swrCtx;
    int64_t out_ch_layout;
    int64_t out_sample_rate;
    enum AVSampleFormat out_sample_fmt;
    JavaVM *javaVM;

    pthread_mutex_t mutex;
    pthread_cond_t cond;

    //视频开始播放的时间
    int64_t start_time;

    int64_t audio_clock;
} Player;

typedef struct DecoderData {
    Player *player;
    int index;
} DecoderData;

struct Player *player;

void *player_fill_func() {
    AVPacket *pkt = av_malloc(sizeof(AVPacket));
    return pkt;
}

void init_queue(struct Player *player) {
    for (int i = 0; i < player->stream_no; ++i) {
        player->queues[i] = queue_init(50, player_fill_func);
    }
}


//读取数据的回调函数-------------------------
//AVIOContext使用的回调函数！
//注意：返回值是读取的字节数
//手动初始化AVIOContext只需要两个东西：内容来源的buffer，和读取这个Buffer到FFmpeg中的函数
//回调函数，功能就是：把buf_size字节数据送入buf即可
//第一个参数(void *opaque)一般情况下可以不用
int count = 0;

int fill_iobuffer(void *opaque, uint8_t *buf, int buf_size) {
//    LOGE("read %d  %d", chars[0], chars[chars_len- 1]);
    int ret = 0;
    if (count < 155 && chars_len > 0) {
        count++;
        ret = chars_len;
        chars_len = -1;
        memcpy(buf, chars, (size_t) ret);
    }
    if (ret > 0) {
        LOGE("fill_iobuffer ret %d count %d", ret, count);
    }
    return ret;
}

void init_input_format_ctx(struct Player *player) {

    //1.注册所有组件
    av_register_all();

    unsigned char *iobuffer = (unsigned char *) av_malloc(32768);
    AVIOContext *avio = avio_alloc_context(iobuffer, 32768, 0, NULL, fill_iobuffer, NULL, NULL);

    if (avio == NULL) {
        LOGE("%s", "avio == NULL");
        return;
    }

    //封装格式上下文，统领全局的结构体，保存了视频文件封装格式的相关信息
    AVFormatContext *avFmtCtx = avformat_alloc_context();
    avFmtCtx->pb = avio;
    avFmtCtx->flags = AVFMT_FLAG_CUSTOM_IO;

    //2.打开输入视频文件
//    if (avformat_open_input(&avFmtCtx, input_cstr, NULL, NULL) != 0) {
//        LOGE("%s", "无法打开输入视频文件");
//        return;
//    }

    // 这里一定要注意，in_fmt要是不设置，可能会导致avformat_open_input()或者avformat_find_stream_info()失败
    // 我使用的是音频转码，故此处填写音频的简称
    AVInputFormat *in_fmt = av_find_input_format("h264");
    int err = avformat_open_input(&avFmtCtx, "", in_fmt, NULL);
    LOGE("err%d", err);
    if (err != 0) {
        LOGE("%s", "无法打开输入文件");
        return;
    }
    LOGI("nb_streams: %s", "获取视频文件信息");
    //3.获取视频文件信息
    if (avformat_find_stream_info(avFmtCtx, NULL) < 0) {
        LOGE("%s", "无法获取视频文件信息");
        return;
    }
    int i = 0;
    //number of streams
    LOGI("nb_streams: %d", avFmtCtx->nb_streams);
    player->video_stream_idx = -1;
    player->audio_stream_idx = -1;
    player->stream_no = avFmtCtx->nb_streams;

    player->video_stream_idx = 0;
    player->stream_no = avFmtCtx->nb_streams = 1;
    player->showWidth = avFmtCtx->streams[i]->codec->width = 1280;
    player->showHeight = avFmtCtx->streams[i]->codec->height = 752;

    player->input_format_ctx = avFmtCtx;
    player->useFilter = 0;
}

void init_codec_ctx(struct Player *player, int index) {
    AVCodecContext *codecCtx = player->input_format_ctx->streams[index]->codec;
    AVCodec *codec = avcodec_find_decoder(codecCtx->codec_id);
    if (NULL == codec) {
        LOGE("%s", "找不到解码器\n");
        return;
    }
    //5.打开解码器
    if (avcodec_open2(codecCtx, codec, NULL) < 0) {
        LOGE("%s", "解码器无法打开\n");
        return;
    }
    player->codecCtx[index] = codecCtx;
}

void showAVFrame(AVFrame *filt_frame, struct Player *player) {

    ANativeWindow *window = player->window;
    ANativeWindow_Buffer outBuffer;
    AVFrame *pFrameRGBA = av_frame_alloc();
    ANativeWindow_setBuffersGeometry(window, player->showWidth, player->showHeight,
                                     WINDOW_FORMAT_RGBA_8888);
    ANativeWindow_lock(window, &outBuffer, NULL);

    avpicture_fill((AVPicture *) pFrameRGBA, outBuffer.bits,
                   AV_PIX_FMT_RGBA, player->showWidth, player->showHeight);
    I420ToARGB(filt_frame->data[0], filt_frame->linesize[0], filt_frame->data[2],
               filt_frame->linesize[2], filt_frame->data[1], filt_frame->linesize[1],
               pFrameRGBA->data[0], pFrameRGBA->linesize[0], filt_frame->width,
               filt_frame->height);
    LOGE("%s", "ANativeWindow_unlockAndPost");
    ANativeWindow_unlockAndPost(window);
}

void decode_video(struct Player *player, AVPacket *packet) {
    AVCodecContext *pCodecCtx = player->codecCtx[player->video_stream_idx];
    int got_picture, ret;
    //AVFrame用于存储解码后的像素数据(YUV)  内存分配
    AVFrame *pFrame = av_frame_alloc();
    AVFrame *filt_frame = av_frame_alloc();
    //YUV420
    AVFrame *pFrameRGBA2 = av_frame_alloc();
    //7.解码一帧视频压缩数据，得到视频像素数据
    ret = avcodec_decode_video2(pCodecCtx, pFrame, &got_picture, packet);
    if (ret < 0) {
        LOGE("%s", "解码错误");
        return;
    }
    //为0说明解码完成，非0正在解码  0没有可以解压的数据帧
    if (got_picture) {
//        LOGI("got_picture  %d", got_picture);
        //把解码后视频帧添加到filter graph
        if (player->useFilter) {
            if (av_buffersrc_add_frame_flags(player->buffersrc_ctx, pFrame,
                                             AV_BUFFERSRC_FLAG_KEEP_REF) < 0) {
                LOGE("Error while feeding the filter_graph\n");
                return;
            }
            //把滤波后的视频帧从filter graph取出来
            ret = av_buffersink_get_frame(player->buffersink_ctx, filt_frame);
            if (ret >= 0) {
                showAVFrame(filt_frame, player);
            }
        } else {
            showAVFrame(pFrame, player);
        }
//        usleep(1000 * 1000 / player->rate);
    }
    av_frame_free(&pFrame);
    av_frame_free(&filt_frame);
    av_frame_free(&pFrameRGBA2);
}

#define MAX_AUDIO_FRAME_SIZE 44100*2

void decode_audio(Player *player, AVPacket *packet) {
    AVCodecContext *codecCtx = player->codecCtx[player->audio_stream_idx];
    AVFrame *frame = av_frame_alloc();
    //输出buf
    uint8_t *outbuf = av_malloc(MAX_AUDIO_FRAME_SIZE);

    SwrContext *swrCtx = player->swrCtx;
    int got_frame, ret;
    ret = avcodec_decode_audio4(codecCtx, frame, &got_frame, packet);
    if (ret < 0) {
        LOGE("%s", "解码完成");
    }
    int nb_channels = av_get_channel_layout_nb_channels(player->out_ch_layout);
    if (got_frame != 0) {
        swr_convert(swrCtx, &outbuf, MAX_AUDIO_FRAME_SIZE, (const uint8_t **) frame->data,
                    frame->nb_samples);
        //获取sample的size
        int out_buf_size = av_samples_get_buffer_size(NULL, nb_channels, frame->nb_samples,
                                                      player->out_sample_fmt, 1);

        int64_t pts = packet->pts;
        if (pts != AV_NOPTS_VALUE) {
            player->audio_clock = av_rescale_q(pts,
                                               player->input_format_ctx->streams[player->audio_stream_idx]->time_base,
                                               AV_TIME_BASE_Q);
            //				av_q2d(stream->time_base) * pts;
            LOGE("player_write_audio - read from pts  audio_clock  %d", player->audio_clock);
        }

        JavaVM *javaVM = player->javaVM;
        JNIEnv *env;
        (*javaVM)->AttachCurrentThread(javaVM, &env, NULL);
        jbyteArray array = (*env)->NewByteArray(env, out_buf_size);
        jbyte *elements = (*env)->GetByteArrayElements(env, array, NULL);
        memcpy(elements, outbuf, out_buf_size);
        (*env)->ReleaseByteArrayElements(env, array, elements, 0);
        (*env)->CallIntMethod(env, player->audioTrack, player->audio_track_write_mid, array, 0,
                              out_buf_size);
        (*env)->DeleteLocalRef(env, array);
        (*javaVM)->DetachCurrentThread(javaVM);
//        usleep(1000 * 10);
    } else {
        LOGE("%s", "没有帧可以被解码");
    }
    av_frame_free(&frame);
}

//子线程解码
void *decode_data(void *arg) {
    struct DecoderData *decoderData = arg;
    int index = decoderData->index;
    for (;;) {
        pthread_mutex_lock(&player->mutex);
        LOGE("%s", "decode_data");
        AVPacket *pkt = queue_pop(player->queues[index], &player->mutex, &player->cond);
        pthread_mutex_unlock(&player->mutex);
        if (index == player->video_stream_idx) {
            decode_video(player, pkt);
        } else if (index == player->audio_stream_idx) {
            decode_audio(player, pkt);
        }
    }

}

void decode_video_prepare(JNIEnv *env, struct Player *player, jobject surface) {
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    player->window = window;
}

void destroy_player(struct Player *player) {

    ANativeWindow_release(player->window);
    int i = 0;
    for (i = 0; i < MAX_STREAM; i++) {
        if (NULL != player->codecCtx[i]) {
            LOGE("%d", player->codecCtx[i]);
//			avcodec_close(player->codecCtx[i]);
        }
    }
    avformat_free_context(player->input_format_ctx);
}

void *read_from_stream(void *arg) {
    AVPacket packet, *pkt = &packet;
    int ret;
    for (;;) {
//        if(!isInput) {
//            LOGE("%s %d", "read_from_stream break ", isInput);
//            continue;
//        }
        ret = av_read_frame(player->input_format_ctx, pkt);
        //到文件结尾了
        if (ret < 0) {
//            LOGE("%s", "read_from_stream break");
//            break;
            continue;
        }
//        if(pkt->stream_index == player->video_stream_idx)
//            continue;
        Queue *queue = player->queues[pkt->stream_index];
        pthread_mutex_lock(&player->mutex);
        LOGE("%s %d", "read_from_stream", pkt->size);
        AVPacket *q = queue_push(queue, &player->mutex, &player->cond);
        *q = packet;
        pthread_mutex_unlock(&player->mutex);
    }
    return 0;
}

JNIEXPORT void JNICALL Java_com_libwuwind_player_VideoUtils_show(JNIEnv *env, jclass clazz) {
    count = 154;
}

JNIEXPORT void JNICALL Java_com_libwuwind_player_VideoUtils_input(JNIEnv *env,
                                                                  jclass jcls,
                                                                  jbyteArray bytearray) {
//    if(chars_len != 0) {
//        LOGE("input chars_len != 0 %d", chars_len);
//        return;
//    }
    jbyte *bytes = (*env)->GetByteArrayElements(env, bytearray, 0);
    int len = (*env)->GetArrayLength(env, bytearray);
    memcpy(chars, bytes, (size_t) len);
//    for(int i=0;i<len;i++) {
//        *(chars+i)=(*(bytes+i));
//    }

    LOGE("len %d", len);
    LOGE("write %d   %d   %d", bytes[0], bytes[len - 1], bytes[len - 2], bytes[len - 3]);
    LOGE("write chars %d  %d   %d", chars[0], chars[len - 1], chars[len - 2], chars[len - 3]);
    chars_len = len;
//    (*env)->ReleaseByteArrayElements(env, bytearray, bytes, 0);
    //release
}

JNIEXPORT void JNICALL Java_com_libwuwind_player_VideoUtils_init(JNIEnv *env,
                                                                 jclass jcls,
                                                                 jobject surface) {

    chars = malloc(32768 * sizeof(char));

    //需要转码的视频文件(输入的视频文件)
//    const char *input_cstr = (*env)->GetStringUTFChars(env, input_jstr, NULL);

    player = malloc(sizeof(struct Player));
    (*env)->GetJavaVM(env, &(player->javaVM));


    init_input_format_ctx(player);
    init_queue(player);
    int video_stream_index = player->video_stream_idx;
//    int audio_stream_index = player->audio_stream_idx;
    init_codec_ctx(player, video_stream_index);
//    init_codec_ctx(player, audio_stream_index);//TODO
    decode_video_prepare(env, player, surface);
//    decode_audio_prepare(player);
//    jni_audio_prepare(env, player, jcls);

    pthread_mutex_init(&player->mutex, NULL);
    pthread_cond_init(&player->cond, NULL);
    player->start_time = 0;

    //创建读线程
    pthread_create(&player->thread_read_from_stream, NULL, read_from_stream, player);

    DecoderData data = {player, video_stream_index};
//    DecoderData data2 = {player, audio_stream_index};
    pthread_create(&player->decode_threads[video_stream_index], NULL, decode_data, &data);
//    pthread_create(&player->decode_threads[audio_stream_index], NULL, decode_data, &data2);

    pthread_join(player->thread_read_from_stream, NULL);
//    pthread_join(player->decode_threads[audio_stream_index], NULL);
//    pthread_join(player->decode_threads[audio_stream_index], NULL);

//
//	(*env)->ReleaseStringUTFChars(env, input_jstr, input_cstr);
//	destroy_player(player);

    LOGI("avcodec_close");
}

#pragma clang diagnostic pop