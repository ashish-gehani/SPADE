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
  char fifoName[]="/tmp/testfifo";
  char errMsg[1000];
  FILE *cfp;
  FILE *pfp;
  /* sentinel == ignored */
  pid = getpid();

  ret = mknod(fifoName, S_IFIFO | 0600, 0); 
  /* 0600 gives read, write permissions to user and none to group and world */
  if(ret < 0){
    sprintf(errMsg,"Unable to create fifo: %s",fifoName);
    exit(1);
  }

  pid=fork();
  if(pid == 0){
    /* child -- open the named pipe and write an integer to it */

    cfp = fopen(fifoName,"w");
    if(cfp == NULL) 
      exit(1);
    ret=fprintf(cfp,"%d",1000);
    fflush(cfp);
    exit(0);
  } 

  else{
    /* parent - open the named pipe and read an integer from it */
    pfp = fopen(fifoName,"r");
    if(pfp == NULL) 
      exit(1);
    ret=fscanf(pfp,"%d",&value);
    if(ret < 0) 
      exit(1);
    fclose(pfp);
    printf("This is the parent. Received value %d from child on fifo \n", value);
    unlink(fifoName); /* Delete the created fifo */
    exit(0);
  }
}
