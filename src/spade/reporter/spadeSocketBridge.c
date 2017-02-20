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
#include <signal.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <getopt.h>

#define SERVER_PATH     "/var/run/audispd_events"
#define BUFFER_LENGTH   10000
#define FALSE           0
#define TRUE		1

int UBSIAnalysis = FALSE;
int UBSI_buffer(const char *buf);
void UBSI_sig_handler(int signo);
int UBSI_buffer_flush();
int waitForEnd = FALSE;
int socketRead = FALSE;
int fileRead = FALSE;
char socketPath[256];
char filePath[256];
int loopCount[100];

/*
			Java does not support reading from Unix domain sockets.

			This utility reads audit records from the audispd socket and writes them
			to the standard output stream.

			The Audit Reporter can invoke this utility and read from its standard
			output to obtain a stream of audit records.
	*/

void print_usage(char** argv) {
		printf("Usage: %s [OPTIONS]\n", argv[0]);
		printf("  -u, --unit																unit analysis\n");
		printf("  -s, --socket              socket name\n");
		printf("  -w, --wait-for-end        continue processing till the end of the log is reached\n");
		printf("  -f, --files               a filename that has a list of log files to process\n");  
		printf("  -h, --help                print this help and exit\n");
		printf("\n");

}

int command_line_option(int argc, char **argv)
{
		int c;

		struct option   long_opt[] =
		{
				{"help",          no_argument,       NULL, 'h'},
				{"unit",          no_argument,       NULL, 'u'},
				{"socket",        required_argument, NULL, 's'},
				{"files",  required_argument,							NULL, 'f'},
				{"wait-for-end",  no_argument,							NULL, 'w'},
				{NULL,            0,                 NULL, 0  }
		};

		while((c = getopt_long(argc, argv, "hus:f:w", long_opt, NULL)) != -1)
		{
				switch(c)
				{
						case 's':
								strncpy(socketPath, optarg, 256);
								socketRead = TRUE;
								break;
						case 'f':
								strncpy(filePath, optarg, 256);
								fileRead = TRUE;
								break;

						case 'w':
								waitForEnd = TRUE;
								break;

						case 'u':
								UBSIAnalysis = TRUE;
								break;

						case 'h':
								print_usage(argv);
								exit(0);

						default:
								fprintf(stderr, "Try `%s --help' for more information.\n", argv[0]);
								exit(-2);
				};
		};

}

void socket_read(char *programName)
{
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
				strcpy(serverAddress.sun_path, socketPath);

				charactersRead = connect(audispdSocketDescriptor, (struct sockaddr *) &serverAddress, SUN_LEN(&serverAddress));
				if (charactersRead < 0) {
						fprintf(stderr, "%s: Unable to connect to the socket: %s. Error: %s\n", programName, socketPath, strerror(errno));
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
						UBSI_buffer(buffer);
				}
		} while (FALSE);

		if (audispdSocketDescriptor != -1) close(audispdSocketDescriptor);
}

void stdin_read()
{
		char buffer[BUFFER_LENGTH];

		do{
				while (TRUE) {
						memset(&buffer, 0, BUFFER_LENGTH);
						if(fgets(& buffer[0], BUFFER_LENGTH, stdin) == NULL) {
								fprintf(stderr, "Reaches the end of file (stdin).\n");
								UBSI_buffer_flush();
								break;
						}
						UBSI_buffer(buffer);
				}
		} while (FALSE);
}

void file_read()
{
		FILE *fp = fopen(filePath, "r");
		FILE *log_fp;
		char tmp[1024];
		char buffer[BUFFER_LENGTH];

		if(fp == NULL) {
				fprintf(stderr, "file open error: %s\n", filePath);
				return;
		}

		while(!feof(fp)) {
				if(fgets(tmp, 1024, fp) == NULL) break;
				fprintf(stderr, "reading a log file: %s", tmp);
				if(tmp[strlen(tmp)-1] == '\n') tmp[strlen(tmp)-1] = '\0';
				
				log_fp = fopen(tmp, "r");
				if(log_fp == NULL) {
						fprintf(stderr, "file open error: %s", tmp);
						continue;
				}
				while (!feof(log_fp)) {
						memset(&buffer, 0, BUFFER_LENGTH);
						if(fgets(& buffer[0], BUFFER_LENGTH, log_fp) == NULL) {
								fprintf(stderr, "Reaches the end of file (%s).\n", tmp);
								//UBSI_buffer_flush();
								break;
						}
						UBSI_buffer(buffer);
				}
				fclose(log_fp);
		}

		UBSI_buffer_flush();
		fclose(fp);
		// read a file: filePath that contains a list of paths to log files, one-per-line

}

int main(int argc, char *argv[]) {
		char *programName = argv[0];
		int audispdSocketDescriptor = -1, charactersRead, bytesReceived;
		char buffer[BUFFER_LENGTH];
		struct sockaddr_un serverAddress;

		command_line_option(argc, argv);

		signal(SIGINT, UBSI_sig_handler);
		signal(SIGKILL, UBSI_sig_handler);
		signal(SIGTERM, UBSI_sig_handler);

		if(socketRead) socket_read(programName);
		else if(fileRead) file_read();
		else stdin_read();

		return 0;
}

// UBSI Unit analysis
#include <assert.h>
#include "uthash.h"
#define UENTRY 0xffffff9c
#define UEXIT 0xffffff9b
#define MREAD1 0xffffff38
#define MREAD2 0xffffff37
#define MWRITE1 0xfffffed4
#define MWRITE2 0xfffffed3

typedef int bool;
#define true 1
#define false 0

typedef struct thread_unit_t {
		int tid;
		int unitid; // unique identifier. different from loopid
		int loopid; // loopid. in the output, we call this unitid.
		int iteration;
		double timestamp; // loop start time. Not iteration start.
		int count; // if two or more loops starts at the same timestamp. We use count to distinguish them.
} thread_unit_t;

typedef struct link_unit_t { // list of dependent units
		thread_unit_t id;
		UT_hash_handle hh;
} link_unit_t;

typedef struct mem_proc_t {
		long int addr;
		thread_unit_t last_written_unit;
		UT_hash_handle hh;
} mem_proc_t;

typedef struct mem_unit_t {
		long int addr;
		//int isWritten;
		UT_hash_handle hh;
} mem_unit_t;

typedef struct unit_table_t {
		int tid; // pid in auditlog which is actually thread_id.
		int pid; // process id.  (main thread id)
		thread_unit_t cur_unit;
		bool valid; // is valid unit?
		long int r_addr;
		long int w_addr;
		link_unit_t *link_unit;
		mem_proc_t *mem_proc;
		mem_unit_t *mem_unit; // mem_write_record in the unit
		char proc[1024];
		UT_hash_handle hh;
} unit_table_t;

typedef struct event_buf_t {
		int id;
		int event_byte;
		char *event;
		UT_hash_handle hh;
} event_buf_t;

unit_table_t *unit_table;
event_buf_t *event_buf;

bool is_same_unit(thread_unit_t u1, thread_unit_t u2)
{
		if(u1.tid == u2.tid && 
				 u1.unitid == u2.unitid &&
					u1.loopid == u2.loopid &&
					u1.iteration == u2.iteration &&
					u1.timestamp == u2.timestamp &&
					u1.count == u2.count) return true;

		return false;
}

double get_timestamp(char *buf)
{
		char *ptr;
		double time;

		ptr = strstr(buf, "(");
		if(ptr == NULL) return 0;

		sscanf(ptr+1, "%lf", &time);

		return time;
}

int emit_log(unit_table_t *ut, char* buf, bool print_unit, bool print_proc)
{
		int rc;
		char buffer[BUFFER_LENGTH];

		if(!print_unit && !print_proc) {
				rc = printf("%s", buf);
				return rc;
		}

		buf[strlen(buf)-1] = '\0';
		
		rc = sprintf(buffer, "%s", buf);
		if(print_unit) {
				rc += sprintf(buffer + rc, " unit=(pid=%d unitid=%d iteration=%d time=%.3lf count=%d) "
							,ut->cur_unit.tid, ut->cur_unit.loopid, ut->cur_unit.iteration, ut->cur_unit.timestamp, ut->cur_unit.count);
		} 

		if(print_proc) {
				rc += sprintf(buffer + rc, "%s", ut->proc);
		}

		if(!print_proc) sprintf(buffer + rc, "\n");

		rc = printf("%s", buffer);

		return rc;
}

void delete_unit_hash(link_unit_t *hash_unit, mem_unit_t *hash_mem)
{
		//	HASH_CLEAR(hh, hash_unit);
		//	HASH_CLEAR(hh, hash_mem);

		link_unit_t *tmp_unit, *cur_unit;
		mem_unit_t *tmp_mem, *cur_mem;
		HASH_ITER(hh, hash_unit, cur_unit, tmp_unit) {
				if(hash_unit != cur_unit) 
						HASH_DEL(hash_unit, cur_unit); 
				if(cur_unit) free(cur_unit);  
		}
		//if(hash_unit) free(hash_unit);

		HASH_ITER(hh, hash_mem, cur_mem, tmp_mem) {
				if(hash_mem != cur_mem) 
						HASH_DEL(hash_mem, cur_mem); 
				if(cur_mem) free(cur_mem);  
		}
		//if(hash_mem) free(hash_mem);

}

void delete_proc_hash(mem_proc_t *mem_proc)
{
		//HASH_CLEAR(hh, mem_proc);

		mem_proc_t *tmp_mem, *cur_mem;
		HASH_ITER(hh, mem_proc, cur_mem, tmp_mem) {
				if(mem_proc != cur_mem) 
						HASH_DEL(mem_proc, cur_mem); 
				if(cur_mem) free(cur_mem);  
		}
}

void loop_entry(unit_table_t *unit, long a1, char* buf, double time)
{
		char *ptr;

		unit->cur_unit.loopid = a1;
		unit->cur_unit.iteration = 0;
		unit->cur_unit.timestamp = time;
		unit->cur_unit.count = loopCount[a1]++; 
		
		ptr = strstr(buf, " ppid=");
		if(ptr == NULL) {
				fprintf(stderr, "loop_entry error! cannot find proc info: %s", buf);
		} else {
				ptr++;
				strncpy(unit->proc, ptr, strlen(ptr));
				unit->proc[strlen(ptr)] = '\0';
		}
		//unit->proc = get this info;
}

void loop_exit(unit_table_t *unit)
{
		char tmp[10240];

		sprintf(tmp,  "type=UBSI_EXIT pid=%d ", unit->cur_unit.tid);
		emit_log(unit, tmp, false, true);
		unit->valid = false;
}

void unit_entry(unit_table_t *unit, long a1, char* buf)
{
		char tmp[10240];
		int tid = unit->tid;
		double time;

		time = get_timestamp(buf);
//		int unitid = ++(unit->cur_unit.unitid);
		if(unit->valid == false) // this is an entry of a new loop.
		{
				loop_entry(unit, a1, buf, time);
		} else {
				unit->cur_unit.iteration++;
		}
		unit->valid = true;
		unit->cur_unit.timestamp = time;
		
		sprintf(tmp, "type=UBSI_ENTRY ");
		emit_log(unit, tmp, true, true);
		//TODO: emit unit_entry event with 5 tuples: {pid, unitid, iteration, start_time, count}
}

void unit_end(unit_table_t *unit, long a1)
{
		struct link_unit_t *ut;
		char buf[10240];

		if(unit->valid == true || HASH_COUNT(unit->link_unit) > 1) {
				bzero(buf, 10240);
				// emit linked unit lists;
				if(unit->link_unit != NULL) {
						sprintf(buf, "type=UBSI_DEP list=\"");
						for(ut=unit->link_unit; ut != NULL; ut=ut->hh.next) {
								//sprintf(buf+strlen(buf), "%d-%d,", ut->id.tid, ut->id.unitid);
								sprintf(buf+strlen(buf), "(pid=%d unitid=%d iteration=%d time=%.3lf count=%d),"
								,ut->id.tid, ut->id.loopid, ut->id.iteration, ut->id.timestamp, ut->id.count);
						}
						sprintf(buf+strlen(buf), "\" ");
						emit_log(unit, buf, true, true);
				}
		}

		delete_unit_hash(unit->link_unit, unit->mem_unit);
		unit->link_unit = NULL;
		unit->mem_unit = NULL;
	//	unit->valid = false;
		unit->r_addr = 0;
		unit->w_addr = 0;
		//unit->unitid++;
}

void proc_end(unit_table_t *unit)
{
		unit_end(unit, -1);
		delete_proc_hash(unit->mem_proc);
		unit->mem_proc = NULL;
}

void proc_group_end(unit_table_t *unit)
{
		int pid = unit->pid;
		unit_table_t *pt;

		if(pid != unit->tid) {
				HASH_FIND_INT(unit_table, &pid, pt);
				proc_end(pt);
		}

		proc_end(unit);
}

void flush_all_unit()
{
		unit_table_t *tmp_unit, *cur_unit;
		HASH_ITER(hh, unit_table, cur_unit, tmp_unit) {
				unit_end(cur_unit, -1);
		}
}


bool is_important_syscall(int S, bool succ)
{
		if(S == 60 || S == 231 || S == 42)  return true;

		if(!succ) 
		{	
				return false;
		}

		switch(S) {
				case 0: case 19: case 1: case 20: case 44: case 45: case 46: case 47: case 86: case 88: 
				case 56: case 57: case 58: case 59: case 2: case 85: case 257: case 259: case 133: case 32: 
				case 33: case 292: case 49: case 43: case 288: case 42: case 82: case 105: case 113: case 90:
				case 22: case 293: case 76: case 77: case 40: case 87: case 263: case 62: case 9: case 10:
						return true;
		}
		return false;
}

void mem_write(unit_table_t *ut, long int addr)
{
		// check for dup_write
		mem_unit_t *umt;
		HASH_FIND(hh, ut->mem_unit, &addr, sizeof(long int), umt);

		if(umt != NULL) return;

		// not duplicated write
		umt = (mem_unit_t*) malloc(sizeof(mem_unit_t));
		umt->addr = addr;
		HASH_ADD(hh, ut->mem_unit, addr, sizeof(long int),  umt);

		// add it into process memory map
		int pid = ut->pid;
		unit_table_t *pt;
		if(pid == ut->tid) pt = ut;
		else {
				HASH_FIND_INT(unit_table, &pid, pt);
				if(pt == NULL) {
						assert(1);
				}
		}

		mem_proc_t *pmt;
		HASH_FIND(hh, pt->mem_proc, &addr, sizeof(long int), pmt);
		if(pmt == NULL) {
				pmt = (mem_proc_t*) malloc(sizeof(mem_proc_t));
				pmt->addr = addr;
				pmt->last_written_unit = ut->cur_unit;
				//pmt->last_written_unit.tid = ut->cur_unit.tid;
				//pmt->last_written_unit.unitid = ut->cur_unit.unitid;
				HASH_ADD(hh, pt->mem_proc, addr, sizeof(long int),  pmt);
		} else {
				pmt->last_written_unit = ut->cur_unit;
				//pmt->last_written_unit.tid = ut->tid;
				//pmt->last_written_unit.unitid = ut->unitid;
		}
}

void mem_read(unit_table_t *ut, long int addr, char *buf)
{
		int pid = ut->pid;
		unit_table_t *pt;
		if(pid == ut->tid) pt = ut;
		else {
				HASH_FIND_INT(unit_table, &pid, pt);
				if(pt == NULL) {
						assert(1);
				}
		}

		mem_proc_t *pmt;
		HASH_FIND(hh, pt->mem_proc, &addr, sizeof(long int), pmt);
		if(pmt == NULL) return;

		thread_unit_t lid;
		if(pmt->last_written_unit.timestamp != 0 && !is_same_unit(pmt->last_written_unit, ut->cur_unit))
		//if((pmt->last_written_unit.tid != ut->tid) || (pmt->last_written_unit.unitid != ut->unitid))
		{
				link_unit_t *lt;
				lid = pmt->last_written_unit;
				//lid.tid = pmt->last_written_unit.tid;
				//lid.unitid = pmt->last_writte_unit.unitid;
				HASH_FIND(hh, ut->link_unit, &lid, sizeof(thread_unit_t), lt);
				if(lt == NULL) {
						// emit the dependence. parse time and eid.
						lt = (link_unit_t*) malloc(sizeof(link_unit_t));
						lt->id = pmt->last_written_unit;
						HASH_ADD(hh, ut->link_unit, id, sizeof(thread_unit_t), lt);
				}
		}
}

unit_table_t* add_unit(int tid, int pid, int unitid, bool valid)
{
		struct unit_table_t *ut;
		ut = malloc(sizeof(struct unit_table_t));
		ut->tid = tid;
		ut->pid = pid;
		ut->valid = valid;

		// TODO: NEED TO FILL THIS OUT
		ut->cur_unit.tid = tid;
		ut->cur_unit.unitid = unitid;
		ut->cur_unit.loopid = 0;
		ut->cur_unit.iteration = 0;
		ut->cur_unit.timestamp = 0;
		ut->cur_unit.count = 0; 

		ut->link_unit = NULL;
		ut->mem_proc = NULL;
		ut->mem_unit = NULL;
		HASH_ADD_INT(unit_table, tid, ut);
		return ut;
}

void set_pid(int tid, int pid)
{
		struct unit_table_t *ut;
		int ppid;

		HASH_FIND_INT(unit_table, &pid, ut);  /* looking for parent thread's pid */
		if(ut == NULL) ppid = pid;
		else ppid = ut->pid;

		ut = NULL;

		HASH_FIND_INT(unit_table, &tid, ut);  /* id already in the hash? */
		if (ut == NULL) {
				ut = add_unit(tid, ppid, 0, 0); 
		} else {
				ut->pid = ppid;
		}

}

void UBSI_event(long tid, long a0, long a1, char *buf)
{
		int isNewUnit = 0;
		struct unit_table_t *ut;
		HASH_FIND_INT(unit_table, &tid, ut);

		if(ut == NULL) {
				isNewUnit = 1;
				ut = add_unit(tid, tid, 0, 0);
		}

		switch(a0) {
				case UENTRY: 
						if(ut->valid) unit_end(ut, a1);
						unit_entry(ut, a1, buf);
						break;
				case UEXIT: 
						if(isNewUnit == false)
						{
								unit_end(ut, a1);
								loop_exit(ut);
						}
						break;
				case MREAD1:
						ut->r_addr = a1;
						ut->r_addr = ut->r_addr << 32;
						break;
				case MREAD2:
						ut->r_addr += a1;
						mem_read(ut, ut->r_addr, buf);
						break;
				case MWRITE1:
						ut->w_addr = a1;
						ut->w_addr = ut->w_addr << 32;
						break;
				case MWRITE2:
						ut->w_addr += a1;
						mem_write(ut, ut->w_addr);
						break;
		}
}

void non_UBSI_event(long tid, int sysno, bool succ, char *buf)
{
		char *ptr;
		long a2;
		long ret;

		struct unit_table_t *ut;

		//if(!is_important_syscall(sysno, succ))  return;

		HASH_FIND_INT(unit_table, &tid, ut);

		if(ut == NULL) {
				ut = add_unit(tid, tid, 0, 0);
		}

		emit_log(ut, buf, false, false);

		if(succ == true && (sysno == 56 || sysno == 57 || sysno == 58)) // clone or fork
		{
				ptr = strstr(buf, " a2=");
				a2 = strtol(ptr+4, NULL, 16);


				if(a2 > 0) { // thread_creat event
						ptr = strstr(buf, " exit=");
						ret = strtol(ptr+6, NULL, 10);
						set_pid(ret, tid);
				}
		} else if(succ == true && ( sysno == 59 || sysno == 322 || sysno == 60 || sysno == 231)) { // execve, exit or exit_group
				if(sysno == 231) { // exit_group call
						// TODO: need to finish all thread in the process group
						proc_group_end(ut);
				} else {
						proc_end(ut);
				}
		}
}

bool get_succ(char *buf)
{
		char *ptr;
		char succ[16];
		int i=0;

		ptr = strstr(buf, " success=");
		if(ptr == NULL) {
				return false;
		}
		ptr+=9;

		for(i=0; ptr[i] != ' '; i++)
		{
				succ[i] = ptr[i];
		}
		succ[i] = '\0';
		if(strncmp(succ, "yes", 3) == 0) {
				return true;
		}
		return false;
}

void syscall_handler(char *buf)
{
		char *ptr;
		int sysno;
		long a0, a1, pid;
		bool succ = false;

		ptr = strstr(buf, " syscall=");
		if(ptr == NULL) {
				printf("ptr = NULL: %s\n", buf);
				return;
		}
		sysno = strtol(ptr+9, NULL, 10);
		
		ptr = strstr(ptr, " pid=");
		pid = strtol(ptr+5, NULL, 10);

		succ = get_succ(buf);

		if(sysno == 62)
		{
				ptr = strstr(buf, " a0=");
				a0 = strtol(ptr+4, NULL, 16);
				if(a0 == UENTRY || a0 == UEXIT || a0 == MREAD1 || a0 == MREAD2 || a0 == MWRITE1 || a0 ==MWRITE2)
				{
						ptr = strstr(ptr, " a1=");
						a1 = strtol(ptr+4, NULL, 16);
						UBSI_event(pid, a0, a1, buf);
						//UBSI_event(pid, a0, a1, buf);
						//printf("pid %d, a0 %x, a1 %x: %s\n", pid, a0, a1, buf);
				} else {
						non_UBSI_event(pid, sysno, succ, buf);
				}
		} else {
				non_UBSI_event(pid, sysno, succ, buf);
		}
}

#define EVENT_LENGTH 1048576
#define REORDERING_WINDOW 10000
int next_event_id = 0;

int UBSI_buffer_flush()
{
		struct event_buf_t *eb;
		fprintf(stderr, "UBSI flush the log buffer: %d events\n", HASH_COUNT(event_buf));

		while(HASH_COUNT(event_buf) > 0)
		{
				HASH_FIND_INT(event_buf, &next_event_id, eb);
				next_event_id++;
				if(eb != NULL) {
						if(strstr(eb->event, "type=SYSCALL") != NULL) {
								if(UBSIAnalysis) syscall_handler(eb->event);
								else printf("%s", eb->event);
						} else {
								printf("%s", eb->event);
						}
						HASH_DEL(event_buf, eb);
						free(eb->event);
						free(eb);
				} 
		}
}

int UBSI_buffer(const char *buf)
{
		int cursor = 0;
		int event_start = 0;
		long id = 0;
		char event[EVENT_LENGTH];
		int event_byte = 0;
		char *ptr;
		static char remain[BUFFER_LENGTH];
		static int remain_byte = 0;

		struct event_buf_t *eb;

		//		printf("\n\nsize %ld\n%s\n\n", strlen(buf), buf); 
		for(cursor=0; cursor < strlen(buf); cursor++) {
				if(buf[cursor] == '\n') {
						if(event_start == 0 && remain_byte > 0) {
								strncpy(event, remain, remain_byte-1);
								strncpy(event+remain_byte-1, buf, cursor+1);
								event[remain_byte + cursor] = '\0';
								event_byte = remain_byte + cursor;
								remain_byte = 0;
						} else {
								strncpy(event, buf+event_start, cursor-event_start+1);
								event[cursor-event_start+1] = '\0';
								event_byte = cursor-event_start+1;
						}
						if(strstr(event, "type=DAEMON_START") != NULL) {
								// flush events in reordering buffer.
								UBSI_buffer_flush();
						}

						if(strstr(event, "type=EOE") == NULL && strstr(event, "type=UNKNOWN") == NULL && strstr(event, "type=PROCTILE") == NULL) {
								ptr = strstr(event, ":");
								if(ptr == NULL) {
										id = 0;
										printf("ERROR: cannot parse event id.\n");
								} else {
										id = strtol(ptr+1, NULL, 10);
										if(next_event_id == 0) next_event_id = id;
								}
								HASH_FIND_INT(event_buf, &id, eb);
								if(eb == NULL) {
										eb = (event_buf_t*) malloc(sizeof(event_buf_t));
										eb->id = id;
										eb->event = (char*) malloc(sizeof(char) * EVENT_LENGTH);
										eb->event_byte = event_byte;
										strncpy(eb->event, event, event_byte+1);
										HASH_ADD_INT(event_buf, id, eb);
										if(next_event_id > id) {
												next_event_id = id;
										}
								} else {
										strncpy(eb->event+eb->event_byte, event, event_byte+1);
										eb->event_byte += event_byte;
								}
						}
						event_start = cursor+1;
						id = 0;
				}
		}
		if(buf[strlen(buf)-1] != '\n') {
				remain_byte = cursor - event_start+1;
				strncpy(remain, buf+event_start, remain_byte);
				remain[remain_byte] = '\0';
				//		printf("remain: %s\n", remain);
		} else {
				remain_byte = 0;
		}

		// if we have enough events in the buffer..
		while(HASH_COUNT(event_buf) > REORDERING_WINDOW)
		{
				HASH_FIND_INT(event_buf, &next_event_id, eb);
				next_event_id++;
				if(eb != NULL) {
						if(strstr(eb->event, "type=SYSCALL") != NULL) {
								if(UBSIAnalysis) syscall_handler(eb->event);
								else printf("%s", eb->event);
						} else {
								printf("%s", eb->event);
						}
						HASH_DEL(event_buf, eb);
						free(eb->event);
						free(eb);
				} else {
						//fprintf(stderr, "!!!!!!!!!!!!!!!!!!!!!!!event id %d is not exist!, hash_count %d\n", next_event_id, HASH_COUNT(event_buf));
				}
		}
}

void UBSI_sig_handler(int signo)
{
		if(waitForEnd == FALSE) {
				UBSI_buffer_flush();
				exit(0);
		} else {
				// ignore the signal and the process continues until the end of the input stream/file.
		}
}
