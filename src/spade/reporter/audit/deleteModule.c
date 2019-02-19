#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <errno.h>

#define MAX_LENGTH 1024

char* buffer = NULL;
char* moduleName = NULL;

int delete_module(const char*, int);

int main(int argc, char*argv[]){
	buffer = (char*)malloc(MAX_LENGTH*sizeof(char));
	bzero((void*)buffer, MAX_LENGTH);
	char* moduleName = NULL;
	if(argc == 1){ // no arg. read from stdin.
		int c;
		int i = 0;
		while((c = fgetc(stdin)) != '\n'){
			if(c == EOF){
				break;
			}else{
				if(i >= MAX_LENGTH){
					fprintf(stderr, "Input length must be less than %d\n", MAX_LENGTH);
					return -1;
				}else{
					buffer[i++] = c;
				}
			}
		}
		moduleName = buffer;
	}else{
		buffer = argv[1];
		int bufferLength = strlen(buffer);
		
		if(bufferLength >= MAX_LENGTH){
			fprintf(stderr, "Argument length must be less than %d\n", MAX_LENGTH);
			return -1;
		}else{
			char* name = (char*)malloc(MAX_LENGTH*sizeof(char));
			bzero((void*)name, MAX_LENGTH);
			strncpy(name, buffer, bufferLength);
			moduleName = name;
		}
	}
	
	if(moduleName != NULL){
		errno = 0;
		int result = delete_module(moduleName, 0);
		//printf("Module name changed to '%s'\n", moduleName);
		if(result == -1){
			perror("SEVERE");
			return -1;
		}else{
			return 0;
		}
	}
}
