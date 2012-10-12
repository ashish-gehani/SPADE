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
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <linux/socket.h>
#include <sys/un.h>

#include <spade_reporter_Audit.h>

#define SERVER_PATH     "/dev/audit"
#define BUFFER_LENGTH   10000
#define FALSE           0

#ifdef DEBUG
#include <android/log.h>
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG , "spade-audit", __VA_ARGS__)
#else
#define LOGD(...)
#endif

// Defining SUN_LEN. Does not exist in Bionic LIBC
#include <string.h>
#define SUN_LEN(ptr) ((size_t) (((struct sockaddr_un *) 0)->sun_path)	\
	+ strlen ((ptr)->sun_path))


int sd = -1, rc;
char buffer[BUFFER_LENGTH];
struct sockaddr_un serveraddr;


/*
 * Class:     spade_reporter_Audit
 * Method:    initAuditStream
 * Signature: ()I
 * Initiates the AF_UNIX socket connection to audit 
 */
JNIEXPORT jint JNICALL Java_spade_reporter_Audit_initAuditStream (JNIEnv *env, jclass j) {
  do {
    sd = socket(AF_UNIX, SOCK_STREAM, 0);

    if (sd < 0) {
      break;
    }

    memset(&serveraddr, 0, sizeof (serveraddr));
    serveraddr.sun_family = AF_UNIX;
    strcpy(serveraddr.sun_path, SERVER_PATH);

    rc = connect(sd, (struct sockaddr *) &serveraddr, SUN_LEN(&serveraddr));
    if (rc < 0) 
      break;
    return 0; // Success
  } 
  while(FALSE);

  if (sd != -1)
    close(sd);

  if (rc == 0)
    return 1;
  return -1;
}

/*
 * Class:     spade_reporter_Audit
 * Method:    readAuditStream
 * Signature: ()Ljava/lang/String;
 * Makes a blocking call to read audit stream from socket
 */
JNIEXPORT jstring JNICALL Java_spade_reporter_Audit_readAuditStream (JNIEnv * env, jclassJNIEnv *, jclass j_class) {
  // assert( sd != -1, "socket connection closed already");
  rc = recv(sd, buffer, BUFFER_LENGTH - 1, 0);
  if (rc < 0) {
    Java_spade_reporter_Audit_closeAuditStream(env, j_class);
    return NULL;
  }
  else if (rc == 0) {
    Java_spade_reporter_Audit_closeAuditStream(env, j_class);
    return NULL;
  }
  buffer[rc] = '\0';
  return env->NewStringUTF(buffer);
}

/*
 * Class:     spade_reporter_Audit
 * Method:    closeAuditStream
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_spade_reporter_Audit_closeAuditStream (JNIEnv * env, jclassJNIEnv *, jclass j_class) {
  if (sd != -1) close(sd);
  return 0;
}


#ifdef MAIN
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
        memset(&buffer, 0, BUFFER_LENGTH);
        while (1) {
            bytesReceived = 0;

            rc = recv(sd, & buffer[bytesReceived], BUFFER_LENGTH - bytesReceived - 1, 0);

            if (rc <= 0) {
                //perror("recv() failed");
                break;
            }
	    buffer[rc] = '\0';
            /*****************************************************************/
            /* Increment the number of bytes that have been received so far  */
            /*****************************************************************/
            bytesReceived += rc;
            printf("%s", buffer);
	    
	    #ifdef DEBUG
	    LOGD(buffer);
	    #endif

        }
    } while (FALSE);

    if (sd != -1) close(sd);
    return 0;

}
#endif
