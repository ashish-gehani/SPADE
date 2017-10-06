#include <unistd.h>
#include <stdio.h>
#include <fcntl.h>
 
int main(void)
{
  /* sentinel == ignored */
  pid_t pid;
  pid = getpid();

  int filedesc = open("testfile.txt", O_CREAT | O_WRONLY | O_APPEND, S_IRUSR|S_IWUSR|S_IRGRP|S_IWGRP|S_IROTH);
 
  if (filedesc < 0) {
    return -1;
  }
 
  if (write(filedesc, "This will be output to testfile.txt\n", 36) != 36) {
    write(2, "There was an error writing to testfile.txt\n", 43);
    return -1;
  }

  close(filedesc);

  if (truncate("testfile.txt", 24) < 0)
    perror("truncate");

  if (unlink("testfile.txt") < 0)
    perror("unlink");
  
 
  return 0;
}
