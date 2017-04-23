#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>

int main (int argc, char** argv) 
{
  /* sentinel == ignored */
  pid_t pid;
  pid = getpid();

  if (symlinkat("/tmp", AT_FDCWD, "a") < 0) 
    printf("symlink creation failed\n");
  if (unlinkat(AT_FDCWD, "a", 0) < 0)
    printf("unlink failed\n");
  

}
