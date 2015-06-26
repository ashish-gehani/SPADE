
#include<stdlib.h>
#include<stdio.h>
#include<string.h>


int maxBytes = 16384;
char provenanceStrings[17408];
char * strPtr = provenanceStrings;
int n = 0;

extern FILE * LLVMReporter_getSocket();

void bufferString(char * formatString, ...);
void flushStrings();


void setAtExit(){
    atexit(flushStrings);
}


void bufferString(char * formatString, ...){
 
    va_list argptr;
    va_start(argptr, formatString);
	
    vsprintf(strPtr, formatString, argptr); 			
    strPtr = provenanceStrings + strlen(provenanceStrings);
    	
    if(strlen(provenanceStrings) > maxBytes){
				
  	FILE * socketDescriptor = LLVMReporter_getSocket();
	fwrite(provenanceStrings, strlen(provenanceStrings), 1, socketDescriptor);
	strPtr = provenanceStrings; 		
    }	
}


void flushStrings(){
	
	FILE * socketDescriptor = LLVMReporter_getSocket();
	fwrite(provenanceStrings, strlen(provenanceStrings), 1, socketDescriptor);
}



