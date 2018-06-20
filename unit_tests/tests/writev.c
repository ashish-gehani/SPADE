#include <unistd.h>
#include <stdio.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/uio.h>
#include <unistd.h>
#include <string.h>

int main () {
  ssize_t bytes_written;
  int fd;
  char *buf0 = "short string\n";
  char *buf1 = "This is a longer string\n";
  char *buf2 = "This is the longest string in this example\n";
  int iovcnt;
  struct iovec iov[3];
  /* sentinel == ignored */
  pid_t pid;
  pid = getpid();

  fd = open("testfile.txt", O_CREAT | O_WRONLY | O_APPEND, S_IRUSR|S_IWUSR|S_IRGRP|S_IWGRP|S_IROTH);

  if (fd < 0) {
    return -1;
  }



  iov[0].iov_base = buf0;
  iov[0].iov_len = strlen(buf0);
  iov[1].iov_base = buf1;
  iov[1].iov_len = strlen(buf1);
  iov[2].iov_base = buf2;
  iov[2].iov_len = strlen(buf2);
  iovcnt = sizeof(iov) / sizeof(struct iovec);
  
  bytes_written = writev(fd, iov, iovcnt);

  if (unlink("testfile.txt") < 0)
    perror("unlink");
}
