#include <unistd.h> /*Included for dup(2) and write(2)*/
 
#include <stdlib.h> /*Included for exit(3)*/
 
#define MESSAGE "Hey! Who redirected me?\r\n\0"
 
int main() {

  char buff[] = MESSAGE;
  /* sentinel == ignored */
  pid_t pid;
  pid = getpid();
  int newfd = dup(STDOUT_FILENO); /*Call dup for an aliased fd*/
 
  if (newfd < 0) { /*Negative file descriptors are errors*/
    exit(EXIT_FAILURE);
  }
  else if (write(newfd, buff, sizeof(buff)) < 0) { /*See: man 2 write*/
    exit(EXIT_FAILURE);
  }
 
  return EXIT_SUCCESS;
}
