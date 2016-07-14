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

	FORK, VFORK, CLONE, CHMOD, FCHMOD, SENDTO, SENDMSG, RECVFROM, RECVMSG, 
	TRUNCATE, FTRUNCATE, READ, READV, PREAD64, WRITE, WRITEV, PWRITE64, 
	ACCEPT, ACCEPT4, CONNECT, SYMLINK, LINK, SETUID, SETREUID, SETRESUID,
	OPEN, OPENAT, MMAP, MMAP2, MPROTECT, RENAME, EXECVE, UNKNOWN, KILL, 
	MKNOD, CREAT, MKNODAT, BIND, DUP, DUP2, DUP3, EXIT,
	EXIT_GROUP, CLOSE, PIPE, PIPE2, UPDATE, CREATE, UNIT, LOAD, SEND, RECV,
	UNSUPPORTED;

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
			case 60: //exit()
				return EXIT;
			case 231: //exit_group()
				return EXIT_GROUP;
			case 9: //mmap()
				return MMAP;
			case 10: //mprotect()
				return MPROTECT;
			case 57: // fork()
				return VFORK;
			case 58: // vfork()
				return FORK;
			case 56: // clone()
				return CLONE;
			case 59: // execve()
				return EXECVE;
			case 257: //openat()
				return OPENAT;
			case 2: // open()
				return OPEN;
			case 3: // close()
				return CLOSE;
			case 86: // link()
				return LINK;
			case 88: // symlink()
				return SYMLINK;
			case 259: //mknodat
				return MKNODAT;              	
			case 133: // mknod()
				return MKNOD;
			case 85: //creat
				return CREAT;
			case 82: // rename()
				return RENAME;
			case 22: // pipe()
				return PIPE;
			case 293: // pipe2()
				return PIPE2;
			case 32: // dup()
				return DUP;
			case 33: // dup2()
				return DUP2;
			case 292: //dup3()
				return DUP3;
	
			case 113: // setreuid()
				return SETREUID;
			case 117: // setresuid()
				return SETRESUID;
			case 105: // setuid()
				return SETUID;
	
			case 76: // truncate()
				return TRUNCATE;	
			case 77: // ftruncate()
				return FTRUNCATE;
	
			case 90: // chmod()
				return CHMOD;	
			case 91: // fchmod()
				return FCHMOD;
				
			case 0: // read()
				return READ;
			case 19: // readv()
				return READV;
			case 17: // pread64()
				return PREAD64;
			case 1: // write()
				return WRITE;
			case 20: // writev()
				return WRITEV;
			case 18: // pwrite64()
				return PWRITE64;
			case 44: // sendto()
				return SENDTO;
			case 46: // sendmsg()
				return SENDMSG;
			case 45: // recvfrom()
				return RECVFROM;
			case 47: // recvmsg()
				return RECVMSG;
	
			case 49: //bind
				return BIND;
			case 42: // connect()
				return CONNECT;
			case 288: //accept4()
				return ACCEPT4;
			case 43: // accept()
				return ACCEPT;
//              case 41: // socket()
//                  break;
			case 62:
				return KILL;
			default:
				return UNSUPPORTED;
		}
	}

	private static SYSCALL get32BitSyscall(int syscallNum){
		// System call numbers are derived from:
        // https://android.googlesource.com/platform/bionic/+/android-4.1.1_r1/libc/SYSCALLS.TXT
        // TODO: Update the calls to make them linux specific.
		
		// source : https://github.com/bnoordhuis/strace/blob/master/linux/i386/syscallent.h
		switch (syscallNum) {
			case 1: //exit()
				return EXIT;
			case 252: //exit_group()
				return EXIT_GROUP;
			case 90: //old_mmap
			case 192: //mmap2
				return MMAP2;
			case 125: //mprotect
				return MPROTECT;
			case 2: // fork()
				return FORK;
			case 190: // vfork()
				return VFORK;
			case 120: // clone()
				return CLONE;
			case 11: // execve()
				return EXECVE;
			case 295: //openat()
			case 322: //openat()
				return OPENAT;
			case 5: // open()
				return OPEN;
			case 6: // close()
				return CLOSE;
			case 9: // link()
				return LINK;
			case 83: // symlink()
				return SYMLINK;
			case 14: // mknod()
				return MKNOD;
			case 38: // rename()
				return RENAME;
			case 42: // pipe()
				return PIPE;
			case 331: // pipe2()
			case 359: // pipe2()
				return PIPE2;
			case 41: // dup()
				return DUP;
			case 63: // dup2()
				return DUP2;
			case 203: // setreuid()
				return SETREUID;
			case 208: // setresuid()
				return SETRESUID;
			case 213: // setuid()
				return SETUID;
			case 92: // truncate()
				return TRUNCATE;
			case 93: // ftruncate()
				return FTRUNCATE;
			case 15: // chmod()
				return CHMOD;
			case 94: // fchmod()
				return FCHMOD;
			case 3: // read()
				return READ;
			case 145: // readv()
				return READV;
			case 180: // pread64()
				return PREAD64;
			case 4: // write()
				return WRITE;
			case 146: // writev()
				return WRITEV;
			case 181: // pwrite64()
				return PWRITE64;
			case 290: // sendto()
				return SENDTO;
			case 296: // sendmsg()
				return SENDMSG;
			case 292: // recvfrom()
				return RECVFROM;
			case 297: // recvmsg()
				return RECVMSG;
			case 282: //bind
				return BIND;
			case 283: // connect()
				return CONNECT;
			case 285: // accept()
				return ACCEPT;
//				case 281: // socket()
//				    break;
			case 129: // kill()
				return KILL;
			default:
				return UNSUPPORTED;
		}
	}
		

}