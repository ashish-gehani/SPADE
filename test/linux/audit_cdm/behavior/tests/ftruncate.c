#include <stdio.h>
#include <unistd.h>

int main() {

  /* sentinel == ignored */
  pid_t pid;
  pid = getpid();

  FILE* f=fopen("/tmp/a.txt","w");
  if (f == NULL)
    perror("fopen");
  else {
    fputs("some string",f);
    fclose(f);
  }
  
  fflush(f);
  ftruncate(fileno(f),(off_t)0);
  unlink("/tmp/a.txt");
  
}
