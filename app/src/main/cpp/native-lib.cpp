#include <jni.h>
#include <string>
#include <cstdlib>
#include "node.h"
#include <pthread.h>
#include <unistd.h>
#include <android/log.h>

// Start threads to redirect stdout and stderr to logcat.
int pipe_stdout[2];
int pipe_stderr[2];
pthread_t thread_stdout;
pthread_t thread_stderr;
const char *ADBTAG = "MAMBAS-NODE";

void *thread_stderr_func(void*) {
    ssize_t redirect_size;
    char buf[2048];
    while((redirect_size = read(pipe_stderr[0], buf, sizeof buf - 1)) > 0) {
        if(buf[redirect_size - 1] == '\n')
            --redirect_size;
        buf[redirect_size] = 0;
        __android_log_write(ANDROID_LOG_ERROR, ADBTAG, buf);
    }
    return 0;
}

void *thread_stdout_func(void*) {
    ssize_t redirect_size;
    char buf[2048];
    while((redirect_size = read(pipe_stdout[0], buf, sizeof buf - 1)) > 0) {
        if(buf[redirect_size - 1] == '\n')
            --redirect_size;
        buf[redirect_size] = 0;
        __android_log_write(ANDROID_LOG_INFO, ADBTAG, buf);
    }
    return 0;
}

int start_redirecting_stdout_stderr() {
    setvbuf(stdout, 0, _IONBF, 0);
    pipe(pipe_stdout);
    dup2(pipe_stdout[1], STDOUT_FILENO);
    setvbuf(stderr, 0, _IONBF, 0);
    pipe(pipe_stderr);
    dup2(pipe_stderr[1], STDERR_FILENO);
    if(pthread_create(&thread_stdout, 0, thread_stdout_func, 0) == -1)
        return -1;
    pthread_detach(thread_stdout);
    if(pthread_create(&thread_stderr, 0, thread_stderr_func, 0) == -1)
        return -1;
    pthread_detach(thread_stderr);
    return 0;
}

// node's libUV requires all arguments being on contiguous memory.
extern "C" jint JNICALL
Java_com_eliobrostech_mambastudio_runner_NodeJsRunner_startNodeWithArguments(
    JNIEnv *env,
    jobject /* this */,
    jobjectArray arguments) {

    jsize argument_count = env->GetArrayLength(arguments);

    // Compute byte size needed for all arguments in contiguous memory.
    int c_arguments_size = 0;
    for (int i = 0; i < argument_count ; i++) {
        c_arguments_size += strlen(env->GetStringUTFChars(
            (jstring)env->GetObjectArrayElement(arguments, i), 0));
        c_arguments_size++; // for '\0'
    }

    // Stores arguments in contiguous memory.
    char* args_buffer = (char*)calloc(c_arguments_size, sizeof(char));
    char* argv[argument_count];
    char* current_args_position = args_buffer;

    for (int i = 0; i < argument_count ; i++) {
        const char* current_argument = env->GetStringUTFChars(
            (jstring)env->GetObjectArrayElement(arguments, i), 0);
        strncpy(current_args_position, current_argument, strlen(current_argument));
        argv[i] = current_args_position;
        current_args_position += strlen(current_args_position) + 1;
    }

    // Start threads to show stdout and stderr in logcat.
    start_redirecting_stdout_stderr();

    // Start node, with argc and argv.
    return jint(node::Start(argument_count, argv));
}

// Helper to stop Node.js gracefully
extern "C" JNIEXPORT void JNICALL
Java_com_eliobrostech_mambastudio_runner_NodeJsRunner_stopNode(
    JNIEnv* env,
    jobject /* this */) {
    // Note: node::Stop() doesn't exist in public API.
    // The Node.js process will run until the app is destroyed.
    // On Android, the process is killed when the app is closed.
}
