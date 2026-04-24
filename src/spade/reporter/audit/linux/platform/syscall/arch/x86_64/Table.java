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
		put(43,  new Syscall(43,  "accept",          "accept"));
		put(288, new Syscall(288, "accept4",          "accept"));
		put(49,  new Syscall(49,  "bind",             "bind"));
		put(80,  new Syscall(80,  "chdir",            "chdir"));
		put(90,  new Syscall(90,  "chmod",            "chmod"));
		put(161, new Syscall(161, "chroot",           "chroot"));
		put(56,  new Syscall(56,  "clone",            "clone"));
		put(3,   new Syscall(3,   "close",            "close"));
		put(42,  new Syscall(42,  "connect",          "connect"));
		put(85,  new Syscall(85,  "creat",            "create"));
		put(32,  new Syscall(32,  "dup",              "dup"));
		put(33,  new Syscall(33,  "dup2",             "dup"));
		put(292, new Syscall(292, "dup3",             "dup"));
		put(59,  new Syscall(59,  "execve",           "execve"));
		put(60,  new Syscall(60,  "exit",             "exit"));
		put(231, new Syscall(231, "exit_group",       "exit"));
		put(81,  new Syscall(81,  "fchdir",           "chdir"));
		put(91,  new Syscall(91,  "fchmod",           "chmod"));
		put(268, new Syscall(268, "fchmodat",         "chmod"));
		put(72,  new Syscall(72,  "fcntl",            "fcntl"));
		put(313, new Syscall(313, "finit_module",     "finit_module"));
		put(57,  new Syscall(57,  "fork",             "fork"));
		put(77,  new Syscall(77,  "ftruncate",        "truncate"));
		put(175, new Syscall(175, "init_module",      "init_module"));
		put(62,  new Syscall(62,  "kill",             "kill"));
		put(86,  new Syscall(86,  "link",             "link"));
		put(265, new Syscall(265, "linkat",           "link"));
		put(8,   new Syscall(8,   "lseek",            "lseek"));
		put(28,  new Syscall(28,  "madvise",          "madvise"));
		put(133, new Syscall(133, "mknod",            "mknod"));
		put(259, new Syscall(259, "mknodat",          "mknod"));
		put(9,   new Syscall(9,   "mmap",             "mmap"));
		put(10,  new Syscall(10,  "mprotect",         "mprotect"));
		put(240, new Syscall(240, "mq_open",          "mq_open"));
		put(243, new Syscall(243, "mq_timedreceive",  "mq_timedreceive"));
		put(242, new Syscall(242, "mq_timedsend",     "mq_timedsend"));
		put(241, new Syscall(241, "mq_unlink",        "mq_unlink"));
		put(71,  new Syscall(71,  "msgctl",           "msgctl"));
		put(68,  new Syscall(68,  "msgget",           "msgget"));
		put(70,  new Syscall(70,  "msgrcv",           "msgrcv"));
		put(69,  new Syscall(69,  "msgsnd",           "msgsnd"));
		put(2,   new Syscall(2,   "open",             "open"));
		put(257, new Syscall(257, "openat",           "open"));
		put(22,  new Syscall(22,  "pipe",             "pipe"));
		put(293, new Syscall(293, "pipe2",            "pipe"));
		put(155, new Syscall(155, "pivot_root",       "pivot_root"));
		put(17,  new Syscall(17,  "pread64",          "read"));
		put(295, new Syscall(295, "preadv",           "read"));
		put(101, new Syscall(101, "ptrace",           "ptrace"));
		put(18,  new Syscall(18,  "pwrite64",         "write"));
		put(296, new Syscall(296, "pwritev",          "write"));
		put(0,   new Syscall(0,   "read",             "read"));
		put(19,  new Syscall(19,  "readv",            "read"));
		put(45,  new Syscall(45,  "recvfrom",         "recv"));
		put(47,  new Syscall(47,  "recvmsg",          "recv"));
		put(82,  new Syscall(82,  "rename",           "rename"));
		put(264, new Syscall(264, "renameat",         "rename"));
		put(46,  new Syscall(46,  "sendmsg",          "send"));
		put(44,  new Syscall(44,  "sendto",           "send"));
		put(123, new Syscall(123, "setfsgid",         "setgid"));
		put(122, new Syscall(122, "setfsuid",         "setuid"));
		put(106, new Syscall(106, "setgid",           "setgid"));
		put(308, new Syscall(308, "setns",            "setns"));
		put(114, new Syscall(114, "setregid",         "setgid"));
		put(119, new Syscall(119, "setresgid",        "setgid"));
		put(117, new Syscall(117, "setresuid",        "setuid"));
		put(113, new Syscall(113, "setreuid",         "setuid"));
		put(105, new Syscall(105, "setuid",           "setuid"));
		put(30,  new Syscall(30,  "shmat",            "shmat"));
		put(31,  new Syscall(31,  "shmctl",           "shmctl"));
		put(67,  new Syscall(67,  "shmdt",            "shmdt"));
		put(29,  new Syscall(29,  "shmget",           "shmget"));
		put(41,  new Syscall(41,  "socket",           "socket"));
		put(53,  new Syscall(53,  "socketpair",       "socketpair"));
		put(275, new Syscall(275, "splice",           "splice"));
		put(88,  new Syscall(88,  "symlink",          "link"));
		put(266, new Syscall(266, "symlinkat",        "link"));
		put(276, new Syscall(276, "tee",              "tee"));
		put(76,  new Syscall(76,  "truncate",         "truncate"));
		put(87,  new Syscall(87,  "unlink",           "unlink"));
		put(263, new Syscall(263, "unlinkat",         "unlink"));
		put(272, new Syscall(272, "unshare",          "unshare"));
		put(58,  new Syscall(58,  "vfork",            "fork"));
		put(278, new Syscall(278, "vmsplice",         "vmsplice"));
		put(1,   new Syscall(1,   "write",            "write"));
		put(20,  new Syscall(20,  "writev",           "write"));
	}

}
