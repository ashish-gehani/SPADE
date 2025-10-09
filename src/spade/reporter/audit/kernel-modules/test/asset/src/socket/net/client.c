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
#include <netinet/in.h>
#include <arpa/inet.h>

#define BUFFER_SIZE 1024

void print_usage(const char *prog) {
    printf("Usage: %s <ip_version> <server_ip> <tcp_port> <udp_port>\n", prog);
    printf("  ip_version: 4 for IPv4, 6 for IPv6\n");
    printf("  server_ip: IP address of the server\n");
    printf("  tcp_port: TCP port number\n");
    printf("  udp_port: UDP port number\n");
    printf("Example: %s 4 127.0.0.1 8080 8081\n", prog);
    printf("Example: %s 6 ::1 8080 8081\n", prog);
}

int main(int argc, char *argv[]) {
    int tcp_sock1, tcp_sock2, udp_sock;
    struct sockaddr_storage server_addr, from_addr;
    char buffer[BUFFER_SIZE];
    ssize_t n;
    int ip_version;
    int tcp_port, udp_port;
    int af_family;
    socklen_t addr_len;

    if (argc != 5) {
        print_usage(argv[0]);
        exit(1);
    }

    ip_version = atoi(argv[1]);
    char *server_ip = argv[2];
    tcp_port = atoi(argv[3]);
    udp_port = atoi(argv[4]);

    if (ip_version != 4 && ip_version != 6) {
        fprintf(stderr, "Error: IP version must be 4 or 6\n");
        print_usage(argv[0]);
        exit(1);
    }

    af_family = (ip_version == 4) ? AF_INET : AF_INET6;

    printf("=== Socket Syscall Test Client (IPv%d) ===\n", ip_version);
    printf("Server: %s, TCP Port: %d, UDP Port: %d\n", server_ip, tcp_port, udp_port);
    sleep(1); // Give server time to start

    // ===== Test 1: connect() and send/recv with accept() =====
    tcp_sock1 = socket(af_family, SOCK_STREAM, 0);
    if (tcp_sock1 < 0) {
        perror("TCP socket 1 creation failed");
        exit(1);
    }

    memset(&server_addr, 0, sizeof(server_addr));
    if (ip_version == 4) {
        struct sockaddr_in *addr4 = (struct sockaddr_in *)&server_addr;
        addr4->sin_family = AF_INET;
        addr4->sin_port = htons(tcp_port);
        if (inet_pton(AF_INET, server_ip, &addr4->sin_addr) <= 0) {
            fprintf(stderr, "Invalid IPv4 address: %s\n", server_ip);
            close(tcp_sock1);
            exit(1);
        }
        addr_len = sizeof(struct sockaddr_in);
    } else {
        struct sockaddr_in6 *addr6 = (struct sockaddr_in6 *)&server_addr;
        addr6->sin6_family = AF_INET6;
        addr6->sin6_port = htons(tcp_port);
        if (inet_pton(AF_INET6, server_ip, &addr6->sin6_addr) <= 0) {
            fprintf(stderr, "Invalid IPv6 address: %s\n", server_ip);
            close(tcp_sock1);
            exit(1);
        }
        addr_len = sizeof(struct sockaddr_in6);
    }

    // Test connect() syscall
    printf("[TCP] Testing connect() to %s:%d...\n", server_ip, tcp_port);
    if (connect(tcp_sock1, (struct sockaddr*)&server_addr, addr_len) < 0) {
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
    tcp_sock2 = socket(af_family, SOCK_STREAM, 0);
    if (tcp_sock2 < 0) {
        perror("TCP socket 2 creation failed");
        exit(1);
    }

    printf("[TCP] Testing connect() to %s:%d...\n", server_ip, tcp_port);
    if (connect(tcp_sock2, (struct sockaddr*)&server_addr, addr_len) < 0) {
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
    udp_sock = socket(af_family, SOCK_DGRAM, 0);
    if (udp_sock < 0) {
        perror("UDP socket creation failed");
        exit(1);
    }

    // Setup UDP server address
    memset(&server_addr, 0, sizeof(server_addr));
    if (ip_version == 4) {
        struct sockaddr_in *addr4 = (struct sockaddr_in *)&server_addr;
        addr4->sin_family = AF_INET;
        addr4->sin_port = htons(udp_port);
        inet_pton(AF_INET, server_ip, &addr4->sin_addr);
        addr_len = sizeof(struct sockaddr_in);
    } else {
        struct sockaddr_in6 *addr6 = (struct sockaddr_in6 *)&server_addr;
        addr6->sin6_family = AF_INET6;
        addr6->sin6_port = htons(udp_port);
        inet_pton(AF_INET6, server_ip, &addr6->sin6_addr);
        addr_len = sizeof(struct sockaddr_in6);
    }

    // Test sendto() syscall
    printf("[UDP] Testing sendto() to %s:%d...\n", server_ip, udp_port);
    const char *udp_msg1 = "UDP message via sendto()";
    n = sendto(udp_sock, udp_msg1, strlen(udp_msg1), 0,
               (struct sockaddr*)&server_addr, addr_len);
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
    msg.msg_name = &server_addr;
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

    printf("\n=== Client completed all syscall tests ===\n");
    return 0;
}
