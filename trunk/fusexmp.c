/*
  FUSE: Filesystem in Userspace
  Copyright (C) 2001-2007  Miklos Szeredi <miklos@szeredi.hu>

  This program can be distributed under the terms of the GNU GPL.
  See the file COPYING.

  gcc -I/usr/java/jdk1.6.0_21/include/ -I/usr/java/jdk1.6.0_21/include/linux -L. -ljvm -Wall `pkg-config fuse --cflags --libs` fusexmp.c -o fusexmp
*/

#define FUSE_USE_VERSION 26

#ifdef HAVE_CONFIG_H
#include <config.h>
#endif

#ifdef linux
/* For pread()/pwrite() */
#define _XOPEN_SOURCE 500
#endif

#include <fuse.h>
#include <stdio.h>
#include <string.h>
#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <errno.h>
#include <sys/time.h>
#ifdef HAVE_SETXATTR
#include <sys/xattr.h>
#endif

JavaVM* jvm;
JNIEnv* env;

jclass FUSEProducerClass;
jobject producerInstance;

jmethodID initMethod;
jmethodID readwriteMethod;
jmethodID renameMethod;
jmethodID linkMethod;
jmethodID shutdownMethod;

void create_vm()
{
	JavaVMInitArgs args;
	JavaVMOption options[1];

	/* There is a new JNI_VERSION_1_4, but it doesn't add anything for the purposes of our example. */
	args.version = JNI_VERSION_1_2;
	args.nOptions = 1;
	options[0].optionString = "-Djava.class.path=.:lib/geronimo-jta_1.1_spec-1.1.1.jar:lib/neo4j-1.2.M04.jar:lib/neo4j-1.2.M04-javadoc.jar:lib/neo4j-examples-1.2.M04.jar:lib/neo4j-graph-algo-0.7-1.2.M04.jar:lib/neo4j-index-1.2-1.2.M04.jar:lib/neo4j-kernel-1.2-1.2.M04.jar:lib/neo4j-kernel-1.2-1.2.M04-tests.jar:lib/neo4j-lucene-index-0.2-1.2.M04.jar:lib/neo4j-management-1.2-1.2.M04.jar:lib/neo4j-online-backup-0.7-1.2.M04.jar:lib/neo4j-remote-graphdb-0.8-1.2.M04.jar:lib/neo4j-shell-1.2-1.2.M04.jar:lib/neo4j-udc-0.1-1.2.M04-neo4j.jar:lib/org.apache.servicemix.bundles.jline-0.9.94_1.jar:lib/org.apache.servicemix.bundles.lucene-3.0.1_2.jar:lib/protobuf-java-2.3.0.jar:lib/jgrapht-jdk1.6.jar";
	args.options = options;
	args.ignoreUnrecognized = JNI_TRUE;

	JNI_CreateJavaVM(&jvm, (void **)&env, &args);

	FUSEProducerClass = (*env)->FindClass(env, "FUSEProducer");

	initMethod = (*env)->GetMethodID(env, FUSEProducerClass, "<init>", "()V");
	readwriteMethod = (*env)->GetMethodID(env, FUSEProducerClass, "readwrite", "(IIIILjava/lang/String;)V");
	renameMethod = (*env)->GetMethodID(env, FUSEProducerClass, "rename", "(IIILjava/lang/String;Ljava/lang/String;)V");
	linkMethod = (*env)->GetMethodID(env, FUSEProducerClass, "link", "(IIILjava/lang/String;Ljava/lang/String;)V");

	producerInstance = (*env)->NewObject(env, FUSEProducerClass, initMethod);
}

static int make_java_call(char method, int pid, int uid, int gid, const char *path1, const char *path2)
{
	(*jvm)->AttachCurrentThread(jvm, (void**)&env, NULL);
	jstring jpath1 = (*env)->NewStringUTF(env, path1);
	jstring jpath2 = (*env)->NewStringUTF(env, path2);
	
	switch (method)
	{
		case 'r':		// read
			(*env)->CallVoidMethod(env, producerInstance, readwriteMethod, 0, pid, uid, gid, jpath1);
			break;
		case 'w':		// write
			(*env)->CallVoidMethod(env, producerInstance, readwriteMethod, 1, pid, uid, gid, jpath1);
			break;
		case 'n':		// rename
			(*env)->CallVoidMethod(env, producerInstance, renameMethod, pid, uid, gid, jpath1, jpath2);
			break;
		case 'l':		// link
			(*env)->CallVoidMethod(env, producerInstance, linkMethod, pid, uid, gid, jpath1, jpath2);
			break;
	}
	
	return 0;
}

static int xmp_getattr(const char *path, struct stat *stbuf)
{
	int res;

	res = lstat(path, stbuf);
	if (res == -1)
		return -errno;

	return 0;
}

static int xmp_access(const char *path, int mask)
{
	int res;

	res = access(path, mask);
	if (res == -1)
		return -errno;

	return 0;
}

static int xmp_readlink(const char *path, char *buf, size_t size)
{
	int res;

	res = readlink(path, buf, size - 1);
	if (res == -1)
		return -errno;

	buf[res] = '\0';
	return 0;
}


static int xmp_readdir(const char *path, void *buf, fuse_fill_dir_t filler,
		       off_t offset, struct fuse_file_info *fi)
{
	DIR *dp;
	struct dirent *de;

	(void) offset;
	(void) fi;

	dp = opendir(path);
	if (dp == NULL)
		return -errno;

	while ((de = readdir(dp)) != NULL) {
		struct stat st;
		memset(&st, 0, sizeof(st));
		st.st_ino = de->d_ino;
		st.st_mode = de->d_type << 12;
		if (filler(buf, de->d_name, &st, 0))
			break;
	}

	closedir(dp);

	return 0;
}

static int xmp_mknod(const char *path, mode_t mode, dev_t rdev)
{
	int res;

	/* On Linux this could just be 'mknod(path, mode, rdev)' but this
	   is more portable */
	if (S_ISREG(mode)) {
		res = open(path, O_CREAT | O_EXCL | O_WRONLY, mode);
		if (res >= 0)
			res = close(res);
	} else if (S_ISFIFO(mode))
		res = mkfifo(path, mode);
	else
		res = mknod(path, mode, rdev);
	if (res == -1)
		return -errno;

	return 0;
}

static int xmp_mkdir(const char *path, mode_t mode)
{
	int res;

	res = mkdir(path, mode);
	if (res == -1)
		return -errno;

	return 0;
}

static int xmp_unlink(const char *path)
{
	int res;

	res = unlink(path);
	if (res == -1)
		return -errno;

	return 0;
}

static int xmp_rmdir(const char *path)
{
	int res;

	res = rmdir(path);
	if (res == -1)
		return -errno;

	return 0;
}

static int xmp_symlink(const char *from, const char *to)
{
	int res;

	res = symlink(from, to);
	if (res == -1)
		return -errno;

	make_java_call('l', fuse_get_context()->pid, fuse_get_context()->uid, fuse_get_context()->gid, from, to);

	return 0;
}

static int xmp_rename(const char *from, const char *to)
{
	int res;

	make_java_call('r', fuse_get_context()->pid, fuse_get_context()->uid, fuse_get_context()->gid, from, "");
	res = rename(from, to);
	if (res == -1)
		return -errno;

	make_java_call('w', fuse_get_context()->pid, fuse_get_context()->uid, fuse_get_context()->gid, to, "");
	make_java_call('n', fuse_get_context()->pid, fuse_get_context()->uid, fuse_get_context()->gid, from, to);

	return 0;
}

static int xmp_link(const char *from, const char *to)
{
	int res;

	res = link(from, to);
	if (res == -1)
		return -errno;

	make_java_call('l', fuse_get_context()->pid, fuse_get_context()->uid, fuse_get_context()->gid, from, to);

	return 0;
}

static int xmp_chmod(const char *path, mode_t mode)
{
	int res;

	res = chmod(path, mode);
	if (res == -1)
		return -errno;

	return 0;
}

static int xmp_chown(const char *path, uid_t uid, gid_t gid)
{
	int res;

	res = lchown(path, uid, gid);
	if (res == -1)
		return -errno;

	return 0;
}

static int xmp_truncate(const char *path, off_t size)
{
	int res;

	res = truncate(path, size);
	if (res == -1)
		return -errno;

	make_java_call('w', fuse_get_context()->pid, fuse_get_context()->uid, fuse_get_context()->gid, path, "");

	return 0;
}

static int xmp_utimens(const char *path, const struct timespec ts[2])
{
	int res;
	struct timeval tv[2];

	tv[0].tv_sec = ts[0].tv_sec;
	tv[0].tv_usec = ts[0].tv_nsec / 1000;
	tv[1].tv_sec = ts[1].tv_sec;
	tv[1].tv_usec = ts[1].tv_nsec / 1000;

	res = utimes(path, tv);
	if (res == -1)
		return -errno;

	return 0;
}

static int xmp_open(const char *path, struct fuse_file_info *fi)
{
	int res;

	res = open(path, fi->flags);
	if (res == -1)
		return -errno;
/*
	if ((fi->flags & O_RDONLY) == O_RDONLY) 
		make_java_call('r', fuse_get_context()->pid, fuse_get_context()->uid, fuse_get_context()->gid, path, "");
	else
		make_java_call('w', fuse_get_context()->pid, fuse_get_context()->uid, fuse_get_context()->gid, path, "");
/**/
	close(res);
	return 0;
}

static int xmp_read(const char *path, char *buf, size_t size, off_t offset,
		    struct fuse_file_info *fi)
{
	int fd;
	int res;

	(void) fi;
	fd = open(path, O_RDONLY);
	if (fd == -1)
		return -errno;

	res = pread(fd, buf, size, offset);
	if (res == -1)
		res = -errno;

	close(fd);

	make_java_call('r', fuse_get_context()->pid, fuse_get_context()->uid, fuse_get_context()->gid, path, "");

	return res;
}

static int xmp_write(const char *path, const char *buf, size_t size,
		     off_t offset, struct fuse_file_info *fi)
{
	int fd;
	int res;

	(void) fi;
	fd = open(path, O_WRONLY);
	if (fd == -1)
		return -errno;

	res = pwrite(fd, buf, size, offset);
	if (res == -1)
		res = -errno;

	close(fd);

	make_java_call('w', fuse_get_context()->pid, fuse_get_context()->uid, fuse_get_context()->gid, path, "");

	return res;
}

static int xmp_statfs(const char *path, struct statvfs *stbuf)
{
	int res;

	res = statvfs(path, stbuf);
	if (res == -1)
		return -errno;

	return 0;
}

static int xmp_release(const char *path, struct fuse_file_info *fi)
{
	/* Just a stub.	 This method is optional and can safely be left
	   unimplemented */

	(void) path;
	(void) fi;
	return 0;
}

static int xmp_fsync(const char *path, int isdatasync,
		     struct fuse_file_info *fi)
{
	/* Just a stub.	 This method is optional and can safely be left
	   unimplemented */

	(void) path;
	(void) isdatasync;
	(void) fi;
	return 0;
}

#ifdef HAVE_SETXATTR
/* xattr operations are optional and can safely be left unimplemented */
static int xmp_setxattr(const char *path, const char *name, const char *value,
			size_t size, int flags)
{
	int res = lsetxattr(path, name, value, size, flags);
	if (res == -1)
		return -errno;
	return 0;
}

static int xmp_getxattr(const char *path, const char *name, char *value,
			size_t size)
{
	int res = lgetxattr(path, name, value, size);
	if (res == -1)
		return -errno;
	return res;
}

static int xmp_listxattr(const char *path, char *list, size_t size)
{
	int res = llistxattr(path, list, size);
	if (res == -1)
		return -errno;
	return res;
}

static int xmp_removexattr(const char *path, const char *name)
{
	int res = lremovexattr(path, name);
	if (res == -1)
		return -errno;
	return 0;
}
#endif /* HAVE_SETXATTR */

static struct fuse_operations xmp_oper = {
	.getattr	= xmp_getattr,
	.access		= xmp_access,
	.readlink	= xmp_readlink,
	.readdir	= xmp_readdir,
	.mknod		= xmp_mknod,
	.mkdir		= xmp_mkdir,
	.symlink	= xmp_symlink,
	.unlink		= xmp_unlink,
	.rmdir		= xmp_rmdir,
	.rename		= xmp_rename,
	.link		= xmp_link,
	.chmod		= xmp_chmod,
	.chown		= xmp_chown,
	.truncate	= xmp_truncate,
	.utimens	= xmp_utimens,
	.open		= xmp_open,
	.read		= xmp_read,
	.write		= xmp_write,
	.statfs		= xmp_statfs,
	.release	= xmp_release,
	.fsync		= xmp_fsync,
#ifdef HAVE_SETXATTR
	.setxattr	= xmp_setxattr,
	.getxattr	= xmp_getxattr,
	.listxattr	= xmp_listxattr,
	.removexattr	= xmp_removexattr,
#endif
};

int main(int argc, char *argv[])
{
	umask(0);
	create_vm();
	return fuse_main(argc, argv, &xmp_oper, NULL);
}

