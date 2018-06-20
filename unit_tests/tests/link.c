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

  close(fd);

  int rval = link("/tmp/a.txt", "/tmp/link.out");

  if (rval != 0) {
    perror("link");
    return;
  }

  if (unlink("/tmp/link.out") != 0)
    perror("unlink");

  if (unlink("/tmp/a.txt") != 0)
    perror("unlink");


  
}
