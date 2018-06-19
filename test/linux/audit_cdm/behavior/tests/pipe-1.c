#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <fcntl.h>

/*
 * simple program to create and use a pipe
 */
int main(int argc, char **argv)
{
	int pipe_desc[2];
	int err;
	char *string;
	char read_buffer[4096];

	/* sentinel == ignored */
	pid_t pid;
        pid = getpid();



	err = pipe(pipe_desc);
	if(err < 0) {
		printf("error creating pipe\n");
		exit(1);
	}

	string = "a string";

	printf("writing %s to pipe_desc[1] which is %d\n",
			string,pipe_desc[1]);

	write(pipe_desc[1],string,strlen(string));

	memset(read_buffer,0,sizeof(read_buffer));
	printf("attempting to read pipe_desc[0] which is %d\n",pipe_desc[0]);
	read(pipe_desc[0], read_buffer, sizeof(read_buffer));

	printf("read %s from pipe_desc[0]\n",read_buffer);

	close(pipe_desc[0]);
	close(pipe_desc[1]);

	return(0);

}

	


