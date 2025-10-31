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

#define _GNU_SOURCE
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
#define ABSTRACT_TCP_SOCK "\0test_abstract_tcp"
#define ABSTRACT_UDP_SOCK "\0test_abstract_udp"

void print_usage(const char *prog) {
    printf("Usage: %s [tcp_socket_path] [udp_socket_path]\n", prog);
    printf("  tcp_socket_path: Unix domain socket path for TCP (default: %s)\n", DEFAULT_TCP_SOCK);
    printf("  udp_socket_path: Unix domain socket path for UDP (default: %s)\n", DEFAULT_UDP_SOCK);
    printf("Example: %s /tmp/my_tcp.sock /tmp/my_udp.sock\n", prog);
}

int main(int argc, char *argv[]) {
    int tcp_sock, udp_sock;
    int tcp_client, tcp_client4;
    struct sockaddr_un tcp_addr, udp_addr, client_addr;
    socklen_t addr_len;
    char buffer[BUFFER_SIZE];
    ssize_t n;
    const char *tcp_path;
    const char *udp_path;

    if (argc > 3) {
        print_usage(argv[0]);
        exit(1);
    }

    tcp_path = (argc >= 2) ? argv[1] : DEFAULT_TCP_SOCK;
    udp_path = (argc >= 3) ? argv[2] : DEFAULT_UDP_SOCK;

    printf("=== Unix Domain Socket Syscall Test Server ===\n");
    printf("TCP Socket: %s\n", tcp_path);
    printf("UDP Socket: %s\n", udp_path);

    // Remove old socket files if they exist
    unlink(tcp_path);
    unlink(udp_path);

    // Create TCP socket
    tcp_sock = socket(AF_UNIX, SOCK_STREAM, 0);
    if (tcp_sock < 0) {
        perror("TCP socket creation failed");
        exit(1);
    }

    // Setup TCP address
    memset(&tcp_addr, 0, sizeof(tcp_addr));
    tcp_addr.sun_family = AF_UNIX;
    strncpy(tcp_addr.sun_path, tcp_path, sizeof(tcp_addr.sun_path) - 1);
    addr_len = sizeof(tcp_addr);

    // Test bind() syscall on TCP socket
    if (bind(tcp_sock, (struct sockaddr*)&tcp_addr, addr_len) < 0) {
        perror("TCP bind failed");
        close(tcp_sock);
        exit(1);
    }
    printf("[TCP] bind() successful on %s\n", tcp_path);

    // Listen on TCP socket
    if (listen(tcp_sock, 5) < 0) {
        perror("TCP listen failed");
        close(tcp_sock);
        unlink(tcp_path);
        exit(1);
    }
    printf("[TCP] Listening for connections...\n");

    // Create UDP socket
    udp_sock = socket(AF_UNIX, SOCK_DGRAM, 0);
    if (udp_sock < 0) {
        perror("UDP socket creation failed");
        close(tcp_sock);
        unlink(tcp_path);
        exit(1);
    }

    // Setup UDP address
    memset(&udp_addr, 0, sizeof(udp_addr));
    udp_addr.sun_family = AF_UNIX;
    strncpy(udp_addr.sun_path, udp_path, sizeof(udp_addr.sun_path) - 1);

    // Test bind() syscall on UDP socket
    if (bind(udp_sock, (struct sockaddr*)&udp_addr, addr_len) < 0) {
        perror("UDP bind failed");
        close(tcp_sock);
        close(udp_sock);
        unlink(tcp_path);
        exit(1);
    }
    printf("[UDP] bind() successful on %s\n", udp_path);

    // Test accept() syscall - wait for first TCP connection
    printf("[TCP] Waiting for first client (accept)...\n");
    socklen_t client_len = sizeof(client_addr);
    tcp_client = accept(tcp_sock, (struct sockaddr*)&client_addr, &client_len);
    if (tcp_client < 0) {
        perror("accept failed");
    } else {
        printf("[TCP] accept() successful - client connected\n");

        // Receive data using recv
        memset(buffer, 0, BUFFER_SIZE);
        n = recv(tcp_client, buffer, BUFFER_SIZE, 0);
        if (n > 0) {
            printf("[TCP] Received: %s\n", buffer);
        }

        // Send response
        const char *response = "Hello from accept()";
        send(tcp_client, response, strlen(response), 0);

        close(tcp_client);
    }

    // Test accept4() syscall - wait for second TCP connection
    printf("[TCP] Waiting for second client (accept4)...\n");
    client_len = sizeof(client_addr);
    tcp_client4 = accept4(tcp_sock, (struct sockaddr*)&client_addr, &client_len, SOCK_CLOEXEC);
    if (tcp_client4 < 0) {
        perror("accept4 failed");
    } else {
        printf("[TCP] accept4() successful - client connected\n");

        // Receive data
        memset(buffer, 0, BUFFER_SIZE);
        n = recv(tcp_client4, buffer, BUFFER_SIZE, 0);
        if (n > 0) {
            printf("[TCP] Received: %s\n", buffer);
        }

        // Send response
        const char *response = "Hello from accept4()";
        send(tcp_client4, response, strlen(response), 0);

        close(tcp_client4);
    }

    // Test recvfrom() and sendto() syscalls on UDP
    printf("[UDP] Waiting for data (recvfrom)...\n");
    memset(buffer, 0, BUFFER_SIZE);
    memset(&client_addr, 0, sizeof(client_addr));
    client_len = sizeof(client_addr);

    n = recvfrom(udp_sock, buffer, BUFFER_SIZE, 0,
                 (struct sockaddr*)&client_addr, &client_len);
    if (n > 0) {
        printf("[UDP] recvfrom() received: %s from %s\n",
               buffer, client_addr.sun_path[0] ? client_addr.sun_path : "(anonymous)");

        // Test sendto() syscall
        const char *response = "UDP response via sendto()";
        n = sendto(udp_sock, response, strlen(response), 0,
                   (struct sockaddr*)&client_addr, client_len);
        if (n > 0) {
            printf("[UDP] sendto() sent %zd bytes\n", n);
        }
    }

    // Test recvmsg() and sendmsg() syscalls
    printf("[UDP] Waiting for data (recvmsg)...\n");
    memset(buffer, 0, BUFFER_SIZE);
    memset(&client_addr, 0, sizeof(client_addr));

    struct iovec iov;
    iov.iov_base = buffer;
    iov.iov_len = BUFFER_SIZE;

    struct msghdr msg;
    memset(&msg, 0, sizeof(msg));
    msg.msg_name = &client_addr;
    msg.msg_namelen = sizeof(client_addr);
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;

    n = recvmsg(udp_sock, &msg, 0);
    if (n > 0) {
        printf("[UDP] recvmsg() received: %s from %s\n",
               buffer, client_addr.sun_path[0] ? client_addr.sun_path : "(anonymous)");

        // Test sendmsg() syscall
        const char *response = "UDP response via sendmsg()";
        iov.iov_base = (void*)response;
        iov.iov_len = strlen(response);

        msg.msg_name = &client_addr;
        msg.msg_namelen = msg.msg_namelen;
        msg.msg_iov = &iov;
        msg.msg_iovlen = 1;

        n = sendmsg(udp_sock, &msg, 0);
        if (n > 0) {
            printf("[UDP] sendmsg() sent %zd bytes\n", n);
        }
    }

    // Cleanup filesystem sockets
    close(tcp_sock);
    close(udp_sock);
    unlink(tcp_path);
    unlink(udp_path);

    printf("\n=== Filesystem socket tests completed ===\n");
    printf("\n=== Starting Abstract Unix Socket Tests ===\n");

    // ===== Abstract Socket Tests =====
    int abs_tcp_sock, abs_udp_sock;
    int abs_tcp_client, abs_tcp_client4;
    struct sockaddr_un abs_tcp_addr, abs_udp_addr, abs_client_addr;

    // Create abstract TCP socket
    abs_tcp_sock = socket(AF_UNIX, SOCK_STREAM, 0);
    if (abs_tcp_sock < 0) {
        perror("Abstract TCP socket creation failed");
        return 1;
    }

    // Setup abstract TCP address (starts with \0)
    memset(&abs_tcp_addr, 0, sizeof(abs_tcp_addr));
    abs_tcp_addr.sun_family = AF_UNIX;
    memcpy(abs_tcp_addr.sun_path, ABSTRACT_TCP_SOCK, sizeof(ABSTRACT_TCP_SOCK) - 1);

    // Bind to abstract socket (use actual length, not sizeof struct)
    addr_len = offsetof(struct sockaddr_un, sun_path) + sizeof(ABSTRACT_TCP_SOCK) - 1;
    printf("[Abstract TCP] Testing bind() on abstract socket...\n");
    if (bind(abs_tcp_sock, (struct sockaddr*)&abs_tcp_addr, addr_len) < 0) {
        perror("Abstract TCP bind failed");
        close(abs_tcp_sock);
        return 1;
    }
    printf("[Abstract TCP] bind() successful on abstract socket \\0test_abstract_tcp\n");

    // Listen
    if (listen(abs_tcp_sock, 5) < 0) {
        perror("Abstract TCP listen failed");
        close(abs_tcp_sock);
        return 1;
    }
    printf("[Abstract TCP] Listening for connections...\n");

    // Create abstract UDP socket
    abs_udp_sock = socket(AF_UNIX, SOCK_DGRAM, 0);
    if (abs_udp_sock < 0) {
        perror("Abstract UDP socket creation failed");
        close(abs_tcp_sock);
        return 1;
    }

    // Setup abstract UDP address
    memset(&abs_udp_addr, 0, sizeof(abs_udp_addr));
    abs_udp_addr.sun_family = AF_UNIX;
    memcpy(abs_udp_addr.sun_path, ABSTRACT_UDP_SOCK, sizeof(ABSTRACT_UDP_SOCK) - 1);

    addr_len = offsetof(struct sockaddr_un, sun_path) + sizeof(ABSTRACT_UDP_SOCK) - 1;
    printf("[Abstract UDP] Testing bind() on abstract socket...\n");
    if (bind(abs_udp_sock, (struct sockaddr*)&abs_udp_addr, addr_len) < 0) {
        perror("Abstract UDP bind failed");
        close(abs_tcp_sock);
        close(abs_udp_sock);
        return 1;
    }
    printf("[Abstract UDP] bind() successful on abstract socket \\0test_abstract_udp\n");

    // Test accept() on abstract socket
    printf("[Abstract TCP] Waiting for first client (accept)...\n");
    client_len = sizeof(abs_client_addr);
    abs_tcp_client = accept(abs_tcp_sock, (struct sockaddr*)&abs_client_addr, &client_len);
    if (abs_tcp_client < 0) {
        perror("Abstract accept failed");
    } else {
        printf("[Abstract TCP] accept() successful\n");
        memset(buffer, 0, BUFFER_SIZE);
        n = recv(abs_tcp_client, buffer, BUFFER_SIZE, 0);
        if (n > 0) {
            printf("[Abstract TCP] Received: %s\n", buffer);
        }
        const char *response = "Hello from abstract accept()";
        send(abs_tcp_client, response, strlen(response), 0);
        close(abs_tcp_client);
    }

    // Test accept4() on abstract socket
    printf("[Abstract TCP] Waiting for second client (accept4)...\n");
    client_len = sizeof(abs_client_addr);
    abs_tcp_client4 = accept4(abs_tcp_sock, (struct sockaddr*)&abs_client_addr, &client_len, SOCK_CLOEXEC);
    if (abs_tcp_client4 < 0) {
        perror("Abstract accept4 failed");
    } else {
        printf("[Abstract TCP] accept4() successful\n");
        memset(buffer, 0, BUFFER_SIZE);
        n = recv(abs_tcp_client4, buffer, BUFFER_SIZE, 0);
        if (n > 0) {
            printf("[Abstract TCP] Received: %s\n", buffer);
        }
        const char *response = "Hello from abstract accept4()";
        send(abs_tcp_client4, response, strlen(response), 0);
        close(abs_tcp_client4);
    }

    // Test recvfrom() and sendto() on abstract UDP
    printf("[Abstract UDP] Waiting for data (recvfrom)...\n");
    memset(buffer, 0, BUFFER_SIZE);
    memset(&abs_client_addr, 0, sizeof(abs_client_addr));
    client_len = sizeof(abs_client_addr);

    n = recvfrom(abs_udp_sock, buffer, BUFFER_SIZE, 0,
                 (struct sockaddr*)&abs_client_addr, &client_len);
    if (n > 0) {
        printf("[Abstract UDP] recvfrom() received: %s\n", buffer);
        const char *response = "Abstract UDP response via sendto()";
        n = sendto(abs_udp_sock, response, strlen(response), 0,
                   (struct sockaddr*)&abs_client_addr, client_len);
        if (n > 0) {
            printf("[Abstract UDP] sendto() sent %zd bytes\n", n);
        }
    }

    // Test recvmsg() and sendmsg() on abstract UDP
    printf("[Abstract UDP] Waiting for data (recvmsg)...\n");
    memset(buffer, 0, BUFFER_SIZE);
    memset(&abs_client_addr, 0, sizeof(abs_client_addr));

    iov.iov_base = buffer;
    iov.iov_len = BUFFER_SIZE;

    memset(&msg, 0, sizeof(msg));
    msg.msg_name = &abs_client_addr;
    msg.msg_namelen = sizeof(abs_client_addr);
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;

    n = recvmsg(abs_udp_sock, &msg, 0);
    if (n > 0) {
        printf("[Abstract UDP] recvmsg() received: %s\n", buffer);
        const char *response = "Abstract UDP response via sendmsg()";
        iov.iov_base = (void*)response;
        iov.iov_len = strlen(response);

        msg.msg_name = &abs_client_addr;
        msg.msg_namelen = msg.msg_namelen;
        msg.msg_iov = &iov;
        msg.msg_iovlen = 1;

        n = sendmsg(abs_udp_sock, &msg, 0);
        if (n > 0) {
            printf("[Abstract UDP] sendmsg() sent %zd bytes\n", n);
        }
    }

    // Cleanup abstract sockets
    close(abs_tcp_sock);
    close(abs_udp_sock);

    printf("\n=== Server completed all syscall tests (filesystem + abstract) ===\n");
    return 0;
}
