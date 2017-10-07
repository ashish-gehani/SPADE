#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <fcntl.h>

/*
 * open and read the contents of a file, printing them out as ascii
 * characters
 */
int main(int argc, char **argv)
{
	int my_file_desc;
	char file_name[4096];
	char read_buffer[4096];
	int i;
	int r;
        pid_t pid;

        pid = getpid();

	/*
	 * zero out the buffer for the file name
	 */
	for(i=0; i < sizeof(file_name); i++) {
		file_name[i] = 0;
	}

	/*
	 * copy the argument into a local buffer
	 */
	strncpy(file_name,"/etc/timezone",sizeof(file_name));
	file_name[sizeof(file_name)-1] = 0;

	/*
	 * try and open the file for reading
	 */
	my_file_desc = open(file_name,O_RDONLY,0);
	if(my_file_desc < 0) {
		printf("failed to open %s for reading\n",file_name);
		exit(1);
	}

	for(i=0; i < sizeof(read_buffer); i++) {
		read_buffer[i] = 0;
	}

	r = read(my_file_desc,read_buffer,sizeof(read_buffer)-1);


	printf("file: %s contains the string: %s\n",
		file_name,
		read_buffer);

	close(my_file_desc);


	return(0);

}


