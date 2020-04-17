#include "queue0.h"

struct _Queue {
    int size;
    int next_to_read;
    int next_to_write;
    //int current;
    void **tab;

    int current;
};

Queue *queue_init(int size, queue_fill_func fill_func) {
    Queue *queue = malloc(sizeof(Queue));
    queue->size = size;
    queue->next_to_read = 0;
    queue->next_to_write = 0;
    queue->current = 0;
    queue->tab = malloc(sizeof(*queue->tab) * size);
    for (int i = 0; i < size; ++i) {
        queue->tab[i] = fill_func();
    }
    return queue;
}

int queue_get_next(Queue *queue, int current) {
    return (current + 1) % queue->size;
}

void *queue_push(Queue *queue, pthread_mutex_t* mutex, pthread_cond_t* cond) {
//    pthread_mutex_lock(&mutex);
    int current = queue->next_to_write;
    int next_to_write;
    for (;;) {
        next_to_write = queue_get_next(queue, current);
        if (next_to_write != queue->next_to_read)
            break;
        LOGI("queue_push wait %d ", queue->current);
        pthread_cond_wait(cond, mutex);
    }
    queue->next_to_write = next_to_write;
    queue->current++;
//    pthread_mutex_unlock(&mutex);
//    pthread_cond_signal(&cond);
    pthread_cond_broadcast(cond);
    LOGI("push %d", current);
    return queue->tab[current];
}

void *queue_pop(Queue *queue,pthread_mutex_t* mutex, pthread_cond_t* cond) {
//    pthread_mutex_lock(&mutex);
    int current = queue->next_to_read;
    for (;;) {
        //下一个要读的位置等于下一个要写的，等写完，在读
        if (queue->next_to_read != queue->next_to_write)
            break;
        LOGI("queue_pop wait %d ", queue->current);
        pthread_cond_wait(cond, mutex);
    }
    queue->next_to_read = queue_get_next(queue, current);
    queue->current--;
//    pthread_mutex_unlock(&mutex);
//    pthread_cond_signal(&cond);
    LOGI("pop %d", current);
    pthread_cond_broadcast(cond);
    return queue->tab[current];
}