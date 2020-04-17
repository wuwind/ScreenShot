
#include <malloc.h>
#include <pthread.h>
#include <android/log.h>
#define LOGI(FORMAT, ...) __android_log_print(ANDROID_LOG_INFO, "wuhf",  FORMAT, ##__VA_ARGS__)
typedef struct _Queue Queue;

typedef void *(*queue_fill_func)();

/**
 * 初始化队列
 * @param size
 * @return
 */
Queue *queue_init(int size, queue_fill_func);

void *queue_push(Queue *,pthread_mutex_t*, pthread_cond_t*);

void *queue_pop(Queue *,pthread_mutex_t*, pthread_cond_t*);

