#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <fcntl.h>

/*
 * simple program creating a pipe between two processes
 */
int main(int argc, char **argv)
{
	pid_t child_id;
	pid_t my_id;
	int pipe_desc[2];
	char *string;
	char read_buffer[4096];
	int err;

	/* sentinel == ignored */
	pid_t pid;
        pid = getpid();


	/*
	 * create the pipe
	 */
	err = pipe(pipe_desc);
	if(err < 0) {
		printf("error creating pipe\n");
		exit(1);
	}

	/*
	 * then fork
	 */
	child_id = fork();
	if(child_id != 0) {
		/*
		 * parent will be the writer
		 * doesn't need the read end
		 */
		my_id = getpid();
		close(pipe_desc[0]);
		/*
		 * send the child a string
		 */
		string = "a string made by the parent\n";
		printf("pid: %d -- writing %s to pipe_desc[1]\n",
			(int)my_id,
			string);
		write(pipe_desc[1],string,strlen(string));
		/*
		 * and another string
		 */
		string = "and another string";
		printf("pid: %d -- writing %s to pipe_desc[1]\n",
			(int)my_id,
			string);
		write(pipe_desc[1],string,strlen(string));
		/*
		 * close the pipe to let the read end know we are
		 * done
		 */
		close(pipe_desc[1]);
	} else {
		/*
		 * child will read until pipe closes
		 * close the write end
		 */
		my_id = getpid();
		close(pipe_desc[1]);
		memset(read_buffer,0,sizeof(read_buffer));
		while(read(pipe_desc[0],read_buffer,sizeof(read_buffer))) {
			printf("pid: %d -- received %s from parent\n",
				(int)my_id,
				read_buffer);
			memset(read_buffer,0,sizeof(read_buffer));
		}
		printf("pid: %d -- child detects write end closed\n",
			(int)my_id);
		close(pipe_desc[0]);
	}

	printf("pid: %d -- I am exiting\n",my_id);
	exit(0);

}


