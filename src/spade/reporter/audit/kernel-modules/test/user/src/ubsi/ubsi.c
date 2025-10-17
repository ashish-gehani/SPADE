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
#include <signal.h>
#include <errno.h>
#include <sys/types.h>

#define UBSI_UENTRY		0xffffff9c
#define UBSI_UENTRY_ID	0xffffff9a
#define UBSI_UEXIT		0xffffff9b
#define UBSI_MREAD1		0xffffff38
#define UBSI_MREAD2		0xffffff37
#define UBSI_MWRITE1 	0xfffffed4
#define UBSI_MWRITE2 	0xfffffed3
#define UBSI_UDEP		0xfffffe70

// List of PIDs to send signals to
static pid_t test_pids[] = {
    UBSI_UENTRY,     // UBSI: Unit entry marker (-100)
    UBSI_UENTRY_ID,  // UBSI: Unit entry ID marker (-102)
    UBSI_UEXIT,      // UBSI: Unit exit marker (-101)
    UBSI_MREAD1,     // UBSI: Memory read marker 1 (-200)
    UBSI_MREAD2,     // UBSI: Memory read marker 2 (-201)
    UBSI_MWRITE1,    // UBSI: Memory write marker 1 (-300)
    UBSI_MWRITE2,    // UBSI: Memory write marker 2 (-301)
    UBSI_UDEP,       // UBSI: Unit dependency marker (-400)
};

// List of signals to test
static int test_signals[] = {
    SIGUSR1,    // 10 - 0xA - User-defined signal 1
};

const char* signal_name(int sig) {
    switch(sig) {
        case SIGUSR1: return "SIGUSR1";
        default: return "UNKNOWN";
    }
}

void signal_handler(int sig) {
    printf("[SIGNAL HANDLER] Received signal %d (%s)\n", sig, signal_name(sig));
}

void setup_signal_handlers() {
    // Install handlers for signals we can catch
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = signal_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0;

    // Install handlers for catchable signals
    sigaction(SIGUSR1, &sa, NULL);
}

void test_kill_signal_to_pid(pid_t pid, int sig) {
    printf("\n--- Testing kill(pid=%d, sig=%d [%s]) ---\n", pid, sig, signal_name(sig));

    kill(pid, sig);
}

void test_kill_to_all_pids_with_signal(int sig) {
    printf("\n=== Testing signal %d (%s) to all PIDs ===\n", sig, signal_name(sig));

    size_t num_pids = sizeof(test_pids) / sizeof(test_pids[0]);

    for (size_t i = 0; i < num_pids; i++) {
        test_kill_signal_to_pid(test_pids[i], sig);
        usleep(10000); // 10ms delay between signals
    }
}

int main(int argc, char *argv[]) {
    printf("=== UBSI Kill Syscall Test ===\n");
    printf("Current PID: %d, UID: %d, GID: %d\n", getpid(), getuid(), getgid());

    // Setup signal handlers to catch signals sent to ourselves
    setup_signal_handlers();

    printf("\nTest PIDs: [");
    size_t num_pids = sizeof(test_pids) / sizeof(test_pids[0]);
    for (size_t i = 0; i < num_pids; i++) {
        printf("%d%s", test_pids[i], (i < num_pids - 1) ? ", " : "");
    }
    printf("]\n");

    printf("\nTest Signals: [");
    size_t num_signals = sizeof(test_signals) / sizeof(test_signals[0]);
    for (size_t i = 0; i < num_signals; i++) {
        printf("%d:%s%s", test_signals[i], signal_name(test_signals[i]),
               (i < num_signals - 1) ? ", " : "");
    }
    printf("]\n");

    // Test 1: Send SIGUSR1 to all PIDs
    printf("\n\n=== TEST 1: Sending SIGUSR1 to all PIDs ===\n");
    test_kill_to_all_pids_with_signal(SIGUSR1);

    // Test 2: Send SIGUSR1 to self
    printf("\n\n=== TEST 2: Sending SIGUSR1 to self (PID %d) ===\n", getpid());
    test_kill_signal_to_pid(getpid(), SIGUSR1);
    usleep(50000); // 50ms delay to allow signal handler to run

    printf("\n=== All tests completed ===\n");
    return 0;
}
