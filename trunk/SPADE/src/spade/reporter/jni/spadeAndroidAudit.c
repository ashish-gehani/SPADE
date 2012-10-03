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
#include <assert.h>

#include <spade_reporter_Audit.h>
#include <spade_reporter_Audit_SYSCALL.h>

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


int sd = -1, end=0, start=0;
char buffer[BUFFER_LENGTH];
struct sockaddr_un serveraddr;

/*
 * Class:     spade_reporter_Audit
 * Method:    initAuditStream
 * Signature: ()I
 * Initiates the AF_UNIX socket connection to audit 
 */
JNIEXPORT jint JNICALL Java_spade_reporter_Audit_initAuditStream (JNIEnv *env, jclass j) {
  int rc;
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
JNIEXPORT jstring JNICALL Java_spade_reporter_Audit_readAuditStream (JNIEnv * env, jclass j_class) {
  int rc;
  assert( sd != -1 ); // Socket already closed ?

  if (start < end)
    {
      int i = start;
      jstring ret;

      // Return next line from read buffer
      while(i < end && buffer[i] != '\n' && buffer[i] != '\0') i++;
      buffer[i] = '\0';
      ret = (*env)->NewStringUTF(env,&buffer[start]);
      start = i + 1;
      return ret;
    }
  else 
    {
      // buffer consumed, reset and receive from socket
      start = end = 0;
    }

  rc = recv(sd, buffer + end, BUFFER_LENGTH - end - 1, 0);
  
  if (rc < 0) {
    Java_spade_reporter_Audit_closeAuditStream(env, j_class);
    // Server closed the connection
    LOGD("Error while receiving audit stream. Closing connection");
    return NULL;
  }
  else if (rc == 0) {
    Java_spade_reporter_Audit_closeAuditStream(env, j_class);
    LOGD("Server closed the connection");
    return NULL;
  }
  end += rc;
  buffer[end] = '\0';
  LOGD(buffer);

  return Java_spade_reporter_Audit_readAuditStream(env, j_class);

}

/*
 * Class:     spade_reporter_Audit
 * Method:    closeAuditStream
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_spade_reporter_Audit_closeAuditStream (JNIEnv * env, jclass j_class) {
  if (sd != -1) close(sd);
  return 0;
}


