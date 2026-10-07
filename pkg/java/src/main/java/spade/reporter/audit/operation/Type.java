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
package spade.reporter.audit.operation;

/**
 * OPM operations. Operation names are read from the config file (see {@link Table}).
 */
public enum Type{

	ACCEPT,
	BIND,
	CHMOD,
	CLONE,
	CLOSE,
	CONNECT,
	CREATE,
	DUP,
	EXECVE,
	EXIT,
	FCNTL,
	FORK,
	LINK,
	LOAD,
	LSEEK,
	MADVISE,
	MKNOD,
	MMAP,
	MPROTECT,
	OPEN,
	PIPE,
	PTRACE,
	READ,
	RECV,
	RENAME,
	SEND,
	SETGID,
	SETUID,
	TRUNCATE,
	UNIT,
	UNIT_DEPENDENCY,
	UNKNOWN,
	UNLINK,
	UPDATE,
	WRITE,
	TEE,
	SPLICE,
	VMSPLICE,
	INIT_MODULE,
	FINIT_MODULE,
	KILL,
	SETNS,
	UNSHARE,
	MSGGET,
	MSGCTL,
	MSGSND,
	MSGRCV,
	SHMGET,
	SHMAT,
	SHMCTL,
	MQ_OPEN,
	MQ_TIMEDSEND,
	MQ_TIMEDRECEIVE,
	MQ_UNLINK;
}
