#include <stdio.h>

/* will hide this version of close when we link close.o with the "host" program  into a shared library */
#define HIDDEN __attribute__((visibility("hidden"))) 

int llvm_close(int close);

HIDDEN int close(int fd){
  return llvm_close(fd);
}
