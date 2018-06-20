#include  <stdio.h>
#include  <string.h>
#include  <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <stdlib.h>
#include <unistd.h>
#include <arpa/inet.h>


int portnum = 7000;

int run_server() 
{

  int welcomeSocket, newSocket;
  char buffer[1024];
  struct sockaddr_in serverAddr, clientAddr;
  socklen_t addr_size;
  int recv_len;
  struct iovec iov[1];
  struct msghdr message;

  /* sentinel == ignored */
  pid_t pid;
  pid = getpid();


  addr_size = sizeof clientAddr;

  welcomeSocket = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
  serverAddr.sin_family = AF_INET;
  serverAddr.sin_port = htons(portnum);
  serverAddr.sin_addr.s_addr = inet_addr("127.0.0.1");
  /* Set all bits of the padding field to 0 */
  memset(serverAddr.sin_zero, '\0', sizeof serverAddr.sin_zero);  

  /*---- Bind the address struct to the socket ----*/
  bind(welcomeSocket, (struct sockaddr *) &serverAddr, sizeof(serverAddr));

  //  if ((recv_len = recvmsg(welcomeSocket, buffer, sizeof(buffer), 0, (struct sockaddr *) &clientAddr, &addr_size)) == -1)


  iov[0].iov_base=buffer;
  iov[0].iov_len=sizeof(buffer);

  message.msg_name=&clientAddr;
  message.msg_namelen=sizeof(clientAddr);
  message.msg_iov=iov;
  message.msg_iovlen=1;
  message.msg_control=0;
  message.msg_controllen=0;

  if (recv_len = recvmsg(welcomeSocket, &message, 0) == -1)
    {
      perror("recvfrom()");
      exit(-1);
    }
         
  //print details of the client/peer and the data received
  printf("Received packet from %s:%d\n", inet_ntoa(clientAddr.sin_addr), ntohs(clientAddr.sin_port));
  printf("Data: %s\n" , buffer);
         
}





int run_client() 
{

  int clientSocket;
  char buffer[6] = "hello";
  struct sockaddr_in serverAddr;
  socklen_t addr_size;
  socklen_t slen = sizeof serverAddr;
  struct iovec iov[1];
  struct msghdr message;

  
  // to make sure the server is up
  sleep(2);

  clientSocket = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
  serverAddr.sin_family = AF_INET;
  serverAddr.sin_port = htons(portnum);
  serverAddr.sin_addr.s_addr = inet_addr("127.0.0.1");
  memset(serverAddr.sin_zero, '\0', sizeof serverAddr.sin_zero);  

  /*---- Connect the socket to the server using the address struct ----*/
  addr_size = sizeof serverAddr;

  iov[0].iov_base=buffer;
  iov[0].iov_len=sizeof(buffer);

  message.msg_name=&serverAddr;
  message.msg_namelen=sizeof(serverAddr);
  message.msg_iov=iov;
  message.msg_iovlen=1;
  message.msg_control=0;
  message.msg_controllen=0;



  /*---- Read the message from the server into the buffer ----*/
  //  sendto(clientSocket, buffer, sizeof(buffer), 0, (struct sockaddr*) &serverAddr, slen);
  sendmsg(clientSocket, &message, 0);


  /*---- Print the received message ----*/
  //  printf("Client: Data received: %s",buffer);   

  return 0;
}




int main(int argc, char** argv)
{
     pid_t  pid;
     int    i;
     pid_t parent_pid = getpid();
     srand(parent_pid);
     portnum = portnum + rand() % 200;

     fork();
     pid = getpid();
    
     if (pid == parent_pid) {
        printf("running server\n");
	run_server();
     }
     else {
        sleep(2);
        printf("running client\n");  
	run_client();
     }
}
