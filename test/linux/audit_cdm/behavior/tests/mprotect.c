#include <stdio.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <sys/mman.h>


int main( int argc, char *argv[]){
  /* sentinel == ignored */
  pid_t pid2;
  pid2 = getpid();

  // pid: the process ID of this process 
  // so we can print it out
  int pid;
  pid = getpid();

  //size: an integer to hold the current page size
  int size;
  size = getpagesize();

  //[1] create two pointers in order to allocate 
  //memory regions
  char *buffer;
  char *buffer2;

  //unprotected buffer:
  //allocate memory using mmap()
  buffer2 = (caddr_t) mmap(NULL,
			   size,
			   PROT_READ|PROT_WRITE,
			   MAP_PRIVATE | MAP_ANON,
			   0,0);



  //[2] put some characters in the allocated memory
  //we're setting these characters one at a time in order
  //to avoid our strings being detected from the binary itself
  buffer2[0] = 'n';
  buffer2[1] = 'o';
  buffer2[2] = 't';
  buffer2[3] = ' ';
  buffer2[4] = 'h';
  buffer2[5] = 'e';
  buffer2[6] = 'r';
  buffer2[7] = 'e';


    
  //protected buffer:
  //allocate memory with mmap() like before
  buffer = (caddr_t) mmap(NULL,
			  size,
			  PROT_READ|PROT_WRITE,
			  MAP_PRIVATE|MAP_ANON,
			  0,0);



  //[2] put some characters in the allocated memory
  //we're setting these characters one at a time in order
  //to avoid our strings being detected from the binary itself
  buffer[0] = 'f';
  buffer[1] = 'i';
  buffer[2] = 'n';
  buffer[3] = 'd';
  buffer[4] = ' ';
  buffer[5] = 'm';
  buffer[6] = 'e';

  //[3] protect the page with PROT_NONE:
  mprotect(buffer, size, PROT_NONE);
  


  //[4] print PID and buffer addresses:
  printf("PID %d\n", pid);
  printf("buffer at %p\n", buffer);
  printf("buffer2 at %p\n", buffer2);

  //spin until killed so that we know it's in memory:
  // while(1);
  return 0;
}
