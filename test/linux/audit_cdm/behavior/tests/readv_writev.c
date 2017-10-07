#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <sys/uio.h>

int main ()
{
  struct iovec iov[3];
  ssize_t nr;
  int fd, i;
  char foo[48], bar[51], baz[49];

  char *buf[] = {
    "The term buccaneer comes from the word boucan.\n",
    "A boucan is a wooden frame used for cooking meat.\n",
    "Buccaneer is the West Indies name for a pirate.\n" };

  /* sentinel == ignored */
  pid_t pid;
  pid = getpid();

  fd = open ("buccaneer.txt", O_RDWR | O_CREAT | O_TRUNC,  S_IRUSR | S_IWUSR);

  
  if (fd == -1) {
    perror ("open");
    return 1;
  }
  
  /* fill out three iovec structures */
  for (i = 0; i < 3; i++) {
    iov[i].iov_base = buf[i];
    iov[i].iov_len = strlen(buf[i]) + 1;
  }
  /* with a single call, write them all out */
  nr = writev (fd, iov, 3);
  if (nr == -1) {
    perror ("writev");
    return 1;
  }
  printf ("wrote %d bytes\n", (int) nr);

  if (close (fd)) {
    perror ("close");
    return 1;
  }


  fd = open ("buccaneer.txt", O_RDONLY);
  if (fd == -1) {
    perror ("open");
    return 1;
  }

  /* set up our iovec structures */
  iov[0].iov_base = foo;
  iov[0].iov_len = sizeof (foo);
  iov[1].iov_base = bar;
  iov[1].iov_len = sizeof (bar);
  iov[2].iov_base = baz;
  iov[2].iov_len = sizeof (baz);

  /* read into the structures with a single call */
  nr = readv (fd, iov, 3);
  if (nr == -1) {
    perror ("readv");
    return 1;
  }

  for (i = 0; i < 3; i++)
    printf ("%d: %s", i, (char *) iov[i].iov_base);

  if (close (fd)) {
    perror ("close");
    return 1;
  }

  sleep(1);

  if (unlink("buccaneer.txt") != 0)
    perror("unlink() error");

  return 0;


}
