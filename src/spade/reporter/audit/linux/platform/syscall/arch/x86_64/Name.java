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

public enum Name implements spade.reporter.audit.linux.platform.syscall.Name{

	ACCEPT("accept"),
	ACCEPT4("accept4"),
	BIND("bind"),
	CHDIR("chdir"),
	CHMOD("chmod"),
	CHROOT("chroot"),
	CLONE("clone"),
	CLOSE("close"),
	CONNECT("connect"),
	CREAT("creat"),
	DUP("dup"),
	DUP2("dup2"),
	DUP3("dup3"),
	EXECVE("execve"),
	EXIT("exit"),
	EXIT_GROUP("exit_group"),
	FCHDIR("fchdir"),
	FCHMOD("fchmod"),
	FCHMODAT("fchmodat"),
	FCNTL("fcntl"),
	FINIT_MODULE("finit_module"),
	FORK("fork"),
	FTRUNCATE("ftruncate"),
	INIT_MODULE("init_module"),
	KILL("kill"),
	LINK("link"),
	LINKAT("linkat"),
	LSEEK("lseek"),
	MADVISE("madvise"),
	MKNOD("mknod"),
	MKNODAT("mknodat"),
	MMAP("mmap"),
	MPROTECT("mprotect"),
	MQ_OPEN("mq_open"),
	MQ_TIMEDRECEIVE("mq_timedreceive"),
	MQ_TIMEDSEND("mq_timedsend"),
	MQ_UNLINK("mq_unlink"),
	MSGCTL("msgctl"),
	MSGGET("msgget"),
	MSGRCV("msgrcv"),
	MSGSND("msgsnd"),
	OPEN("open"),
	OPENAT("openat"),
	PIPE("pipe"),
	PIPE2("pipe2"),
	PIVOT_ROOT("pivot_root"),
	PREAD64("pread64"),
	PREADV("preadv"),
	PTRACE("ptrace"),
	PWRITE64("pwrite64"),
	PWRITEV("pwritev"),
	READ("read"),
	READV("readv"),
	RECVFROM("recvfrom"),
	RECVMSG("recvmsg"),
	RENAME("rename"),
	RENAMEAT("renameat"),
	SENDMSG("sendmsg"),
	SENDTO("sendto"),
	SETFSGID("setfsgid"),
	SETFSUID("setfsuid"),
	SETGID("setgid"),
	SETNS("setns"),
	SETREGID("setregid"),
	SETRESGID("setresgid"),
	SETRESUID("setresuid"),
	SETREUID("setreuid"),
	SETUID("setuid"),
	SHMAT("shmat"),
	SHMCTL("shmctl"),
	SHMDT("shmdt"),
	SHMGET("shmget"),
	SOCKET("socket"),
	SOCKETPAIR("socketpair"),
	SPLICE("splice"),
	SYMLINK("symlink"),
	SYMLINKAT("symlinkat"),
	TEE("tee"),
	TRUNCATE("truncate"),
	UNLINK("unlink"),
	UNLINKAT("unlinkat"),
	UNSHARE("unshare"),
	VFORK("vfork"),
	VMSPLICE("vmsplice"),
	WRITE("write"),
	WRITEV("writev");

	private final String value;

	Name(final String value){
		this.value = value;
	}

	@Override
	public String value(){
		return value;
	}

}
