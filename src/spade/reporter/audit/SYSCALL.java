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
 --------------------------------------------------------------------------------
 */
package spade.reporter.audit;

public enum SYSCALL {

	FORK, VFORK, CLONE, EXECVE, 
	UNIT, // Used for beep unit creation (not an actual system call)
	LOAD, // Used for linked libraries when an execve happens (not an actual system call)
	SETUID, SETREUID, SETRESUID, SETFSUID,
	SETGID, SETREGID, SETRESGID, SETFSGID,
	MMAP, MMAP2, MPROTECT,
	BIND, ACCEPT, ACCEPT4, CONNECT, SOCKET, 
	SENDTO, SENDMSG, RECVFROM, RECVMSG, 
	SEND, // Used for grouping SENDTO and SENDMSG system call (not an actual system call) 
	RECV, // Used for grouping RECVFROM and RECVMSG system call (not an actual system call)
	CHMOD, FCHMOD, FCHMODAT,
	TRUNCATE, FTRUNCATE, 
	OPEN, OPENAT, MKNOD, MKNODAT, CREAT, CLOSE, 
	FCNTL,
	CREATE, // Used for grouping CREAT and OPEN system call where OPEN creates the file (not an actual system call) 
	UPDATE, // Used for version update edges between artifacts
	READ, READV, PREAD, PREADV, WRITE, WRITEV, PWRITE, PWRITEV, 
	SYMLINK, SYMLINKAT, LINK, LINKAT, 
	UNLINK, UNLINKAT,		
	RENAME, RENAMEAT,
	UNKNOWN, // Used for edges between processes where system call wasn't known (not an actual system call)
	DUP, DUP2, DUP3, 
	EXIT, EXIT_GROUP, 
	PIPE, PIPE2, 
	TEE, SPLICE, VMSPLICE,
	INIT_MODULE, FINIT_MODULE,
	UNSUPPORTED; // Used for system calls not in this enum (not an actual system call)
	
	public static SYSCALL getSyscall(int syscallNum, int arch){
		if(arch == 32){
			return get32BitSyscall(syscallNum);
		}else if(arch == 64){
			return get64BitSyscall(syscallNum);
		}
		return null;
	}
	
	private static SYSCALL get64BitSyscall(int syscallNum){
		// System call numbers are derived from:
        // http://blog.rchapman.org/post/36801038863/linux-system-call-table-for-x86-64
		
		// source : https://github.com/bnoordhuis/strace/blob/master/linux/x86_64/syscallent.h
		switch (syscallNum) {
			case 175:	return INIT_MODULE;
			case 313:	return FINIT_MODULE;
			case 276:	return TEE;
			case 275:	return SPLICE;
			case 278:	return VMSPLICE;
			case 43:	return ACCEPT;
			case 288:	return ACCEPT4;
			case 49:	return BIND;
			case 90:	return CHMOD;
			case 85:	return CREAT;
			case 3:		return CLOSE;
			case 56:	return CLONE;
			case 42:	return CONNECT;
			case 32:	return DUP;
			case 33:	return DUP2;
			case 292:	return DUP3;
			case 59:	return EXECVE;
			case 60:	return EXIT;
			case 231:	return EXIT_GROUP;	
			case 91:	return FCHMOD;
			case 268:	return FCHMODAT;
			case 72:	return FCNTL;
			case 58:	return FORK;
			case 77:	return FTRUNCATE;
			case 86:	return LINK;
			case 265:	return LINKAT;
			case 133:	return MKNOD;
			case 259:	return MKNODAT;
			case 9:		return MMAP;
			case 10:	return MPROTECT;
			case 2:		return OPEN;
			case 257:	return OPENAT;
			case 22:	return PIPE;
			case 293:	return PIPE2;
			case 17:	return PREAD;
			case 295:	return PREADV;
			case 18:	return PWRITE;
			case 296:	return PWRITEV;
			case 0:		return READ;
			case 19:	return READV;
			case 45:	return RECVFROM;
			case 47:	return RECVMSG;
			case 82:	return RENAME;
			case 264:	return RENAMEAT;
			case 46:	return SENDMSG;
			case 44:	return SENDTO;
			case 123:	return SETFSGID;
			case 122:	return SETFSUID;
			case 106:	return SETGID;
			case 114:	return SETREGID;
			case 119:	return SETRESGID;
			case 117:	return SETRESUID;
			case 113:	return SETREUID;
			case 105:	return SETUID;
			case 41:	return SOCKET;
			case 88:	return SYMLINK;
			case 266:	return SYMLINKAT;
			case 76:	return TRUNCATE;	
			case 87:	return UNLINK;
			case 263:	return UNLINKAT;
			case 57:	return VFORK;
			case 1:		return WRITE;
			case 20:	return WRITEV;
			default:	return UNSUPPORTED;
		}
	}

	private static SYSCALL get32BitSyscall(int syscallNum){
		// System call numbers are derived from:
        // https://android.googlesource.com/platform/bionic/+/android-4.1.1_r1/libc/SYSCALLS.TXT
        // TODO: Update the calls to make them linux specific.
		
		// source : https://github.com/bnoordhuis/strace/blob/master/linux/i386/syscallent.h
		switch (syscallNum) {
			case 128: return INIT_MODULE;
			case 350: return FINIT_MODULE;
			case 315: return TEE;
			case 313: return SPLICE;
			case 316: return VMSPLICE;
			case 1: return EXIT;
			case 2: return FORK;
			case 3: return READ;
			case 4: return WRITE;
			case 5: return OPEN;
			case 6: return CLOSE;
			case 9: return LINK;
			case 10: return UNLINK;
			case 11: return EXECVE;
			case 14: return MKNOD;
			case 15: return CHMOD;
			case 38: return RENAME;
			case 41: return DUP;
			case 42: return PIPE;
			case 55: return FCNTL;
			case 63: return DUP2;
			case 83: return SYMLINK;
			case 90: return MMAP2;
			case 92: return TRUNCATE;
			case 93: return FTRUNCATE;
			case 94: return FCHMOD;
			case 120: return CLONE;
			case 125: return MPROTECT;
			case 145: return READV;
			case 146: return WRITEV;			
			case 180: return PREAD;
			case 333: return PREADV;
			case 181: return PWRITE;
			case 334: return PWRITEV;
			case 190: return VFORK;
			case 192: return MMAP2;
			case 252: return EXIT_GROUP;
			case 282: return BIND;
			case 283: return CONNECT;
			case 285: return ACCEPT;
			case 290: return SENDTO;
			case 292: return RECVFROM;
			case 295: return OPENAT;
			case 296: return SENDMSG;
			case 297: return RECVMSG;
			case 301: return UNLINKAT;
			case 302: return RENAMEAT;
			case 303: return LINKAT;
			case 304: return SYMLINKAT;
			case 322: return OPENAT;
			case 331: return PIPE2;
			case 359: return PIPE2;
			case 216:	return SETFSGID;
			case 215:	return SETFSUID;
			case 214:	return SETGID;
			case 213:	return SETUID;
			case 204:	return SETREGID;
			case 203:	return SETREUID;
			case 210:	return SETRESGID;
			case 208:	return SETRESUID;
			default: return UNSUPPORTED;
		}
	}
}