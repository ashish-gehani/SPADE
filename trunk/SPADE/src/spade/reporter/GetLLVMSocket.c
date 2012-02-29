#include <sys/socket.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <netdb.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>

int LLVMSocket = 5000;
int sock = -1;
FILE* socket_fp = NULL;

FILE* GetLLVMSocket()
{
	if(sock == -1)
	{
		struct hostent *host;
		struct sockaddr_in server_addr;  

		host = gethostbyname("127.0.0.1");

		if ((sock = socket(AF_INET, SOCK_STREAM, 0)) == -1) {
			perror("Unable to create client socket from LLVM instrumented program to SPADE");
			exit(1);
		}

		server_addr.sin_family = AF_INET;     
		server_addr.sin_port = htons(LLVMSocket);   
		server_addr.sin_addr = *((struct in_addr *)host->h_addr);
		bzero(&(server_addr.sin_zero),8); 

		if (connect(sock, (struct sockaddr *)&server_addr,
					sizeof(struct sockaddr)) == -1) 
		{
			perror("Unable to connect from LLVM instrumented program to SPADE");
			exit(1);
		}
		socket_fp = fdopen(sock, "r+");
	}
	return socket_fp;
}
