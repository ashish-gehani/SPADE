#include <unistd.h>
#include <stdio.h>

int main (int argc, char** argv) 
{
  /* sentinel == ignored */
  pid_t pid;
  pid = getpid();

  if (symlink("/tmp", "a") < 0) 
    printf("symlink creation failed\n");
  if (unlink("a") < 0)
    printf("unlink failed\n");
  

}
