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
#include <stdlib.h>
#include <arpa/inet.h>
#include <bsm/audit.h>
#include <bsm/libbsm.h>
#include <bsm/audit_kevents.h>
#include <sys/ioctl.h>
#include <security/audit/audit_ioctl.h>

int main(int argc, char** argv){

	// User configurable section starts here

	FILE* output = stdout;
	FILE* errors = stderr;
	char* delimiter = ",";
	int raw = 1; // Convert raw to string
	int shortForm = 0; // Use long form
	char* auditPipe = "/dev/auditpipe";
	u_int dataFlow = 0x00000001 | 0x00000002 | 0x00000010 | 0x00000020 | 0x00000080 | 0x40000000;
								// Class defined in /etc/security/audit_class
								// Events in class defined in /etc/security/audit_event
	
	// User configurable section ends here

	// Open the audit trail

	FILE* auditFile;
	int auditFileDescriptor;

	auditFile = fopen(auditPipe, "r");
	if(auditFile == NULL){
		fprintf(stderr, "Unable to open audit pipe: %s\n", auditPipe);
		perror("Error ");
		exit(1);
	}
	auditFileDescriptor = fileno(auditFile);

	// Configure the audit pipe
	
	int ioctlReturn;

	int mode = AUDITPIPE_PRESELECT_MODE_LOCAL;
	ioctlReturn = ioctl(auditFileDescriptor, AUDITPIPE_SET_PRESELECT_MODE, &mode);
	if(ioctlReturn == -1){
		fprintf(stderr, "Unable to set the audit pipe mode to local.\n");
		perror("Error ");
	}

	int queueLength;
	ioctlReturn = ioctl(auditFileDescriptor, AUDITPIPE_GET_QLIMIT_MAX, &queueLength);
	if(ioctlReturn == -1){
		fprintf(stderr, "Unable to get the maximum queue length of the audit pipe.\n");
		perror("Error ");
	}

	ioctlReturn = ioctl(auditFileDescriptor, AUDITPIPE_SET_QLIMIT, &queueLength);
	if(ioctlReturn == -1){
		fprintf(stderr, "Unable to set the queue length of the audit pipe.\n");
		perror("Error ");
	}
	
	u_int attributableEventsMask = dataFlow;
	ioctlReturn = ioctl(auditFileDescriptor, AUDITPIPE_SET_PRESELECT_FLAGS, &attributableEventsMask);
	if(ioctlReturn == -1){
		fprintf(stderr, "Unable to set the attributable events preselection mask.\n");
		perror("Error ");
	}
	
	u_int nonAttributableEventsMask = dataFlow;
	ioctlReturn = ioctl(auditFileDescriptor, AUDITPIPE_SET_PRESELECT_NAFLAGS, &nonAttributableEventsMask);
	if(ioctlReturn == -1){
		fprintf(stderr, "Unable to set the non-attributable events preselection mask.\n");
		perror("Error ");
	}
	
	// fprintf(output, "Provenance collection has started.\n");
	// Start processing audit records

	u_char* buffer;
	int remainingRecords = 1;
	int recordLength;
	int recordBalance;
	int processedLength;
	int tokenCount;
	int fetchToken;
	tokenstr_t token;
	
	while(remainingRecords){

		// Read an audit record
		// Note: au_read_rec() man page incorrectly states return
		//	value is 0 on success (rather than number of bytes
		//	read in record)
		recordLength = au_read_rec(auditFile, &buffer);
		if(recordLength == -1){
			remainingRecords = 0;
			break;
		}

		recordBalance = recordLength;
		processedLength = 0;
		tokenCount = 0;
		
		while(recordBalance){

			// Extract a token from the record
			fetchToken = au_fetch_tok(&token, buffer + processedLength, recordBalance);

			if(fetchToken == -1){
				// fprintf(errors, "Error fetching token.\n");
				break;
			}

			// Print the long form of the token as a string
			au_print_tok(output, &token, delimiter, raw, shortForm);		
			fprintf(output, "\n");

			tokenCount++;			
			processedLength += token.len;
			recordBalance -= token.len;
		}
		
		free(buffer);
		// fprintf(output, "\n");
	}

	fclose(auditFile);
	fprintf(output, "Provenance collection has ended.\n");
}
