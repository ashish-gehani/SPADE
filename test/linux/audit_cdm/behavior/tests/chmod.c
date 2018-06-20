#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <stdlib.h>
#include <errno.h>
#include <string.h>
#include <fcntl.h>

int main(int argc, char **argv)
{
  char mode[] = "0777";
  char buf[100] = "testfile.txt";
  int i;

  

  /* sentinel == ignored */
  pid_t pid;
  pid = getpid();

  int filedesc = open("testfile.txt", O_CREAT | O_WRONLY | O_APPEND, S_IRUSR|S_IWUSR|S_IRGRP|S_IWGRP|S_IROTH);
 
  if (filedesc < 0) {
    return -1;
  }



  i = strtol(mode, 0, 8);
  if (chmod (buf,i) < 0)
    {
      fprintf(stderr, "%s: error in chmod(%s, %s) - %d (%s)\n",
	      argv[0], buf, mode, errno, strerror(errno));
      exit(1);
    }

  if (unlink("testfile.txt") < 0)
    perror("unlink");

  return(0);
}

