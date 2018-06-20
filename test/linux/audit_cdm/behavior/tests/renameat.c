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

  int fd=open("b.txt",O_CREAT|O_RDONLY, S_IRUSR|S_IWUSR|S_IRGRP|S_IWGRP|S_IROTH);

  if (renameat(AT_FDCWD, "b.txt",AT_FDCWD, "a.txt") == -1)
    perror("rename");
  unlink("a.txt");
}
