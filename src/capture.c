#include <jni.h>
#include <pthread.h>
#include <signal.h>
#include <stdint.h>
#include <sys/time.h>
#include "zdtun.h"

#define FLOW_SLOTS 1024
#define HTTP_PREVIEW_BYTES 2048

struct flow_entry {
    zdtun_5tuple_t tuple;
    time_t checked;
    int blocked;
};

struct capture {
    JNIEnv *env;
    jobject service;
    jmethodID protect_socket;
    jmethodID classify_flow;
    jmethodID observe_packet;
    int tun_fd;
    FILE *pcap;
    int failed;
    struct flow_entry *flows;
};

static int flow_slot(const zdtun_5tuple_t *tuple) {
    uint32_t hash = 2166136261u;
    const unsigned char *key = (const unsigned char *)tuple;
    for (size_t i = 0; i < sizeof(*tuple); i++) hash = (hash ^ key[i]) * 16777619u;
    return hash % FLOW_SLOTS;
}

static int http_start(const zdtun_pkt_t *packet, int response) {
    if (packet->tuple.ipproto != IPPROTO_TCP || !packet->l7_len) return 0;
    if (response) return packet->l7_len >= 7 && !memcmp(packet->l7, "HTTP/1.", 7);
    static const char *methods[] = {
        "GET ", "POST ", "PUT ", "DELETE ", "HEAD ", "OPTIONS ", "PATCH ", "CONNECT ", "TRACE "
    };
    for (size_t i = 0; i < sizeof(methods) / sizeof(methods[0]); i++) {
        size_t length = strlen(methods[i]);
        if (packet->l7_len >= length && !memcmp(packet->l7, methods[i], length)) return 1;
    }
    return 0;
}

static void observe(struct capture *capture, int slot, int length,
                    const zdtun_pkt_t *http, int response) {
    JNIEnv *env = capture->env;
    jbyteArray payload = NULL;
    if (http) {
        int size = min(http->l7_len, HTTP_PREVIEW_BYTES);
        payload = (*env)->NewByteArray(env, size);
        if (!payload) { capture->failed = 1; return; }
        (*env)->SetByteArrayRegion(env, payload, 0, size, (const jbyte *)http->l7);
    }
    (*env)->CallVoidMethod(env, capture->service, capture->observe_packet,
        slot, length, payload, (jboolean)response);
    if (payload) (*env)->DeleteLocalRef(env, payload);
    if ((*env)->ExceptionCheck(env)) capture->failed = 1;
}

static int inspect_packet(struct capture *capture, zdtun_t *tun, struct flow_entry *flows,
                          const char *bytes, int length, int block_unknown) {
    JNIEnv *env = capture->env;
    zdtun_pkt_t packet;
    if (zdtun_parse_pkt(tun, bytes, length, &packet) != 0 ||
        (packet.flags & ZDTUN_PKT_IS_FRAGMENT)) {
        observe(capture, -1, length, NULL, 0);
        return capture->failed || block_unknown;
    }
    const zdtun_5tuple_t *tuple = &packet.tuple;
    int slot = flow_slot(tuple);
    struct flow_entry *entry = &flows[slot];
    time_t now = time(NULL);
    int syn = tuple->ipproto == IPPROTO_TCP && (((unsigned char *)packet.l4)[13] & 2);
    if (syn || !entry->checked || now - entry->checked >= 2 || now < entry->checked ||
        memcmp(&entry->tuple, tuple, sizeof(*tuple)) != 0) {
        int address_size = tuple->ipver == 4 ? 4 : 16;
        jbyteArray source = (*env)->NewByteArray(env, address_size);
        if (!source) { capture->failed = 1; return 1; }
        jbyteArray destination = (*env)->NewByteArray(env, address_size);
        if (!destination) {
            (*env)->DeleteLocalRef(env, source);
            capture->failed = 1;
            return 1;
        }
        (*env)->SetByteArrayRegion(env, source, 0, address_size, (const jbyte *)&tuple->src_ip);
        (*env)->SetByteArrayRegion(env, destination, 0, address_size, (const jbyte *)&tuple->dst_ip);
        entry->blocked = (*env)->CallBooleanMethod(env, capture->service, capture->classify_flow,
            slot, (jint)tuple->ipproto, source, (jint)ntohs(tuple->src_port),
            destination, (jint)ntohs(tuple->dst_port));
        (*env)->DeleteLocalRef(env, source);
        (*env)->DeleteLocalRef(env, destination);
        if ((*env)->ExceptionCheck(env)) { capture->failed = 1; return 1; }
        entry->tuple = *tuple;
        entry->checked = now;
    }
    const zdtun_pkt_t *http = !entry->blocked && http_start(&packet, 0) ? &packet : NULL;
    observe(capture, slot, length, http, 0);
    return capture->failed || entry->blocked;
}

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
    struct capture *capture = zdtun_userdata(tun);
    ssize_t written = TEMP_FAILURE_RETRY(write(capture->tun_fd, packet->buf, packet->len));
    if (written == packet->len) {
        record_packet(capture, packet->buf, packet->len);
        if (!capture->failed && connection && http_start(packet, 1)) {
            const zdtun_5tuple_t *tuple = zdtun_conn_get_5tuple(connection);
            int slot = flow_slot(tuple);
            struct flow_entry *entry = &capture->flows[slot];
            if (entry->checked && !entry->blocked &&
                memcmp(&entry->tuple, tuple, sizeof(*tuple)) == 0) {
                observe(capture, slot, packet->len, packet, 1);
            }
        }
        return 0;
    }
    if (written >= 0 || errno != EAGAIN) capture->failed = 1;
    return -1;
}

JNIEXPORT jint JNICALL Java_local_helperblock_CaptureService_capture(
        JNIEnv *env, jobject service, jint tun_fd, jint pcap_fd, jboolean block_unknown) {
    jclass cls = (*env)->GetObjectClass(env, service);
    jmethodID protect = (*env)->GetMethodID(env, cls, "protect", "(I)Z");
    jmethodID running = (*env)->GetMethodID(env, cls, "shouldCapture", "()Z");
    jmethodID classify = (*env)->GetMethodID(env, cls, "classifyFlow", "(II[BI[BI)Z");
    jmethodID observe = (*env)->GetMethodID(env, cls, "observePacket", "(II[BZ)V");
    if (!protect || !running || !classify || !observe || tun_fd < 0 || tun_fd >= FD_SETSIZE) return -1;

    int output = dup(pcap_fd);
    if (output < 0) return -1;
    FILE *pcap = fdopen(output, "wb");
    if (!pcap) {
        close(output);
        return -1;
    }

    // PCAP v2.4, no truncation, LINKTYPE_RAW (IPv4/IPv6 without Ethernet).
    const uint32_t header[] = {0xa1b2c3d4, 0x00040002, 0, 0, 65535, 101};
    struct flow_entry flows[FLOW_SLOTS] = {0};
    struct capture capture = {env, service, protect, classify, observe, tun_fd, pcap, 0, flows};
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
                if (!capture.failed &&
                    !inspect_packet(&capture, tun, flows, packet, length, block_unknown)) {
                    zdtun_easy_forward(tun, packet, length);
                }
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
