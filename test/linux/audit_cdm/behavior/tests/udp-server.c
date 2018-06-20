
#include <stdio.h>
#include <errno.h>
#include <sys/socket.h>
#include <resolv.h>
#include <strings.h>
#include <arpa/inet.h>

#define DEFAULT_PORT 9999


void run_server(short port){

  int sd;
  struct sockaddr_in addr;
  char buffer[1024];
  struct timeval timeout;


  sd = socket(PF_INET, SOCK_DGRAM, 0);
  bzero(&addr, sizeof(addr));
  addr.sin_family = AF_INET;
  addr.sin_port = htons(port);
  addr.sin_addr.s_addr = INADDR_ANY;

  timeout.tv_sec = 100;
  timeout.tv_usec = 0;

  if (setsockopt (sd, SOL_SOCKET, SO_RCVTIMEO, (char *)&timeout,
		  sizeof(timeout)) < 0)
    error("setsockopt failed\n");


  if ( bind(sd, (struct sockaddr*)&addr, sizeof(addr)) != 0 )
    perror("bind");

  //  while (1)
    {
      int bytes, addr_len=sizeof(addr);

      bytes = recvfrom(sd, buffer, sizeof(buffer), 0, (struct sockaddr*)&addr, &addr_len);
      printf("msg from %s:%d (%d bytes)%s\n", inet_ntoa(addr.sin_addr),
	     ntohs(addr.sin_port), bytes, buffer);
    }
  close(sd);

}


void run_client(short port) {

  int sd;
  struct sockaddr_in addr;
  char buffer[6] = "hello";

  sd = socket(PF_INET, SOCK_DGRAM, 0);
  bzero(&addr, sizeof(addr));
  addr.sin_family = AF_INET;
  addr.sin_port = htons(port);
  addr.sin_addr.s_addr = INADDR_ANY;



  sendto(sd, buffer, sizeof(buffer), 0, (struct sockaddr*)&addr, sizeof(addr));

}










int main(int argc, char** argv)
{
     pid_t  pid;
     int    i;
     pid_t parent_pid = getpid();
     srand(parent_pid);
     short portnum = DEFAULT_PORT + rand() % 200;
     

     fork();
     pid = getpid();
    
     if (pid == parent_pid) {
        printf("running server\n");
	run_server(portnum);
     }
     else {
	sleep(2);
        printf("running client\n");  
	run_client(portnum);
     }
}



