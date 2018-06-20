#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>

int main()
{

  /* sentinel == ignored */
  pid_t pid;
  pid = getpid();


  int fd = open("/tmp/a.txt", O_CREAT | O_WRONLY, 0600);
  char buf[64];

  if (fd < 0) {
    perror("open");
    return;
  }

  sprintf(buf, "/proc/self/fd/%d", fd);
  int rval = linkat(AT_FDCWD, buf, AT_FDCWD, "/tmp/linkat.out", AT_SYMLINK_FOLLOW);

  if (rval != 0) {
    perror("linkat");
    return;
  }

  if (unlink("/tmp/linkat.out") != 0)
    perror("unlink");

  if (unlink("/tmp/a.txt") != 0)
    perror("unlink");


  
}
