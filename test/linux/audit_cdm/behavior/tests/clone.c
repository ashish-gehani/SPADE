#include <stdio.h>
#include <sched.h>
#include <stdlib.h>
#include <sys/wait.h>

int fn(void *arg)
{
  printf("\nINFO: This code is running under child process.\n");

  int i = 0;
   
  for ( i = 1 ; i <= 5 ; i++ )
    printf("hello");

  printf("\n");

  return 0;
}

int main(int argc, char *argv[])
{

  /* sentinel == ignored */
  pid_t pid;
  pid = getpid();

  printf("Hello, World!\n");

  void *pchild_stack = malloc(1024 * 1024);
  if ( pchild_stack == NULL ) {
    printf("ERROR: Unable to allocate memory.\n");
    exit(EXIT_FAILURE);
  }

  pid = clone(fn, pchild_stack + (1024 * 1024), SIGCHLD, "ignored");
  if ( pid < 0 ) {
    printf("ERROR: Unable to create the child process.\n");
    exit(EXIT_FAILURE);
  }

  wait(NULL);

  free(pchild_stack);

  printf("INFO: Child process terminated.\n");
}
