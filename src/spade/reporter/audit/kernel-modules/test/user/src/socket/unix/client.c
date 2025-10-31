/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2025 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>

#define BUFFER_SIZE 1024
#define DEFAULT_TCP_SOCK "/tmp/test_tcp.sock"
#define DEFAULT_UDP_SOCK "/tmp/test_udp.sock"
#define CLIENT_UDP_SOCK "/tmp/test_client_udp.sock"
#define ABSTRACT_TCP_SOCK "\0test_abstract_tcp"
#define ABSTRACT_UDP_SOCK "\0test_abstract_udp"
#define ABSTRACT_CLIENT_UDP_SOCK "\0test_abstract_client_udp"

void print_usage(const char *prog) {
    printf("Usage: %s [tcp_socket_path] [udp_socket_path]\n", prog);
    printf("  tcp_socket_path: Unix domain socket path for TCP (default: %s)\n", DEFAULT_TCP_SOCK);
    printf("  udp_socket_path: Unix domain socket path for UDP (default: %s)\n", DEFAULT_UDP_SOCK);
    printf("Example: %s /tmp/my_tcp.sock /tmp/my_udp.sock\n", prog);
}

int main(int argc, char *argv[]) {
    int tcp_sock1, tcp_sock2, udp_sock;
    struct sockaddr_un server_tcp_addr, server_udp_addr, client_udp_addr, from_addr;
    char buffer[BUFFER_SIZE];
    ssize_t n;
    const char *tcp_path;
    const char *udp_path;
    socklen_t addr_len;

    if (argc > 3) {
        print_usage(argv[0]);
        exit(1);
    }

    tcp_path = (argc >= 2) ? argv[1] : DEFAULT_TCP_SOCK;
    udp_path = (argc >= 3) ? argv[2] : DEFAULT_UDP_SOCK;

    printf("=== Unix Domain Socket Syscall Test Client ===\n");
    printf("TCP Socket: %s\n", tcp_path);
    printf("UDP Socket: %s\n", udp_path);
    sleep(1); // Give server time to start

    // ===== Test 1: connect() and send/recv with accept() =====
    tcp_sock1 = socket(AF_UNIX, SOCK_STREAM, 0);
    if (tcp_sock1 < 0) {
        perror("TCP socket 1 creation failed");
        exit(1);
    }

    memset(&server_tcp_addr, 0, sizeof(server_tcp_addr));
    server_tcp_addr.sun_family = AF_UNIX;
    strncpy(server_tcp_addr.sun_path, tcp_path, sizeof(server_tcp_addr.sun_path) - 1);

    // Test connect() syscall
    printf("[TCP] Testing connect() to %s...\n", tcp_path);
    if (connect(tcp_sock1, (struct sockaddr*)&server_tcp_addr, sizeof(server_tcp_addr)) < 0) {
        perror("connect failed");
        close(tcp_sock1);
        exit(1);
    }
    printf("[TCP] connect() successful (for accept test)\n");

    // Send data
    const char *msg1 = "Hello from client 1";
    send(tcp_sock1, msg1, strlen(msg1), 0);

    // Receive response
    memset(buffer, 0, BUFFER_SIZE);
    n = recv(tcp_sock1, buffer, BUFFER_SIZE, 0);
    if (n > 0) {
        printf("[TCP] Received response: %s\n", buffer);
    }

    close(tcp_sock1);
    sleep(1);

    // ===== Test 2: connect() with accept4() =====
    tcp_sock2 = socket(AF_UNIX, SOCK_STREAM, 0);
    if (tcp_sock2 < 0) {
        perror("TCP socket 2 creation failed");
        exit(1);
    }

    printf("[TCP] Testing connect() to %s...\n", tcp_path);
    if (connect(tcp_sock2, (struct sockaddr*)&server_tcp_addr, sizeof(server_tcp_addr)) < 0) {
        perror("connect failed");
        close(tcp_sock2);
        exit(1);
    }
    printf("[TCP] connect() successful (for accept4 test)\n");

    // Send data
    const char *msg2 = "Hello from client 2";
    send(tcp_sock2, msg2, strlen(msg2), 0);

    // Receive response
    memset(buffer, 0, BUFFER_SIZE);
    n = recv(tcp_sock2, buffer, BUFFER_SIZE, 0);
    if (n > 0) {
        printf("[TCP] Received response: %s\n", buffer);
    }

    close(tcp_sock2);
    sleep(1);

    // ===== Test 3: sendto() and recvfrom() UDP =====
    // Remove old client socket if it exists
    unlink(CLIENT_UDP_SOCK);

    udp_sock = socket(AF_UNIX, SOCK_DGRAM, 0);
    if (udp_sock < 0) {
        perror("UDP socket creation failed");
        exit(1);
    }

    // Bind client UDP socket so server can respond
    memset(&client_udp_addr, 0, sizeof(client_udp_addr));
    client_udp_addr.sun_family = AF_UNIX;
    strncpy(client_udp_addr.sun_path, CLIENT_UDP_SOCK, sizeof(client_udp_addr.sun_path) - 1);

    if (bind(udp_sock, (struct sockaddr*)&client_udp_addr, sizeof(client_udp_addr)) < 0) {
        perror("UDP client bind failed");
        close(udp_sock);
        exit(1);
    }

    // Setup UDP server address
    memset(&server_udp_addr, 0, sizeof(server_udp_addr));
    server_udp_addr.sun_family = AF_UNIX;
    strncpy(server_udp_addr.sun_path, udp_path, sizeof(server_udp_addr.sun_path) - 1);
    addr_len = sizeof(server_udp_addr);

    // Test sendto() syscall
    printf("[UDP] Testing sendto() to %s...\n", udp_path);
    const char *udp_msg1 = "UDP message via sendto()";
    n = sendto(udp_sock, udp_msg1, strlen(udp_msg1), 0,
               (struct sockaddr*)&server_udp_addr, addr_len);
    if (n > 0) {
        printf("[UDP] sendto() sent %zd bytes\n", n);
    }

    // Test recvfrom() syscall
    memset(buffer, 0, BUFFER_SIZE);
    socklen_t from_len = sizeof(from_addr);
    n = recvfrom(udp_sock, buffer, BUFFER_SIZE, 0,
                 (struct sockaddr*)&from_addr, &from_len);
    if (n > 0) {
        printf("[UDP] recvfrom() received: %s\n", buffer);
    }

    sleep(1);

    // ===== Test 4: sendmsg() and recvmsg() UDP =====
    printf("[UDP] Testing sendmsg()...\n");
    const char *udp_msg2 = "UDP message via sendmsg()";

    struct iovec iov;
    iov.iov_base = (void*)udp_msg2;
    iov.iov_len = strlen(udp_msg2);

    struct msghdr msg;
    memset(&msg, 0, sizeof(msg));
    msg.msg_name = &server_udp_addr;
    msg.msg_namelen = addr_len;
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;

    // Test sendmsg() syscall
    n = sendmsg(udp_sock, &msg, 0);
    if (n > 0) {
        printf("[UDP] sendmsg() sent %zd bytes\n", n);
    }

    // Test recvmsg() syscall
    memset(buffer, 0, BUFFER_SIZE);
    iov.iov_base = buffer;
    iov.iov_len = BUFFER_SIZE;

    memset(&msg, 0, sizeof(msg));
    msg.msg_name = &from_addr;
    msg.msg_namelen = sizeof(from_addr);
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;

    n = recvmsg(udp_sock, &msg, 0);
    if (n > 0) {
        printf("[UDP] recvmsg() received: %s\n", buffer);
    }

    close(udp_sock);
    unlink(CLIENT_UDP_SOCK);

    printf("\n=== Filesystem socket tests completed ===\n");
    printf("\n=== Starting Abstract Unix Socket Tests ===\n");
    sleep(1);

    // ===== Abstract Socket Tests =====
    int abs_tcp_sock1, abs_tcp_sock2, abs_udp_sock;
    struct sockaddr_un abs_server_tcp_addr, abs_server_udp_addr, abs_client_udp_addr, abs_from_addr;
    socklen_t abs_addr_len;

    // ===== Test 1: connect() to abstract TCP socket with accept() =====
    abs_tcp_sock1 = socket(AF_UNIX, SOCK_STREAM, 0);
    if (abs_tcp_sock1 < 0) {
        perror("Abstract TCP socket 1 creation failed");
        return 1;
    }

    memset(&abs_server_tcp_addr, 0, sizeof(abs_server_tcp_addr));
    abs_server_tcp_addr.sun_family = AF_UNIX;
    memcpy(abs_server_tcp_addr.sun_path, ABSTRACT_TCP_SOCK, sizeof(ABSTRACT_TCP_SOCK) - 1);
    abs_addr_len = offsetof(struct sockaddr_un, sun_path) + sizeof(ABSTRACT_TCP_SOCK) - 1;

    printf("[Abstract TCP] Testing connect() to abstract socket...\n");
    if (connect(abs_tcp_sock1, (struct sockaddr*)&abs_server_tcp_addr, abs_addr_len) < 0) {
        perror("Abstract connect failed");
        close(abs_tcp_sock1);
        return 1;
    }
    printf("[Abstract TCP] connect() successful (for accept test)\n");

    // Send data
    const char *abs_msg1 = "Hello from abstract client 1";
    send(abs_tcp_sock1, abs_msg1, strlen(abs_msg1), 0);

    // Receive response
    memset(buffer, 0, BUFFER_SIZE);
    n = recv(abs_tcp_sock1, buffer, BUFFER_SIZE, 0);
    if (n > 0) {
        printf("[Abstract TCP] Received response: %s\n", buffer);
    }

    close(abs_tcp_sock1);
    sleep(1);

    // ===== Test 2: connect() to abstract TCP socket with accept4() =====
    abs_tcp_sock2 = socket(AF_UNIX, SOCK_STREAM, 0);
    if (abs_tcp_sock2 < 0) {
        perror("Abstract TCP socket 2 creation failed");
        return 1;
    }

    printf("[Abstract TCP] Testing connect() to abstract socket...\n");
    if (connect(abs_tcp_sock2, (struct sockaddr*)&abs_server_tcp_addr, abs_addr_len) < 0) {
        perror("Abstract connect failed");
        close(abs_tcp_sock2);
        return 1;
    }
    printf("[Abstract TCP] connect() successful (for accept4 test)\n");

    // Send data
    const char *abs_msg2 = "Hello from abstract client 2";
    send(abs_tcp_sock2, abs_msg2, strlen(abs_msg2), 0);

    // Receive response
    memset(buffer, 0, BUFFER_SIZE);
    n = recv(abs_tcp_sock2, buffer, BUFFER_SIZE, 0);
    if (n > 0) {
        printf("[Abstract TCP] Received response: %s\n", buffer);
    }

    close(abs_tcp_sock2);
    sleep(1);

    // ===== Test 3: sendto() and recvfrom() with abstract UDP =====
    abs_udp_sock = socket(AF_UNIX, SOCK_DGRAM, 0);
    if (abs_udp_sock < 0) {
        perror("Abstract UDP socket creation failed");
        return 1;
    }

    // Bind client UDP socket so server can respond
    memset(&abs_client_udp_addr, 0, sizeof(abs_client_udp_addr));
    abs_client_udp_addr.sun_family = AF_UNIX;
    memcpy(abs_client_udp_addr.sun_path, ABSTRACT_CLIENT_UDP_SOCK, sizeof(ABSTRACT_CLIENT_UDP_SOCK) - 1);
    abs_addr_len = offsetof(struct sockaddr_un, sun_path) + sizeof(ABSTRACT_CLIENT_UDP_SOCK) - 1;

    if (bind(abs_udp_sock, (struct sockaddr*)&abs_client_udp_addr, abs_addr_len) < 0) {
        perror("Abstract UDP client bind failed");
        close(abs_udp_sock);
        return 1;
    }

    // Setup abstract UDP server address
    memset(&abs_server_udp_addr, 0, sizeof(abs_server_udp_addr));
    abs_server_udp_addr.sun_family = AF_UNIX;
    memcpy(abs_server_udp_addr.sun_path, ABSTRACT_UDP_SOCK, sizeof(ABSTRACT_UDP_SOCK) - 1);
    abs_addr_len = offsetof(struct sockaddr_un, sun_path) + sizeof(ABSTRACT_UDP_SOCK) - 1;

    // Test sendto() syscall
    printf("[Abstract UDP] Testing sendto() to abstract socket...\n");
    const char *abs_udp_msg1 = "Abstract UDP message via sendto()";
    n = sendto(abs_udp_sock, abs_udp_msg1, strlen(abs_udp_msg1), 0,
               (struct sockaddr*)&abs_server_udp_addr, abs_addr_len);
    if (n > 0) {
        printf("[Abstract UDP] sendto() sent %zd bytes\n", n);
    }

    // Test recvfrom() syscall
    memset(buffer, 0, BUFFER_SIZE);
    socklen_t abs_from_len = sizeof(abs_from_addr);
    n = recvfrom(abs_udp_sock, buffer, BUFFER_SIZE, 0,
                 (struct sockaddr*)&abs_from_addr, &abs_from_len);
    if (n > 0) {
        printf("[Abstract UDP] recvfrom() received: %s\n", buffer);
    }

    sleep(1);

    // ===== Test 4: sendmsg() and recvmsg() with abstract UDP =====
    printf("[Abstract UDP] Testing sendmsg() to abstract socket...\n");
    const char *abs_udp_msg2 = "Abstract UDP message via sendmsg()";

    struct iovec abs_iov;
    abs_iov.iov_base = (void*)abs_udp_msg2;
    abs_iov.iov_len = strlen(abs_udp_msg2);

    struct msghdr abs_msg;
    memset(&abs_msg, 0, sizeof(abs_msg));
    abs_msg.msg_name = &abs_server_udp_addr;
    abs_msg.msg_namelen = abs_addr_len;
    abs_msg.msg_iov = &abs_iov;
    abs_msg.msg_iovlen = 1;

    // Test sendmsg() syscall
    n = sendmsg(abs_udp_sock, &abs_msg, 0);
    if (n > 0) {
        printf("[Abstract UDP] sendmsg() sent %zd bytes\n", n);
    }

    // Test recvmsg() syscall
    memset(buffer, 0, BUFFER_SIZE);
    abs_iov.iov_base = buffer;
    abs_iov.iov_len = BUFFER_SIZE;

    memset(&abs_msg, 0, sizeof(abs_msg));
    abs_msg.msg_name = &abs_from_addr;
    abs_msg.msg_namelen = sizeof(abs_from_addr);
    abs_msg.msg_iov = &abs_iov;
    abs_msg.msg_iovlen = 1;

    n = recvmsg(abs_udp_sock, &abs_msg, 0);
    if (n > 0) {
        printf("[Abstract UDP] recvmsg() received: %s\n", buffer);
    }

    close(abs_udp_sock);

    printf("\n=== Client completed all syscall tests (filesystem + abstract) ===\n");
    return 0;
}
