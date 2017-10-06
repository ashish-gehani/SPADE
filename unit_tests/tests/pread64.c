#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>

main() {
  int ret, file_descriptor;
  off_t off=5;
  char buf[]="Test text";
  int rc;

  /* sentinel == ignored */
  pid_t pid;
  pid = getpid();

  if ((file_descriptor = creat("/tmp/test.output", S_IWUSR|S_IRUSR)) < 0) 
    perror("creat() error");
  
  else {
    if (write(file_descriptor, buf, sizeof(buf)-1) < 0)
      perror("write() error");
    if (close(file_descriptor)!= 0)
      perror("close() error");
  }

  if ((file_descriptor = open("/tmp/test.output", O_RDONLY)) < 0)
    perror("open() error");
  else {
    ret = pread64(file_descriptor, buf, ((sizeof(buf)-1)-off), off);
    buf[ret] = 0x00;
    printf("block pread: \n<%s>\n", buf);
    if (close(file_descriptor)!= 0)
      perror("close() error");
  }
  if (unlink("/tmp/test.output")!= 0)
    perror("unlink() error");
}
