#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <fcntl.h>

/*
 * simple program calling fork
 */
int main(int argc, char **argv)
{
	pid_t child_id;
	pid_t my_id;

	/* sentinel == ignored */
	pid_t pid;
        pid = getpid();


	my_id = getpid();
	printf("pid: %d -- I am the parent about to call fork\n",
			(int)my_id);

	child_id = fork();
	if(child_id != 0) {
		my_id = getpid();
		printf("pid: %d -- I just forked a child with id %d\n",
			(int)my_id,
			(int)child_id);
	} else {
		my_id = getpid();
		printf("pid: %d -- I am the child\n",my_id);
	}

	printf("pid: %d -- I am exiting\n",my_id);
	exit(0);

}


