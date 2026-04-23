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
		put(43,  new Syscall(43,  "accept"));
		put(288, new Syscall(288, "accept4"));
		put(49,  new Syscall(49,  "bind"));
		put(80,  new Syscall(80,  "chdir"));
		put(90,  new Syscall(90,  "chmod"));
		put(161, new Syscall(161, "chroot"));
		put(56,  new Syscall(56,  "clone"));
		put(3,   new Syscall(3,   "close"));
		put(42,  new Syscall(42,  "connect"));
		put(85,  new Syscall(85,  "creat"));
		put(32,  new Syscall(32,  "dup"));
		put(33,  new Syscall(33,  "dup2"));
		put(292, new Syscall(292, "dup3"));
		put(59,  new Syscall(59,  "execve"));
		put(60,  new Syscall(60,  "exit"));
		put(231, new Syscall(231, "exit_group"));
		put(81,  new Syscall(81,  "fchdir"));
		put(91,  new Syscall(91,  "fchmod"));
		put(268, new Syscall(268, "fchmodat"));
		put(72,  new Syscall(72,  "fcntl"));
		put(313, new Syscall(313, "finit_module"));
		put(57,  new Syscall(57,  "fork"));
		put(77,  new Syscall(77,  "ftruncate"));
		put(175, new Syscall(175, "init_module"));
		put(62,  new Syscall(62,  "kill"));
		put(86,  new Syscall(86,  "link"));
		put(265, new Syscall(265, "linkat"));
		put(8,   new Syscall(8,   "lseek"));
		put(28,  new Syscall(28,  "madvise"));
		put(133, new Syscall(133, "mknod"));
		put(259, new Syscall(259, "mknodat"));
		put(9,   new Syscall(9,   "mmap"));
		put(10,  new Syscall(10,  "mprotect"));
		put(240, new Syscall(240, "mq_open"));
		put(243, new Syscall(243, "mq_timedreceive"));
		put(242, new Syscall(242, "mq_timedsend"));
		put(241, new Syscall(241, "mq_unlink"));
		put(71,  new Syscall(71,  "msgctl"));
		put(68,  new Syscall(68,  "msgget"));
		put(70,  new Syscall(70,  "msgrcv"));
		put(69,  new Syscall(69,  "msgsnd"));
		put(2,   new Syscall(2,   "open"));
		put(257, new Syscall(257, "openat"));
		put(22,  new Syscall(22,  "pipe"));
		put(293, new Syscall(293, "pipe2"));
		put(155, new Syscall(155, "pivot_root"));
		put(17,  new Syscall(17,  "pread64"));
		put(295, new Syscall(295, "preadv"));
		put(101, new Syscall(101, "ptrace"));
		put(18,  new Syscall(18,  "pwrite64"));
		put(296, new Syscall(296, "pwritev"));
		put(0,   new Syscall(0,   "read"));
		put(19,  new Syscall(19,  "readv"));
		put(45,  new Syscall(45,  "recvfrom"));
		put(47,  new Syscall(47,  "recvmsg"));
		put(82,  new Syscall(82,  "rename"));
		put(264, new Syscall(264, "renameat"));
		put(46,  new Syscall(46,  "sendmsg"));
		put(44,  new Syscall(44,  "sendto"));
		put(123, new Syscall(123, "setfsgid"));
		put(122, new Syscall(122, "setfsuid"));
		put(106, new Syscall(106, "setgid"));
		put(308, new Syscall(308, "setns"));
		put(114, new Syscall(114, "setregid"));
		put(119, new Syscall(119, "setresgid"));
		put(117, new Syscall(117, "setresuid"));
		put(113, new Syscall(113, "setreuid"));
		put(105, new Syscall(105, "setuid"));
		put(30,  new Syscall(30,  "shmat"));
		put(31,  new Syscall(31,  "shmctl"));
		put(67,  new Syscall(67,  "shmdt"));
		put(29,  new Syscall(29,  "shmget"));
		put(41,  new Syscall(41,  "socket"));
		put(53,  new Syscall(53,  "socketpair"));
		put(275, new Syscall(275, "splice"));
		put(88,  new Syscall(88,  "symlink"));
		put(266, new Syscall(266, "symlinkat"));
		put(276, new Syscall(276, "tee"));
		put(76,  new Syscall(76,  "truncate"));
		put(87,  new Syscall(87,  "unlink"));
		put(263, new Syscall(263, "unlinkat"));
		put(272, new Syscall(272, "unshare"));
		put(58,  new Syscall(58,  "vfork"));
		put(278, new Syscall(278, "vmsplice"));
		put(1,   new Syscall(1,   "write"));
		put(20,  new Syscall(20,  "writev"));
	}

}
