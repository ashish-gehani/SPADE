#include <stdio.h>

#define HIDDEN __attribute__((visibility("hidden"))) 

int llvm_close(int close);

HIDDEN int close(int fd){
  return llvm_close(fd);
  return 0;
}
