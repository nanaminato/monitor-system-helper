#include <jni.h>
#include <pthread.h>
#include <signal.h>
#include <stdint.h>
#include <sys/time.h>
#include "zdtun.h"

struct capture {
    JNIEnv *env;
    jobject service;
    jmethodID protect_socket;
    int tun_fd;
    FILE *pcap;
    int failed;
};

static void record_packet(struct capture *capture, const char *packet, uint32_t length) {
    struct timeval now;
    gettimeofday(&now, NULL);
    // Android ABIs are little-endian. This is a classic PCAP packet record.
    uint32_t header[] = {now.tv_sec, now.tv_usec, length, length};
    if (fwrite(header, sizeof(header), 1, capture->pcap) != 1 ||
        fwrite(packet, length, 1, capture->pcap) != 1) {
        capture->failed = 1;
    }
}

static int protect_socket(zdtun_t *tun, socket_t socket) {
    struct capture *capture = zdtun_userdata(tun);
    JNIEnv *env = capture->env;
    if ((*env)->CallBooleanMethod(env, capture->service, capture->protect_socket, socket)) return 0;
    capture->failed = 1;
    return -1;
}

static int send_to_app(zdtun_t *tun, zdtun_pkt_t *packet, const zdtun_conn_t *connection) {
    (void)connection;
    struct capture *capture = zdtun_userdata(tun);
    ssize_t written = TEMP_FAILURE_RETRY(write(capture->tun_fd, packet->buf, packet->len));
    if (written == packet->len) {
        record_packet(capture, packet->buf, packet->len);
        return 0;
    }
    if (written >= 0 || errno != EAGAIN) capture->failed = 1;
    return -1;
}

JNIEXPORT jint JNICALL Java_local_helperblock_CaptureService_capture(
        JNIEnv *env, jobject service, jint tun_fd, jint pcap_fd) {
    jclass cls = (*env)->GetObjectClass(env, service);
    jmethodID protect = (*env)->GetMethodID(env, cls, "protect", "(I)Z");
    jmethodID running = (*env)->GetMethodID(env, cls, "shouldCapture", "()Z");
    if (!protect || !running || tun_fd < 0 || tun_fd >= FD_SETSIZE) return -1;

    int output = dup(pcap_fd);
    if (output < 0) return -1;
    FILE *pcap = fdopen(output, "wb");
    if (!pcap) {
        close(output);
        return -1;
    }

    // PCAP v2.4, no truncation, LINKTYPE_RAW (IPv4/IPv6 without Ethernet).
    const uint32_t header[] = {0xa1b2c3d4, 0x00040002, 0, 0, 65535, 101};
    struct capture capture = {env, service, protect, tun_fd, pcap, 0};
    if (fwrite(header, sizeof(header), 1, pcap) != 1) capture.failed = 1;
    zdtun_callbacks_t callbacks = {.send_client = send_to_app, .on_socket_open = protect_socket};
    zdtun_t *tun = zdtun_init(&callbacks, &capture);
    if (!tun) {
        fclose(pcap);
        return -1;
    }
    zdtun_set_mtu(tun, 1500);

    // A TCP peer can close between select and send. Keep SIGPIPE on this worker.
    sigset_t blocked;
    sigemptyset(&blocked);
    sigaddset(&blocked, SIGPIPE);
    pthread_sigmask(SIG_BLOCK, &blocked, NULL);

    char packet[65535];
    time_t last_purge = 0;
    while (!capture.failed && (*env)->CallBooleanMethod(env, service, running)) {
        int highest;
        fd_set reads, writes;
        zdtun_fds(tun, &highest, &reads, &writes);
        FD_SET(tun_fd, &reads);
        if (tun_fd > highest) highest = tun_fd;
        struct timeval timeout = {0, 200000};
        int ready = select(highest + 1, &reads, &writes, NULL, &timeout);
        if (ready < 0 && errno == EINTR) continue;
        if (ready < 0) {
            capture.failed = 1;
            break;
        }
        int tunnel_ready = FD_ISSET(tun_fd, &reads);
        FD_CLR(tun_fd, &reads);
        if (ready > 0) zdtun_handle_fd(tun, &reads, &writes);
        if (tunnel_ready && !capture.failed) {
            ssize_t length = TEMP_FAILURE_RETRY(read(tun_fd, packet, sizeof(packet)));
            if (length > 0) {
                record_packet(&capture, packet, length);
                if (!capture.failed) zdtun_easy_forward(tun, packet, length);
            } else if (length == 0 || errno != EAGAIN) {
                capture.failed = 1;
            }
        }
        time_t now = time(NULL);
        if (now != last_purge) {
            zdtun_purge_expired(tun);
            if (fflush(pcap) != 0) capture.failed = 1;
            last_purge = now;
        }
    }
    zdtun_finalize(tun);
    if (fclose(pcap) != 0) capture.failed = 1;
    return capture.failed ? -1 : 0;
}
