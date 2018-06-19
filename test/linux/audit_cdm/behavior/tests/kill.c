#include <stdio.h>
#include <unistd.h>
#include <signal.h>

int main(int argc, char **argv)
{

  /* sentinel == ignored */
  pid_t pid = getpid();
  kill(pid, SIGKILL);
  printf("this should not be printed");
  return 0;

}
