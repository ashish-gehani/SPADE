#include <stdlib.h>/* needed to define exit() */
#include <unistd.h>/* needed to define getpid() */
#include <stdio.h>/* needed for printf() */

int
main(int argc, char **argv) {
  char *args[] = {"date", 0};/* each element represents a command line argument */
  char *env[] = { 0 };/* leave the environment list null */
  /* sentinel == ignored */
  pid_t pid;
  pid = getpid();

  printf("About to run date\n");
  execve("/bin/date", args, env);
  perror("execve");/* if we get here, execve failed */
  //  exit(1);
}
