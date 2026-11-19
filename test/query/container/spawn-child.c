/*
 * B10: start /bin/true with posix_spawn and wait for it.
 *
 * Built static against glibc, so that inside an alpine container posix_spawn is
 * glibc's, which creates the child with clone3 (musl, alpine's libc, uses clone).
 * Prints the child's PID as the program sees it, inside its PID namespace.
 *
 * Build: gcc -static -O2 -o spawn-child spawn-child.c
 */
#include <spawn.h>
#include <stdio.h>
#include <sys/wait.h>

extern char **environ;

int main(void)
{
    pid_t child;
    char *argv[] = {"true", NULL};
    int err = posix_spawn(&child, "/bin/true", NULL, NULL, argv, environ);

    if (err != 0) {
        fprintf(stderr, "posix_spawn failed: %d\n", err);
        return 1;
    }
    waitpid(child, NULL, 0);
    printf("%d\n", (int)child);
    return 0;
}
