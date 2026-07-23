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
#include <netinet/in.h>
#include <arpa/inet.h>

#define BUFFER_SIZE 1024

void print_usage(const char *prog) {
    printf("Usage: %s <ip_version> <tcp_port> <udp_port>\n", prog);
    printf("  ip_version: 4 for IPv4, 6 for IPv6\n");
    printf("  tcp_port: TCP port number\n");
    printf("  udp_port: UDP port number\n");
    printf("Example: %s 4 8080 8081\n", prog);
    printf("Example: %s 6 8080 8081\n", prog);
}

void print_client_address(struct sockaddr_storage *addr, int ip_version) {
    char ip_str[INET6_ADDRSTRLEN];
    int port;

    if (ip_version == 4) {
        struct sockaddr_in *addr4 = (struct sockaddr_in *)addr;
        inet_ntop(AF_INET, &addr4->sin_addr, ip_str, sizeof(ip_str));
        port = ntohs(addr4->sin_port);
    } else {
        struct sockaddr_in6 *addr6 = (struct sockaddr_in6 *)addr;
        inet_ntop(AF_INET6, &addr6->sin6_addr, ip_str, sizeof(ip_str));
        port = ntohs(addr6->sin6_port);
    }

    printf("%s:%d", ip_str, port);
}

int main(int argc, char *argv[]) {
    int tcp_sock, udp_sock;
    int tcp_client, tcp_client4;
    struct sockaddr_storage tcp_addr, udp_addr, client_addr;
    socklen_t addr_len;
    char buffer[BUFFER_SIZE];
    ssize_t n;
    int ip_version;
    int tcp_port, udp_port;
    int af_family;

    if (argc != 4) {
        print_usage(argv[0]);
        exit(1);
    }

    ip_version = atoi(argv[1]);
    tcp_port = atoi(argv[2]);
    udp_port = atoi(argv[3]);

    if (ip_version != 4 && ip_version != 6) {
        fprintf(stderr, "Error: IP version must be 4 or 6\n");
        print_usage(argv[0]);
        exit(1);
    }

    af_family = (ip_version == 4) ? AF_INET : AF_INET6;

    printf("=== Socket Syscall Test Server (IPv%d) ===\n", ip_version);
    printf("TCP Port: %d, UDP Port: %d\n", tcp_port, udp_port);

    // Create TCP socket
    tcp_sock = socket(af_family, SOCK_STREAM, 0);
    if (tcp_sock < 0) {
        perror("TCP socket creation failed");
        exit(1);
    }

    // Enable address reuse
    int opt = 1;
    setsockopt(tcp_sock, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    // Setup TCP address
    memset(&tcp_addr, 0, sizeof(tcp_addr));
    if (ip_version == 4) {
        struct sockaddr_in *addr4 = (struct sockaddr_in *)&tcp_addr;
        addr4->sin_family = AF_INET;
        addr4->sin_addr.s_addr = INADDR_ANY;
        addr4->sin_port = htons(tcp_port);
        addr_len = sizeof(struct sockaddr_in);
    } else {
        struct sockaddr_in6 *addr6 = (struct sockaddr_in6 *)&tcp_addr;
        addr6->sin6_family = AF_INET6;
        addr6->sin6_addr = in6addr_any;
        addr6->sin6_port = htons(tcp_port);
        addr_len = sizeof(struct sockaddr_in6);
    }

    // Test bind() syscall on TCP socket
    if (bind(tcp_sock, (struct sockaddr*)&tcp_addr, addr_len) < 0) {
        perror("TCP bind failed");
        close(tcp_sock);
        exit(1);
    }
    printf("[TCP] bind() successful on port %d\n", tcp_port);

    // Listen on TCP socket
    if (listen(tcp_sock, 5) < 0) {
        perror("TCP listen failed");
        close(tcp_sock);
        exit(1);
    }
    printf("[TCP] Listening for connections...\n");

    // Create UDP socket
    udp_sock = socket(af_family, SOCK_DGRAM, 0);
    if (udp_sock < 0) {
        perror("UDP socket creation failed");
        close(tcp_sock);
        exit(1);
    }

    // Setup UDP address
    memset(&udp_addr, 0, sizeof(udp_addr));
    if (ip_version == 4) {
        struct sockaddr_in *addr4 = (struct sockaddr_in *)&udp_addr;
        addr4->sin_family = AF_INET;
        addr4->sin_addr.s_addr = INADDR_ANY;
        addr4->sin_port = htons(udp_port);
    } else {
        struct sockaddr_in6 *addr6 = (struct sockaddr_in6 *)&udp_addr;
        addr6->sin6_family = AF_INET6;
        addr6->sin6_addr = in6addr_any;
        addr6->sin6_port = htons(udp_port);
    }

    // Test bind() syscall on UDP socket
    if (bind(udp_sock, (struct sockaddr*)&udp_addr, addr_len) < 0) {
        perror("UDP bind failed");
        close(tcp_sock);
        close(udp_sock);
        exit(1);
    }
    printf("[UDP] bind() successful on port %d\n", udp_port);

    // Test accept() syscall - wait for first TCP connection
    printf("[TCP] Waiting for first client (accept)...\n");
    socklen_t client_len = sizeof(client_addr);
    tcp_client = accept(tcp_sock, (struct sockaddr*)&client_addr, &client_len);
    if (tcp_client < 0) {
        perror("accept failed");
    } else {
        printf("[TCP] accept() successful - client connected from ");
        print_client_address(&client_addr, ip_version);
        printf("\n");

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
        printf("[TCP] accept4() successful - client connected from ");
        print_client_address(&client_addr, ip_version);
        printf("\n");

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
        printf("[UDP] recvfrom() received: %s from ", buffer);
        print_client_address(&client_addr, ip_version);
        printf("\n");

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
        printf("[UDP] recvmsg() received: %s from ", buffer);
        print_client_address(&client_addr, ip_version);
        printf("\n");

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

    // Cleanup
    close(tcp_sock);
    close(udp_sock);

    printf("\n=== Server completed all syscall tests ===\n");
    return 0;
}
