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

Code derived from:
http://www-01.ibm.com/support/knowledgecenter/ssw_i5_54/rzab6/xconoclient.htm
--------------------------------------------------------------------------------
 */

#include <stdio.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <errno.h>

#define SERVER_PATH     "/var/run/audispd_events"
#define BUFFER_LENGTH   10000
#define FALSE           0
#define TRUE		1

/*
  Java does not support reading from Unix domain sockets.

  This utility reads audit records from the audispd socket and writes them
  to the standard output stream.

  The Audit Reporter can invoke this utility and read from its standard
  output to obtain a stream of audit records.
*/

int main(int argc, char *argv[]) {
    char *programName = argv[0];
    int audispdSocketDescriptor = -1, charactersRead, bytesReceived;
    char buffer[BUFFER_LENGTH];
    struct sockaddr_un serverAddress;

    do {
        audispdSocketDescriptor = socket(AF_UNIX, SOCK_STREAM, 0);
        if (audispdSocketDescriptor < 0) {
            fprintf(stderr, "%s: Unable to construct a socket. Error: %s\n", programName, strerror(errno));
            break;
        }

        memset(&serverAddress, 0, sizeof (serverAddress));
        serverAddress.sun_family = AF_UNIX;
        strcpy(serverAddress.sun_path, SERVER_PATH);

        charactersRead = connect(audispdSocketDescriptor, (struct sockaddr *) &serverAddress, SUN_LEN(&serverAddress));
        if (charactersRead < 0) {
            fprintf(stderr, "%s: Unable to connect to the socket. Error: %s\n", programName, strerror(errno));
            break;
        }

        while (TRUE) {
            memset(&buffer, 0, BUFFER_LENGTH);
            charactersRead = recv(audispdSocketDescriptor, & buffer[0], BUFFER_LENGTH - 1, 0);
            if (charactersRead < 0) {
                fprintf(stderr, "%s: Error while reading from the socket. Error: %s\n", programName, strerror(errno));
                break;
            } else if (charactersRead == 0) {
		fprintf(stderr, "%s: Server closed the connection. Errror: %s\n", programName, strerror(errno));
                break;
            }

           printf("%s", buffer);
        }
    } while (FALSE);

    if (audispdSocketDescriptor != -1) close(audispdSocketDescriptor);
    return 0;
}
