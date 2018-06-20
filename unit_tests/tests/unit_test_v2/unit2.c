#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include "UBSI.h"
#include <stdint.h>

#define MAX 50
pthread_mutex_t the_mutex;
pthread_cond_t condc;

char *buffer[MAX];

void* producer(void *ptr) {
  int i, n, fd;
		char fname[64], temp[16];

  for (i = 0; i < MAX; i++) {
				UBSI_LOOP_ENTRY(1);
				bzero(temp, 16);
				sprintf(fname, "/home/vinod/unit_tests/tests/unit_test_v2/input/data%02d.txt",i);
				fd = open(fname, O_RDONLY);
				if(fd < 0) {
						printf("file open fails: %s\n", fname);
						continue;
				}

				buffer[i] = (char*) malloc(16);
				n = read(fd, temp, 16);

    pthread_mutex_lock(&the_mutex);
				UBSI_MEM_WRITE(buffer[i]);
				memcpy(buffer[i], temp, 16);
    pthread_cond_signal(&condc);
    pthread_mutex_unlock(&the_mutex);
				close(fd);
  }

		UBSI_LOOP_EXIT(1);
  pthread_exit(0);
}

void* consumer(void *ptr) {
  int i, n, fd;
		char temp[16], fname[64];

  for (i = 0; i < MAX; i++) {
				UBSI_LOOP_ENTRY(2);
    pthread_mutex_lock(&the_mutex);	
    while (buffer[i] == NULL)		
      pthread_cond_wait(&condc, &the_mutex);
    pthread_mutex_unlock(&the_mutex);

				UBSI_MEM_READ(buffer[i]);
				memcpy(temp, buffer[i], 16);
				sprintf(fname, "/home/vinod/unit_tests/tests/unit_test_v2/output/output%02d.txt", i);

				fd = open(fname, O_RDWR|O_CREAT, 0644);
				if(fd < 0) {
						printf("file open fails: %s\n", fname);
						continue;
				}
				n = write(fd, temp, strlen(temp));
				close(fd);
  }
		UBSI_LOOP_EXIT(2);
  pthread_exit(0);
}

int main(int argc, char **argv) {
  pthread_t pro, con;
		int i;
		for(i = 0; i < MAX; i++)
		{
			 buffer[i] = NULL;
		}

  pthread_mutex_init(&the_mutex, NULL);	
  pthread_cond_init(&condc, NULL);

   /* sentinel == ignored */
  pid_t pid;
  pid = getpid();

  pthread_create(&con, NULL, consumer, NULL);
  pthread_create(&pro, NULL, producer, NULL);

  pthread_join(pro, NULL);
  pthread_join(con, NULL);

  pthread_mutex_destroy(&the_mutex);
  pthread_cond_destroy(&condc);

}
