//#include <stdlib.h>
//#include <stdio.h>
//#include <unistd.h>
//#include <pthread.h>
//#include <android/log.h>
//#include <ffmpeg/libavutil/time.h>
//
////编码
//#include "libavcodec/avcodec.h"
////封装格式处理
//#include "libavformat/avformat.h"
////像素处理
//#include "libswscale/swscale.h"
//
//#include "libavfilter/avfilter.h"
//#include "libavfilter/buffersrc.h"
//#include "libavfilter/buffersink.h"
//#include "libavutil/opt.h"
//
//#include "android/native_window.h"
//#include "android/native_window_jni.h"
//
//#include "libyuv.h"
//
//#include "queue0.h"
//
//#include "libswresample/swresample.h"
//
//#define LOGE(FORMAT, ...) __android_log_print(ANDROID_LOG_ERROR, "wuhf",  FORMAT, ##__VA_ARGS__)
//#define LOGI(FORMAT, ...) __android_log_print(ANDROID_LOG_INFO, "wuhf",  FORMAT, ##__VA_ARGS__)
//
//#define MAX_STREAM 4
//
//#define MIN_SLEEP_TIME_US 1000ll
//#define AUDIO_TIME_ADJUST_US -200000ll
//
//typedef struct Player {
//    AVFormatContext *input_format_ctx;
//    int video_stream_idx;
//    int audio_stream_idx;
//    int stream_no;
//    AVCodecContext *codecCtx[MAX_STREAM];
//    ANativeWindow *window;
//    char useFilter;
//    AVFilterContext *buffersrc_ctx;
//    AVFilterContext *buffersink_ctx;
//    int showWidth;
//    int showHeight;
//    int rate;
//    Queue *queues[MAX_STREAM];
//    pthread_t thread_read_from_stream;
//    pthread_t decode_threads[MAX_STREAM];
//    //JNI
//    jobject audioTrack;
//    jmethodID audio_track_write_mid;
//
//    SwrContext *swrCtx;
//    int64_t out_ch_layout;
//    int64_t out_sample_rate;
//    enum AVSampleFormat out_sample_fmt;
//    JavaVM *javaVM;
//
//    pthread_mutex_t mutex;
//    pthread_cond_t cond;
//
//    //视频开始播放的时间
//    int64_t start_time;
//
//    int64_t audio_clock;
//} Player;
//
//typedef struct DecoderData {
//    Player *player;
//    int index;
//} DecoderData;
//
//struct Player *player;
//
//void *player_fill_func() {
//    AVPacket *pkt = av_malloc(sizeof(AVPacket));
//    return pkt;
//}
//
//void init_queue(struct Player *player) {
//    for (int i = 0; i < player->stream_no; ++i) {
//        player->queues[i] = queue_init(50, player_fill_func);
//    }
//}
//
//void init_input_format_ctx(struct Player *player, const char *input_cstr) {
//    //1.注册所有组件
//    av_register_all();
//    //封装格式上下文，统领全局的结构体，保存了视频文件封装格式的相关信息
//    AVFormatContext *pFormatCtx = avformat_alloc_context();
//    LOGI("%s", input_cstr);
//    //2.打开输入视频文件
//    if (avformat_open_input(&pFormatCtx, input_cstr, NULL, NULL) != 0) {
//        LOGE("%s", "无法打开输入视频文件");
//        return;
//    }
//
//    //3.获取视频文件信息
//    if (avformat_find_stream_info(pFormatCtx, NULL) < 0) {
//        LOGE("%s", "无法获取视频文件信息");
//        return;
//    }
//    int i = 0;
//    //number of streams
//    LOGI("nb_streams: %d", pFormatCtx->nb_streams);
//    player->video_stream_idx = -1;
//    player->audio_stream_idx = -1;
//    player->stream_no = pFormatCtx->nb_streams;
//    for (; i < pFormatCtx->nb_streams; i++) {
////    for (; i < 2; i++) {
//        LOGI("index: %d, codec_type:%d", i, pFormatCtx->streams[i]->codec->codec_type);
//        //流的类型
//        if (pFormatCtx->streams[i]->codec->codec_type == AVMEDIA_TYPE_VIDEO &&
//            player->video_stream_idx == -1) {
//            player->video_stream_idx = i;
//            player->showWidth = pFormatCtx->streams[i]->codec->coded_height;
//            player->showHeight = pFormatCtx->streams[i]->codec->coded_width;
//            player->rate = pFormatCtx->streams[i]->r_frame_rate.num;
//            LOGI("w:%d, h:%d index:%d, rate:%d", player->showWidth, player->showHeight, i,
//                 player->rate);
//        } else if (pFormatCtx->streams[i]->codec->codec_type == AVMEDIA_TYPE_AUDIO &&
//                   player->audio_stream_idx == -1) {
//            player->audio_stream_idx = i;
//        }
//    }
//
//    player->input_format_ctx = pFormatCtx;
//    player->useFilter = 0;
//}
//
//void init_codec_ctx(struct Player *player, int index) {
//    AVCodecContext *codecCtx = player->input_format_ctx->streams[index]->codec;
//    AVCodec *codec = avcodec_find_decoder(codecCtx->codec_id);
//    if (NULL == codec) {
//        LOGE("%s", "找不到解码器\n");
//        return;
//    }
//    //5.打开解码器
//    if (avcodec_open2(codecCtx, codec, NULL) < 0) {
//        LOGE("%s", "解码器无法打开\n");
//        return;
//    }
//    player->codecCtx[index] = codecCtx;
//}
//
///**
// * 获取视频当前播放时间
// */
//int64_t player_get_current_video_time(Player *player) {
//    int64_t current_time = av_gettime();
//    return current_time - player->start_time;
//}
//
///**
// * 延迟
// */
//void player_wait_for_frame(Player *player, int64_t stream_time,
//                           int stream_no) {
////    return;
////    pthread_mutex_lock(&player->mutex);
////    for(;;){
////        int64_t current_video_time = player_get_current_video_time(player);
////        LOGE("current_video_time  %d", current_video_time);
////        int64_t sleep_time = stream_time - current_video_time;
////        if (sleep_time < -300000ll) {
////            // 300 ms late
////            int64_t new_value = player->start_time - sleep_time;
////            LOGI("player_wait_for_frame[%d] correcting %f to %f because late",
////                 stream_no, (av_gettime() - player->start_time) / 1000000.0,
////                 (av_gettime() - new_value) / 1000000.0);
////
////            player->start_time = new_value;
////            pthread_cond_broadcast(&player->cond);
////        }
////
////        if (sleep_time <= MIN_SLEEP_TIME_US) {
////            // We do not need to wait if time is slower then minimal sleep time
////            break;
////        }
////
////        if (sleep_time > 500000ll) {
////            // if sleep time is bigger then 500ms just sleep this 500ms
////            // and check everything again
////            sleep_time = 500000ll;
////        }
////        //等待指定时长
////        int timeout_ret = pthread_cond_timeout_np(&player->cond,
////                                                  &player->mutex, sleep_time/1000ll);
////
////        // just go further
////        LOGI("player_wait_for_frame[%d] finish", stream_no);
////    }
////    pthread_mutex_unlock(&player->mutex);
//}
//int64_t video_clock = 0;
//int64_t synchronize(AVFrame *srcFrame, struct Player *player, int64_t pts) {
//    double frame_delay;
//
//    if (pts != 0)
//        video_clock = pts; // Get pts,then set video clock to it
//    else
//        pts = video_clock; // Don't get pts,set it to video clock
//
//    frame_delay = av_q2d(player->input_format_ctx->streams[player->video_stream_idx]->codec->time_base);
//    frame_delay += srcFrame->repeat_pict * (frame_delay * 0.5);
//
//    video_clock += frame_delay;
//
//    return pts;
//}
//
//void showAVFrame(AVFrame *filt_frame, struct Player *player) {
//    int64_t pts;
//
//    if ((pts = av_frame_get_best_effort_timestamp(filt_frame)) == AV_NOPTS_VALUE)
//        pts = 0;
//    LOGE("pts  %d", pts);
////    double timestamp = av_frame_get_best_effort_timestamp(filt_frame)*av_q2d(player->input_format_ctx->streams[player->video_stream_idx]->time_base);
//    //计算延迟
////    pts = av_frame_get_best_effort_timestamp(filt_frame);
//
//    pts *= av_q2d(player->input_format_ctx->streams[player->video_stream_idx]->time_base);
//
//    pts = synchronize(filt_frame, player, pts);
//
//    //转换（不同时间基时间转换）
//    int64_t time = av_rescale_q(pts,player->input_format_ctx->streams[player->video_stream_idx]->time_base,AV_TIME_BASE_Q);
//    LOGE("pts  %d", pts);
//    LOGE("time  %d", time);
//    player_wait_for_frame(player, time, player->video_stream_idx);
//
//    ANativeWindow *window = player->window;
//    ANativeWindow_Buffer outBuffer;
//    AVFrame *pFrameRGBA = av_frame_alloc();
//    ANativeWindow_setBuffersGeometry(window, player->showWidth, player->showHeight,
//                                     WINDOW_FORMAT_RGBA_8888);
//    ANativeWindow_lock(window, &outBuffer, NULL);
//
//    avpicture_fill((AVPicture *) pFrameRGBA, outBuffer.bits,
//                   AV_PIX_FMT_RGBA, player->showWidth, player->showHeight);
//    I420ToARGB(filt_frame->data[0], filt_frame->linesize[0], filt_frame->data[2],
//               filt_frame->linesize[2], filt_frame->data[1], filt_frame->linesize[1],
//               pFrameRGBA->data[0], pFrameRGBA->linesize[0], player->showWidth,
//               player->showHeight);
//    ANativeWindow_unlockAndPost(window);
//}
//
//void decode_video(struct Player *player, AVPacket *packet) {
//
//    AVCodecContext *pCodecCtx = player->codecCtx[player->video_stream_idx];
//
//    int got_picture, ret;
//    //AVFrame用于存储解码后的像素数据(YUV)
//    //内存分配
//    AVFrame *pFrame = av_frame_alloc();
//    AVFrame *filt_frame = av_frame_alloc();
//    //YUV420
//
//    AVFrame *pFrameRGBA2 = av_frame_alloc();
//
//    //7.解码一帧视频压缩数据，得到视频像素数据
//    ret = avcodec_decode_video2(pCodecCtx, pFrame, &got_picture, packet);
//    if (ret < 0) {
//        LOGE("%s", "解码错误");
//        return;
//    }
//    //为0说明解码完成，非0正在解码  0没有可以解压的数据帧
//    if (got_picture) {
////        LOGI("got_picture  %d", got_picture);
//        //把解码后视频帧添加到filter graph
//        if (player->useFilter) {
//            if (av_buffersrc_add_frame_flags(player->buffersrc_ctx, pFrame,
//                                             AV_BUFFERSRC_FLAG_KEEP_REF) < 0) {
//                LOGE("Error while feeding the filter_graph\n");
//                return;
//            }
//            //把滤波后的视频帧从filter graph取出来
//            ret = av_buffersink_get_frame(player->buffersink_ctx, filt_frame);
//            if (ret >= 0) {
//                showAVFrame(filt_frame, player);
//            }
//        } else {
//            showAVFrame(pFrame, player);
//        }
////        usleep(1000 * 1000 / player->rate);
//    }
//    av_frame_free(&pFrame);
//    av_frame_free(&filt_frame);
//    av_frame_free(&pFrameRGBA2);
//}
//
//void decode_audio_prepare(Player *player) {
//    //-------------------重采样参数设置
//    SwrContext *swrCtx = swr_alloc();
//    AVCodecContext *codecCtx = player->codecCtx[player->audio_stream_idx];
//    int64_t out_ch_layout = AV_CH_LAYOUT_STEREO;
//    int64_t in_ch_layout = codecCtx->channel_layout;
//    if (in_ch_layout == 0) {
//        int channels = codecCtx->channels;
//        int64_t default_ch_layout = av_get_default_channel_layout(channels);
//        in_ch_layout = default_ch_layout;
//    }
//    enum AVSampleFormat out_sample_fmt = AV_SAMPLE_FMT_S16;
//    enum AVSampleFormat in_sample_fmt = codecCtx->sample_fmt;
//    int out_sample_rate = 44100;
//    int in_sample_rate = codecCtx->sample_rate;
//
//    swr_alloc_set_opts(swrCtx, out_ch_layout, out_sample_fmt, out_sample_rate,
//                       in_ch_layout, in_sample_fmt, in_sample_rate, 0, NULL);
//    swr_init(swrCtx);
//    //-------------------end
//    player->swrCtx = swrCtx;
//    player->out_ch_layout = out_ch_layout;
//    player->out_sample_rate = out_sample_rate;
//    player->out_sample_fmt = out_sample_fmt;
//}
//
////void jni_audio_prepare(JNIEnv *env, struct Player *player, jclass jcls) {
////    jmethodID createAudioTrackID = (*env)->GetStaticMethodID(env, jcls,
////                                                             "createAudioTrack",
////                                                             "()Landroid/media/AudioTrack;");
////    jobject audioTrack = (*env)->CallStaticObjectMethod(env, jcls, createAudioTrackID);
////    if (NULL == audioTrack) {
////        LOGE("%s", "audioTrack is null");
////    }
////    jclass audioTrack_cls = (*env)->GetObjectClass(env, audioTrack);
////    jmethodID playID = (*env)->GetMethodID(env, audioTrack_cls, "play", "()V");
////    jmethodID writeID = (*env)->GetMethodID(env, audioTrack_cls, "write",
////                                            "([BII)I");
////    (*env)->CallVoidMethod(env, audioTrack, playID);
////    //坑 一定要new全局引用
////    player->audioTrack = (*env)->NewGlobalRef(env, audioTrack);
////    player->audio_track_write_mid = writeID;
////}
//
//#define MAX_AUDIO_FRAME_SIZE 44100*2
//
//void decode_audio(Player *player, AVPacket *packet) {
//    AVCodecContext *codecCtx = player->codecCtx[player->audio_stream_idx];
//    AVFrame *frame = av_frame_alloc();
//    //输出buf
//    uint8_t *outbuf = av_malloc(MAX_AUDIO_FRAME_SIZE);
//
//    SwrContext *swrCtx = player->swrCtx;
//    int got_frame, ret;
//    ret = avcodec_decode_audio4(codecCtx, frame, &got_frame, packet);
//    if (ret < 0) {
//        LOGE("%s", "解码完成");
//    }
//    int nb_channels = av_get_channel_layout_nb_channels(player->out_ch_layout);
//    if (got_frame != 0) {
//        swr_convert(swrCtx, &outbuf, MAX_AUDIO_FRAME_SIZE, (const uint8_t **) frame->data,
//                    frame->nb_samples);
//        //获取sample的size
//        int out_buf_size = av_samples_get_buffer_size(NULL, nb_channels, frame->nb_samples,
//                                                      player->out_sample_fmt, 1);
//
//        int64_t pts = packet->pts;
//        if (pts != AV_NOPTS_VALUE) {
//            player->audio_clock = av_rescale_q(pts, player->input_format_ctx->streams[player->audio_stream_idx]->time_base, AV_TIME_BASE_Q);
//            //				av_q2d(stream->time_base) * pts;
//            LOGE("player_write_audio - read from pts  audio_clock  %d", player->audio_clock);
//            player_wait_for_frame(player,
//                                  player->audio_clock + AUDIO_TIME_ADJUST_US, player->audio_stream_idx);
//        }
//
//        JavaVM *javaVM = player->javaVM;
//        JNIEnv *env;
//        (*javaVM)->AttachCurrentThread(javaVM, &env, NULL);
//        jbyteArray array = (*env)->NewByteArray(env, out_buf_size);
//        jbyte *elements = (*env)->GetByteArrayElements(env, array, NULL);
//        memcpy(elements, outbuf, out_buf_size);
//        (*env)->ReleaseByteArrayElements(env, array, elements, 0);
//        (*env)->CallIntMethod(env, player->audioTrack, player->audio_track_write_mid, array, 0,
//                              out_buf_size);
//        (*env)->DeleteLocalRef(env, array);
//        (*javaVM)->DetachCurrentThread(javaVM);
////        usleep(1000 * 10);
//    } else {
//        LOGE("%s", "没有帧可以被解码");
//    }
//    av_frame_free(&frame);
//}
//
////子线程解码
//void *decode_data(void *arg) {
//    struct DecoderData *decoderData = arg;
//    int index = decoderData->index;
//    for (;;) {
//        pthread_mutex_lock(&player->mutex);
//        LOGE("%s", "decode_data");
//        AVPacket *pkt = queue_pop(player->queues[index], &player->mutex, &player->cond);
//        pthread_mutex_unlock(&player->mutex);
//        if (index == player->video_stream_idx) {
//            decode_video(player, pkt);
//        } else if (index == player->audio_stream_idx) {
//            decode_audio(player, pkt);
//        }
//    }
//
//}
//
//void decode_video_prepare(JNIEnv *env, struct Player *player, jobject surface) {
//    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
//    player->window = window;
//}
//
//void destroy_player(struct Player *player) {
//
//    ANativeWindow_release(player->window);
//    int i = 0;
//    for (i = 0; i < MAX_STREAM; i++) {
//        if (NULL != player->codecCtx[i]) {
//            LOGE("%d", player->codecCtx[i]);
////			avcodec_close(player->codecCtx[i]);
//        }
//    }
//    avformat_free_context(player->input_format_ctx);
//}
//
//void *read_from_stream(void *arg) {
//    AVPacket packet, *pkt = &packet;
//    int ret;
//    for (;;) {
//        ret = av_read_frame(player->input_format_ctx, pkt);
//        //到文件结尾了
//        if (ret < 0) {
//            LOGE("%s", "read_from_stream break");
////            break;
//            continue;
//        }
////        if(pkt->stream_index == player->video_stream_idx)
////            continue;
//        Queue *queue = player->queues[pkt->stream_index];
//        pthread_mutex_lock(&player->mutex);
//        LOGE("%s", "read_from_stream");
//        AVPacket *q = queue_push(queue, &player->mutex, &player->cond);
//        *q = packet;
//        pthread_mutex_unlock(&player->mutex);
//    }
//    return 0;
//}
//
//
//JNIEXPORT void JNICALL Java_com_libwuwind_player_VideoUtils_play(JNIEnv *env,
//                                                                 jclass jcls, jstring input_jstr,
//                                                                 jobject surface) {
//    //需要转码的视频文件(输入的视频文件)
//    const char *input_cstr = (*env)->GetStringUTFChars(env, input_jstr, NULL);
//
//    player = malloc(sizeof(struct Player));
//    (*env)->GetJavaVM(env, &(player->javaVM));
//
//    init_input_format_ctx(player, input_cstr);
//    init_queue(player);
//    int video_stream_index = player->video_stream_idx;
////    int audio_stream_index = player->audio_stream_idx;
//    init_codec_ctx(player, video_stream_index);
////    init_codec_ctx(player, audio_stream_index);//TODO
//    decode_video_prepare(env, player, surface);
////    decode_audio_prepare(player);
////    jni_audio_prepare(env, player, jcls);
//
//    pthread_mutex_init(&player->mutex, NULL);
//    pthread_cond_init(&player->cond, NULL);
//    player->start_time = 0;
//
//    pthread_create(&player->thread_read_from_stream, NULL, read_from_stream, player);
//
//    DecoderData data = {player, video_stream_index};
////    DecoderData data2 = {player, audio_stream_index};
//    pthread_create(&player->decode_threads[video_stream_index], NULL, decode_data, &data);
////    pthread_create(&player->decode_threads[audio_stream_index], NULL, decode_data, &data2);
//
//    pthread_join(player->thread_read_from_stream, NULL);
////    pthread_join(player->decode_threads[audio_stream_index], NULL);
////    pthread_join(player->decode_threads[audio_stream_index], NULL);
//
////
////	(*env)->ReleaseStringUTFChars(env, input_jstr, input_cstr);
////	destroy_player(player);
//
//    LOGI("avcodec_close");
//}