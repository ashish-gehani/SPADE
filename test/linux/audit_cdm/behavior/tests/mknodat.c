#include<stdio.h>
#include<fcntl.h>
#include<string.h>
#include<sys/stat.h>
#include<unistd.h> 
#include<stdlib.h>

int main()
{
  int ret;
  pid_t pid;
  int value;
  char fifoName[]="testfifo";
  char errMsg[1000];
  FILE *cfp;
  FILE *pfp;
  /* sentinel == ignored */
  pid = getpid();

  ret = mknodat(AT_FDCWD, fifoName, S_IFIFO | 0600, 0); 
  /* 0600 gives read, write permissions to user and none to group and world */
  if(ret < 0){
    sprintf(errMsg,"Unable to create fifo: %s",fifoName);
    exit(1);
  }

}
