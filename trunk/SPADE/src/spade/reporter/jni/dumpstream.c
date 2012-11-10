/*
--------------------------------------------------------------------------------
SPADE - Support for Provenance Auditing in Distributed Environments.
Copyright (C) 2012 SRI International

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
#include <errno.h>

#define SERVER_PATH     "/dev/audit"
#define BUFFER_LENGTH   10000
#define FALSE           0

#ifdef DEBUG
#include <android.h>
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG , "dumpstream", __VA_ARGS__)
// #define LOGD(...) printf(__VA_ARGS__)
#else
#define LOGD(...)
#endif

#ifndef SUN_LEN
#define SUN_LEN(ptr) ((size_t) (((struct sockaddr_un *) 0)->sun_path)	\
	+ strlen ((ptr)->sun_path))
#endif

int sd = -1;

char buffer[BUFFER_LENGTH];
struct sockaddr_un serveraddr;

void print_help(char* selfname)
{
  printf("Output the audit stream in file or stdout \n\n");
  printf("Usage: %s [-f filename]\n", selfname);
  printf("If no filename is provided, audit data is written in stdout \n");
}

int main(char c, char** v) 
{
  int rc, i;
  FILE* fout = stdout;

  for(i = 0; i < c ; i++) 
    {
      if ( strcmp("-h", v[i]) == 0 || strcmp("--help", v[i]) == 0 )
	{
	  print_help(v[0]);
	  return 0;
	}
      if( strcmp("-f", v[i]) == 0 ) 
	{
	  fout = fopen(v[i+1], "a+");
	  if (fout == NULL)
	    {
	      fprintf(stderr, "Could not create file: %s ; Error Code %d", v[i+1], errno);
	      return 1;
	    }
	  break;
	}
    }

  // connect to socket
  sd = socket(AF_UNIX, SOCK_STREAM, 0);

 if ( sd < 0 )
    {
      printf("Couldn't open socket");
      return 1;
    }
  
  memset(&serveraddr, 0, sizeof (serveraddr));
  serveraddr.sun_family = AF_UNIX;
  strcpy(serveraddr.sun_path, SERVER_PATH);

  rc = connect(sd, (struct sockaddr *) &serveraddr, SUN_LEN(&serveraddr)); 
  if (rc < 0) 
    {
      printf("Couldn't connect to socket ");
      return 1;
    }

  // Start read and output on output stream
  for(;;)
    {
      rc = recv(sd, buffer, BUFFER_LENGTH, 0);
      if (rc < 0) 
	{
	  fprintf(stderr, "Connection error: %d", rc);
	  return 1;
	}
      fwrite(buffer, 1, rc, fout);
    }
  return 0;
}
