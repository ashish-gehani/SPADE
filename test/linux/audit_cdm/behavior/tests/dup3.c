#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>
 

 
int main()
{
  /* sentinel == ignored */
  pid_t pid;
  pid = getpid();

  //First, we're going to open a file
  int file = open("/tmp/myfile.txt", O_CREAT | O_WRONLY | O_RDONLY,
		  S_IRUSR|S_IWUSR|S_IRGRP|S_IWGRP|S_IROTH);

  if(file < 0)  {
    printf("error opening file\n");
    return 1;
  }
 
  //Now we redirect standard output to the file using dup2
  if(dup3(file,1,O_CLOEXEC) < 0)    return 1;
 
  //Now standard out has been redirected, we can write to
  // the file
  printf("This will print in myfile.txt\n"); 
  //At the end the file has to be closed:
  close(file);

  unlink("/tmp/myfile.txt");
 
  return 0;
}//end of function main
