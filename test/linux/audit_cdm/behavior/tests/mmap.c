#include <stdio.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <sys/mman.h>

int main(int argc, char const *argv[])
{
    char *f;
    int size;
    struct stat s;
    /* sentinel == ignored */
    pid_t pid;
    pid = getpid();

    const char * file_name = "/etc/timezone";
    int fd = open (file_name, O_RDONLY);
    
    if (fd < 0) {
      perror("open");
      return;
    }
    
    int i;
    /* Get the size of the file. */
    int status = fstat (fd, & s);
    size = s.st_size;

    f = (char *) mmap (0, size, PROT_READ, MAP_PRIVATE, fd, 0);
    for (i = 0; i < size; i++) {
        char c;

        c = f[i];
        putchar(c);
    }

    return 0;
}
