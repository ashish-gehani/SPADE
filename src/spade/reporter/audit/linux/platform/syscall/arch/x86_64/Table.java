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
package spade.reporter.audit.linux.platform.syscall.arch.x86_64;

import spade.reporter.audit.linux.platform.syscall.Syscall;

public class Table extends spade.reporter.audit.linux.platform.syscall.Table{

	public Table(){
		put(new Syscall(43,  Name.ACCEPT));
		put(new Syscall(288, Name.ACCEPT4));
		put(new Syscall(49,  Name.BIND));
		put(new Syscall(80,  Name.CHDIR));
		put(new Syscall(90,  Name.CHMOD));
		put(new Syscall(161, Name.CHROOT));
		put(new Syscall(56,  Name.CLONE));
		put(new Syscall(3,   Name.CLOSE));
		put(new Syscall(42,  Name.CONNECT));
		put(new Syscall(85,  Name.CREAT));
		put(new Syscall(32,  Name.DUP));
		put(new Syscall(33,  Name.DUP2));
		put(new Syscall(292, Name.DUP3));
		put(new Syscall(59,  Name.EXECVE));
		put(new Syscall(60,  Name.EXIT));
		put(new Syscall(231, Name.EXIT_GROUP));
		put(new Syscall(81,  Name.FCHDIR));
		put(new Syscall(91,  Name.FCHMOD));
		put(new Syscall(268, Name.FCHMODAT));
		put(new Syscall(72,  Name.FCNTL));
		put(new Syscall(313, Name.FINIT_MODULE));
		put(new Syscall(57,  Name.FORK));
		put(new Syscall(77,  Name.FTRUNCATE));
		put(new Syscall(175, Name.INIT_MODULE));
		put(new Syscall(62,  Name.KILL));
		put(new Syscall(86,  Name.LINK));
		put(new Syscall(265, Name.LINKAT));
		put(new Syscall(8,   Name.LSEEK));
		put(new Syscall(28,  Name.MADVISE));
		put(new Syscall(133, Name.MKNOD));
		put(new Syscall(259, Name.MKNODAT));
		put(new Syscall(9,   Name.MMAP));
		put(new Syscall(10,  Name.MPROTECT));
		put(new Syscall(240, Name.MQ_OPEN));
		put(new Syscall(243, Name.MQ_TIMEDRECEIVE));
		put(new Syscall(242, Name.MQ_TIMEDSEND));
		put(new Syscall(241, Name.MQ_UNLINK));
		put(new Syscall(71,  Name.MSGCTL));
		put(new Syscall(68,  Name.MSGGET));
		put(new Syscall(70,  Name.MSGRCV));
		put(new Syscall(69,  Name.MSGSND));
		put(new Syscall(2,   Name.OPEN));
		put(new Syscall(257, Name.OPENAT));
		put(new Syscall(22,  Name.PIPE));
		put(new Syscall(293, Name.PIPE2));
		put(new Syscall(155, Name.PIVOT_ROOT));
		put(new Syscall(17,  Name.PREAD64));
		put(new Syscall(295, Name.PREADV));
		put(new Syscall(101, Name.PTRACE));
		put(new Syscall(18,  Name.PWRITE64));
		put(new Syscall(296, Name.PWRITEV));
		put(new Syscall(0,   Name.READ));
		put(new Syscall(19,  Name.READV));
		put(new Syscall(45,  Name.RECVFROM));
		put(new Syscall(47,  Name.RECVMSG));
		put(new Syscall(82,  Name.RENAME));
		put(new Syscall(264, Name.RENAMEAT));
		put(new Syscall(46,  Name.SENDMSG));
		put(new Syscall(44,  Name.SENDTO));
		put(new Syscall(123, Name.SETFSGID));
		put(new Syscall(122, Name.SETFSUID));
		put(new Syscall(106, Name.SETGID));
		put(new Syscall(308, Name.SETNS));
		put(new Syscall(114, Name.SETREGID));
		put(new Syscall(119, Name.SETRESGID));
		put(new Syscall(117, Name.SETRESUID));
		put(new Syscall(113, Name.SETREUID));
		put(new Syscall(105, Name.SETUID));
		put(new Syscall(30,  Name.SHMAT));
		put(new Syscall(31,  Name.SHMCTL));
		put(new Syscall(67,  Name.SHMDT));
		put(new Syscall(29,  Name.SHMGET));
		put(new Syscall(41,  Name.SOCKET));
		put(new Syscall(53,  Name.SOCKETPAIR));
		put(new Syscall(275, Name.SPLICE));
		put(new Syscall(88,  Name.SYMLINK));
		put(new Syscall(266, Name.SYMLINKAT));
		put(new Syscall(276, Name.TEE));
		put(new Syscall(76,  Name.TRUNCATE));
		put(new Syscall(87,  Name.UNLINK));
		put(new Syscall(263, Name.UNLINKAT));
		put(new Syscall(272, Name.UNSHARE));
		put(new Syscall(58,  Name.VFORK));
		put(new Syscall(278, Name.VMSPLICE));
		put(new Syscall(1,   Name.WRITE));
		put(new Syscall(20,  Name.WRITEV));
	}

}
