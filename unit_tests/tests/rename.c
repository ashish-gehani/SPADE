#include<stdio.h>
#include<sys/stat.h>
#include<sys/types.h>
#include<fcntl.h>
#include<errno.h>

int main()
{
  /* sentinel == ignored */
  pid_t pid;
  pid = getpid();

  int fd=open("/tmp/b.txt",O_CREAT|O_RDONLY, S_IRUSR|S_IWUSR|S_IRGRP|S_IWGRP|S_IROTH);

  if (fd == -1) {
    perror("open");
    return;
  }

  if (rename("/tmp/b.txt", "/tmp/a.txt") == -1) {
    perror("rename");
    return;
  }

  if (unlink("/tmp/a.txt") == -1)
    perror("unlink");
}
