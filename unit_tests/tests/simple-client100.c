/**************************************************************************
 *   This is a simple client socket reader.  It opens a socket, connects
 *   to a server, reads the message, and closes.
 **************************************************************************/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <resolv.h>
#include <arpa/inet.h>

#define PORT_SSH        22              /* SSH connection port */
#define SERVER_ADDR     "127.0.0.1"     /* localhost */
#define MAXBUF          1024

int main()
{   int sockfd;
  struct sockaddr_in dest;
  char buffer[MAXBUF];
  pid_t pid;
  int i=0;

  pid = getpid();

  for (i=0; i < 100; i++) {

    /*---Open socket for streaming---*/
    if ( (sockfd = socket(AF_INET, SOCK_STREAM, 0)) < 0 )
      {
	perror("Socket");
	exit(errno);
      }
    
    /*---Initialize server address/port struct---*/
    bzero(&dest, sizeof(dest));
    dest.sin_family = AF_INET;
    dest.sin_port = htons(PORT_SSH);
    if ( inet_aton(SERVER_ADDR, (struct in_addr*) &dest.sin_addr.s_addr) == 0 )
      {
	perror(SERVER_ADDR);
	exit(errno);
      }
    


    /*---Connect to server---*/
    if ( connect(sockfd, (struct sockaddr*)&dest, sizeof(dest)) != 0 )
      {
	perror("Connect ");
	exit(errno);
      }
    
    /*---Get "Hello?"---*/
    bzero(buffer, MAXBUF);
    recv(sockfd, buffer, sizeof(buffer), 0);
    printf("%s", buffer);

    /*---Clean up---*/
    close(sockfd);
    sleep(1);
  }
  return 0;
}

