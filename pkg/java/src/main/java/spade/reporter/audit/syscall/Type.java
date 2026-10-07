/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

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
package spade.reporter.audit.syscall;

/**
 * System calls in the arch system call tables (cfg/spade.reporter.audit.arch.*.syscall.Table.config), across all
 * arches. The lowercase constant name is the system call name in the tables, except for {@link #UNSUPPORTED}.
 */
public enum Type{

	// Type of a system call that is not in a table (see Syscall.UNSUPPORTED). Not allowed in the tables.
	UNSUPPORTED,

	ACCEPT,
	ACCEPT4,
	BIND,
	CHDIR,
	CHMOD,
	CHROOT,
	CLONE,
	CLOSE,
	CONNECT,
	CREAT,
	DUP,
	DUP2,
	DUP3,
	EXECVE,
	EXIT,
	EXIT_GROUP,
	FCHDIR,
	FCHMOD,
	FCHMODAT,
	FCNTL,
	FINIT_MODULE,
	FORK,
	FTRUNCATE,
	INIT_MODULE,
	KILL,
	LINK,
	LINKAT,
	LSEEK,
	MADVISE,
	MKNOD,
	MKNODAT,
	MMAP,
	MPROTECT,
	MQ_OPEN,
	MQ_TIMEDRECEIVE,
	MQ_TIMEDSEND,
	MQ_UNLINK,
	MSGCTL,
	MSGGET,
	MSGRCV,
	MSGSND,
	OPEN,
	OPENAT,
	PIPE,
	PIPE2,
	PIVOT_ROOT,
	PREAD,
	PREADV,
	PTRACE,
	PWRITE,
	PWRITEV,
	READ,
	READV,
	RECVFROM,
	RECVMSG,
	RENAME,
	RENAMEAT,
	SENDMSG,
	SENDTO,
	SETFSGID,
	SETFSUID,
	SETGID,
	SETNS,
	SETREGID,
	SETRESGID,
	SETRESUID,
	SETREUID,
	SETUID,
	SHMAT,
	SHMCTL,
	SHMDT,
	SHMGET,
	SOCKET,
	SOCKETPAIR,
	SPLICE,
	SYMLINK,
	SYMLINKAT,
	TEE,
	TRUNCATE,
	UNLINK,
	UNLINKAT,
	UNSHARE,
	VFORK,
	VMSPLICE,
	WRITE,
	WRITEV;
}
