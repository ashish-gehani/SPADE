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
#include <sys/wait.h>
#include <sys/syscall.h>
#include <sched.h>
#include <signal.h>
#include <fcntl.h>

#define STACK_SIZE (1024 * 1024)

// Child function for clone
static int child_func(void *arg) {
    char *msg = (char *)arg;
    printf("[CLONE CHILD] PID: %d, Message: %s\n", getpid(), msg);
    return 0;
}

void test_fork() {
    printf("\n=== Testing fork() via syscall() ===\n");

    // Explicitly call fork syscall using syscall()
    pid_t pid = syscall(SYS_fork);

    if (pid < 0) {
        perror("fork syscall failed");
        return;
    }

    if (pid == 0) {
        // Child process
        printf("[FORK CHILD] PID: %d, Parent PID: %d\n", getpid(), getppid());
        exit(0);
    } else {
        // Parent process
        printf("[FORK PARENT] Created child with PID: %d (via SYS_fork syscall)\n", pid);
        waitpid(pid, NULL, 0);
        printf("[FORK PARENT] Child exited\n");
    }
}

void test_vfork() {
    printf("\n=== Testing vfork() ===\n");

    pid_t pid = vfork();

    if (pid < 0) {
        perror("vfork failed");
        return;
    }

    if (pid == 0) {
        // Child process
        printf("[VFORK CHILD] PID: %d, Parent PID: %d\n", getpid(), getppid());
        _exit(0);  // Must use _exit() after vfork
    } else {
        // Parent process
        printf("[VFORK PARENT] Created child with PID: %d\n", pid);
        waitpid(pid, NULL, 0);
        printf("[VFORK PARENT] Child exited\n");
    }
}

void test_clone() {
    printf("\n=== Testing clone() ===\n");

    // Allocate stack for child
    char *stack = malloc(STACK_SIZE);
    if (!stack) {
        perror("malloc failed");
        return;
    }

    char *stack_top = stack + STACK_SIZE;
    char *msg = "Hello from clone!";

    // Create child process with clone
    pid_t pid = clone(child_func, stack_top, SIGCHLD, msg);

    if (pid < 0) {
        perror("clone failed");
        free(stack);
        return;
    }

    printf("[CLONE PARENT] Created child with PID: %d\n", pid);
    waitpid(pid, NULL, 0);
    printf("[CLONE PARENT] Child exited\n");

    free(stack);
}

void test_clone_with_namespaces() {
    printf("\n=== Testing clone() with user namespace ===\n");

    // Allocate stack for child
    char *stack = malloc(STACK_SIZE);
    if (!stack) {
        perror("malloc failed");
        return;
    }

    char *stack_top = stack + STACK_SIZE;
    char *msg = "In new user namespace!";

    // Create child with new user namespace (unprivileged)
    int flags = CLONE_NEWUSER | SIGCHLD;
    pid_t pid = clone(child_func, stack_top, flags, msg);

    if (pid < 0) {
        perror("clone with user namespace failed");
        free(stack);
        return;
    }

    printf("[CLONE NS PARENT] Created child with PID: %d\n", pid);
    waitpid(pid, NULL, 0);
    printf("[CLONE NS PARENT] Child exited\n");

    free(stack);
}

void test_unshare() {
    printf("\n=== Testing unshare() with user namespace ===\n");

    pid_t pid = fork();

    if (pid < 0) {
        perror("fork failed");
        return;
    }

    if (pid == 0) {
        // Child process - test unshare
        printf("[UNSHARE CHILD] PID before unshare: %d, UID: %d\n", getpid(), getuid());

        // Unshare user namespace (unprivileged)
        if (unshare(CLONE_NEWUSER) < 0) {
            perror("unshare(CLONE_NEWUSER) failed");
        } else {
            printf("[UNSHARE CHILD] Successfully unshared user namespace\n");
            printf("[UNSHARE CHILD] UID after unshare: %d\n", getuid());
        }

        exit(0);
    } else {
        // Parent process
        printf("[UNSHARE PARENT] Created child with PID: %d\n", pid);
        waitpid(pid, NULL, 0);
        printf("[UNSHARE PARENT] Child exited\n");
    }
}

void test_setns() {
    printf("\n=== Testing setns() with user namespace ===\n");

    // First, create a child process with new user namespace
    char *stack = malloc(STACK_SIZE);
    if (!stack) {
        perror("malloc failed");
        return;
    }

    char *stack_top = stack + STACK_SIZE;
    char *msg = "User namespace target";

    // Create child with new user namespace (unprivileged)
    int flags = CLONE_NEWUSER | SIGCHLD;
    pid_t child_pid = clone(child_func, stack_top, flags, msg);

    if (child_pid < 0) {
        perror("clone failed for setns test");
        free(stack);
        return;
    }

    printf("[SETNS PARENT] Created namespace target with PID: %d\n", child_pid);

    // Try to setns into the child's user namespace
    char ns_path[256];
    snprintf(ns_path, sizeof(ns_path), "/proc/%d/ns/user", child_pid);

    int ns_fd = open(ns_path, O_RDONLY);
    if (ns_fd < 0) {
        perror("open namespace fd failed");
    } else {
        printf("[SETNS PARENT] Opened namespace fd: %s\n", ns_path);

        if (setns(ns_fd, CLONE_NEWUSER) < 0) {
            perror("setns failed");
        } else {
            printf("[SETNS PARENT] Successfully joined user namespace of PID %d\n", child_pid);
        }

        close(ns_fd);
    }

    waitpid(child_pid, NULL, 0);
    printf("[SETNS PARENT] Child exited\n");

    free(stack);
}

int main(int argc, char *argv[]) {
    printf("=== Process Creation and Namespace Syscall Test (Unprivileged) ===\n");
    printf("Parent PID: %d, UID: %d\n", getpid(), getuid());

    // Test fork
    test_fork();

    // Test vfork
    test_vfork();

    // Test clone (basic)
    test_clone();

    // Test clone with user namespace (unprivileged)
    test_clone_with_namespaces();

    // Test unshare with user namespace (unprivileged)
    test_unshare();

    // Test setns with user namespace (unprivileged)
    test_setns();

    printf("\n=== All tests completed ===\n");
    return 0;
}
