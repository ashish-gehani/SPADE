#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <fcntl.h>

/*
 * write to stdout and stderr
 */
int main(int argc, char **argv)
{
	char *string;
	char *string_err;
	pid_t pid;

        pid = getpid();

	string = "a string written to standard out\n";
	write(1,string,strlen(string));

	string_err = "a string written to standard error\n";
	write(2,string_err,strlen(string_err));
	
	return(0);

}


