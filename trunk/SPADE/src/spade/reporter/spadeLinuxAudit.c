/*
--------------------------------------------------------------------------------
SPADE - Support for Provenance Auditing in Distributed Environments.
Copyright (C) 2011 SRI International

This program is free software: you can redistribute it and/or
modify it under the terms of the GNU General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
--------------------------------------------------------------------------------
*/

#include <stdio.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>

#define SERVER_PATH     "/var/run/audispd_events"
#define BUFFER_LENGTH   10000
#define FALSE           0

int main(int argc, char *argv[]) {
    int sd = -1, rc, bytesReceived;
    char buffer[BUFFER_LENGTH];
    struct sockaddr_un serveraddr;

    /***********************************************************************/
    /* A do/while(FALSE) loop is used to make error cleanup easier.  The   */
    /* close() of the socket descriptor is only done once at the very end  */
    /* of the program.                                                     */
    /***********************************************************************/
    do {
        /***********************************************************************/
        /* The socket() function returns a socket descriptor, which represents */
        /* an endpoint.  The statement also identifies that the UNIX           */
        /* address family with the stream transport (SOCK_STREAM) will be      */
        /* used for this socket.                                               */
        /***********************************************************************/
        sd = socket(AF_UNIX, SOCK_STREAM, 0);
        if (sd < 0) {
            //perror("socket() failed");
            break;
        }

        /********************************************************************/
        /* If an argument was passed in, use this as the server, otherwise  */
        /* use the #define that is located at the top of this program.      */
        /********************************************************************/
        memset(&serveraddr, 0, sizeof (serveraddr));
        serveraddr.sun_family = AF_UNIX;
        strcpy(serveraddr.sun_path, SERVER_PATH);

        /********************************************************************/
        /* Use the connect() function to establish a connection to the      */
        /* server.                                                          */
        /********************************************************************/
        rc = connect(sd, (struct sockaddr *) &serveraddr, SUN_LEN(&serveraddr));
        if (rc < 0) {
            //perror("connect() failed");
            break;
        }

        /********************************************************************/
        /* In this example we know that the server is going to respond with */
        /* the same 250 bytes that we just sent.  Since we know that 250    */
        /* bytes are going to be sent back to us, we can use the            */
        /* SO_RCVLOWAT socket option and then issue a single recv() and     */
        /* retrieve all of the data.                                        */
        /*                                                                  */
        /* The use of SO_RCVLOWAT is already illustrated in the server      */
        /* side of this example, so we will do something different here.    */
        /* The 250 bytes of the data may arrive in separate packets,        */
        /* therefore we will issue recv() over and over again until all     */
        /* 250 bytes have arrived.                                          */
        /********************************************************************/
        while (1) {
            bytesReceived = 0;
            memset(&buffer, 0, BUFFER_LENGTH);
            rc = recv(sd, & buffer[bytesReceived], BUFFER_LENGTH - bytesReceived - 1, 0);
            if (rc < 0) {
                //perror("recv() failed");
                break;
            } else if (rc == 0) {
                //printf("The server closed the connection\n");
                break;
            }

            /*****************************************************************/
            /* Increment the number of bytes that have been received so far  */
            /*****************************************************************/
            bytesReceived += rc;
            printf("%s\n", buffer);
        }
    } while (FALSE);

    if (sd != -1) close(sd);
    return 0;

}
