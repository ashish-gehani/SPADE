#include<stdio.h>
#include<sys/stat.h>
#include<sys/types.h>
#include<fcntl.h>

int main()
{
  /* sentinel == ignored */
  pid_t pid;
  int fd;
  pid = getpid();

  fd=open("/tmp/b.txt",O_CREAT|O_RDONLY);
  fchmod(fd,S_IRUSR|S_IWUSR|S_IRGRP|S_IWGRP|S_IROTH);

  if (unlink("/tmp/b.txt") == -1)
    perror("unlink");

}
