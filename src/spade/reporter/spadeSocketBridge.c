/*
--------------------------------------------------------------------------------
SPADE - Support for Provenance Auditing in Distributed Environments.
Copyright (C) 2015 SRI International

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

CODE DERIVED FROM THE FOLLOWING:
http://www-01.ibm.com/support/knowledgecenter/ssw_i5_54/rzab6/xconoclient.htm
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

/*
  Connecting to the audispd socket to receive audit records and printing them 
  to STDOUT so that Audit.java (SPADE reporter) can read them.
*/

int main(int argc, char *argv[]) {
    int sd = -1, rc, bytesReceived;
    char buffer[BUFFER_LENGTH];
    struct sockaddr_un serveraddr;

    do {
        sd = socket(AF_UNIX, SOCK_STREAM, 0);
        if (sd < 0) {
            //perror("socket() failed");
            break;
        }

        memset(&serveraddr, 0, sizeof (serveraddr));
        serveraddr.sun_family = AF_UNIX;
        strcpy(serveraddr.sun_path, SERVER_PATH);

        rc = connect(sd, (struct sockaddr *) &serveraddr, SUN_LEN(&serveraddr));
        if (rc < 0) {
            //perror("connect() failed");
            break;
        }

        while (1) {
            memset(&buffer, 0, BUFFER_LENGTH);
            rc = recv(sd, & buffer[0], BUFFER_LENGTH - 1, 0);
            if (rc < 0) {
                //perror("recv() failed");
                break;
            } else if (rc == 0) {
                //printf("The server closed the connection\n");
                break;
            }

           printf("%s", buffer);
        }
    } while (FALSE);

    if (sd != -1) close(sd);
    return 0;

}
