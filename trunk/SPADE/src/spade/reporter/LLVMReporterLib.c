#include <sys/socket.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <netdb.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>

#if defined(_LLVMREPORTER_MACOSX)
#include <pthread.h>
#elif defined(_LLVMREPORTER_LINUX)
#include <sys/syscall.h>
#elif defined(_LLVMREPORTER_WINDOWS)
//Ian says: let me know if you want windows...
#endif

int LLVMSocket = 5000;

/* turn this on to use independently of the spade server; reporting goes to stderr */
#define _IANS_DEBUGGING  0

#if _IANS_DEBUGGING == 1
int sock = STDERR_FILENO;
#else
int sock = -1;
#endif

FILE* socket_fp = NULL;

unsigned long LLVMReporter_getThreadId() {
    unsigned long retval = 0;
#if defined(_LLVMREPORTER_MACOSX)
    //look at pthread.h for this stuff; it's unique to a mac
    //N.B. on my(all?) macs the value of pthread_self() is always the same for the main thread (regardless of which pid it has)
    //hence the extra yards...
    __uint64_t tid;
    pthread_t self = pthread_self();
    pthread_threadid_np(self, &tid);
    retval = tid;
#elif defined(_LLVMREPORTER_LINUX)
    retval = syscall(SYS_gettid);
#elif defined(_LLVMREPORTER_WINDOWS)
    retval = 0;
#endif
    //fprintf(stderr, "LLVMReporter_getThreadId(%lu)\n", retval);
    return retval;
}

int llvm_close(int fd) {
    if ((sock != -1) && (sock == fd)) {
        return 0;
    } else {
        //don't close stderr
        return close(fd);
    }
}

FILE* LLVMReporter_getSocket() {
    if (socket_fp == NULL) {

        if (sock == -1) {
            struct hostent *host;
            struct sockaddr_in server_addr;

            host = gethostbyname("127.0.0.1");

            if ((sock = socket(AF_INET, SOCK_STREAM, 0)) == -1) {
                perror("Unable to create client socket from LLVM instrumented program to SPADE");
                exit(1);
            }

            server_addr.sin_family = AF_INET;
            server_addr.sin_port = htons(LLVMSocket);
            server_addr.sin_addr = *((struct in_addr *) host->h_addr);
            bzero(&(server_addr.sin_zero), 8);

            if (connect(sock, (struct sockaddr *) &server_addr,
                    sizeof (struct sockaddr)) == -1) {
                perror("Unable to connect from LLVM instrumented program to SPADE");
                exit(1);
            }
        }
        socket_fp = fdopen(sock, "r+");
    }

    return socket_fp;
}
