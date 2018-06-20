#include<pwd.h> 
#include<unistd.h>
#include<sys/types.h>
#include<string.h>
#include<stdio.h>

void whois(const char* prefix , struct passwd* pwd) {
  printf ("\t %s %s (%d)\n", prefix, pwd->pw_name, pwd->pw_uid);
  return;
} 

void printInfo() {
  whois("real:      " , getpwuid(getuid()));
  whois("effective: " , getpwuid(geteuid()));
  printf("\n");
  return;
} 


int main(int argc , char** argv) {

  /* sentinel == ignored */
  pid_t pid;
  pid = getpid();

  const uid_t realUid = getuid();
  const uid_t savedUid = geteuid();

  printInfo();
     /*
    NOTE: if the effective uid is root, this call to
    setuid() here will change the effective uid for
    the rest of the program. This is why one must
    use setreuid() if you want a setuid root
    program to flip back and forth between root and
    the real UID.
     */
  printf("[call to setuid %d]\n", realUid);
  if (0 == savedUid) {
    printf("saved UID is root: there’s no going back from here!\n");
  }
  setuid(realUid);
  printInfo();
  printf("[reset to original state using setreuid(%d, %d)]\n", realUid, savedUid);
  setreuid(realUid , savedUid);
  printInfo();
  printf("call to seteuid( %d )]\n", realUid);
  seteuid(realUid);
  printInfo();
  printf("[reset to original state using setreuid(%d, %d)]\n", realUid, savedUid);
  setreuid(realUid , savedUid);
  printInfo();
  printf("[swapping real/effective uids using setreuid()]\n");
  setreuid(savedUid , realUid);
  printInfo();
  printf("[reset to original state using setreuid(%d, %d)\n", 
	 realUid, savedUid);

  setreuid(realUid , savedUid);
  printInfo();
  printf("[changing all ids using setresuid(%d, %d, %d)\n", realUid, realUid, realUid);
  setresuid(realUid , realUid , realUid);
  printInfo();
  printf("[reset to original state using setreuid(%d, %d)]\n", 
	 realUid, savedUid);
  printf("this will fail: we just overwrote the saved set-user ID with setresuid()\n");
  setreuid(realUid , savedUid);
  printInfo();
  return(0);
} 
