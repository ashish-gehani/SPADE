/*
  FUSE: Filesystem in Userspace
  Copyright (C) 2001-2007  Miklos Szeredi <miklos@szeredi.hu>

  This program can be distributed under the terms of the GNU GPL.
  See the file COPYING.

--------------------------------------------------------------------------------
SPADE - Support for Provenance Auditing in Distributed Environments.
Copyright (C) 2011 SRI International

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

#define FUSE_USE_VERSION 26

#include "FUSEProducer.h"

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

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *pvt) {
    jvm = vm;
    return JNI_VERSION_1_2;
}

static int make_java_call(char method, int pid, int uid, int gid, const char *path1, const char *path2) {
    (*jvm)->AttachCurrentThread(jvm, (void**) &env, NULL);
    jstring jpath1 = (*env)->NewStringUTF(env, path1);
    jstring jpath2 = (*env)->NewStringUTF(env, path2);

    switch (method) {
        case 'r': // read
            (*env)->CallVoidMethod(env, producerInstance, readwriteMethod, 0, pid, uid, gid, jpath1);
            break;
        case 'w': // write
            (*env)->CallVoidMethod(env, producerInstance, readwriteMethod, 1, pid, uid, gid, jpath1);
            break;
        case 'n': // rename
            (*env)->CallVoidMethod(env, producerInstance, renameMethod, pid, uid, gid, jpath1, jpath2);
            break;
        case 'l': // link
            (*env)->CallVoidMethod(env, producerInstance, linkMethod, pid, uid, gid, jpath1, jpath2);
            break;
    }

    return 0;
}

static int xmp_getattr(const char *path, struct stat *stbuf) {
    int res;

    res = lstat(path, stbuf);
    if (res == -1)
        return -errno;

    return 0;
}

static int xmp_access(const char *path, int mask) {
    int res;

    res = access(path, mask);
    if (res == -1)
        return -errno;

    return 0;
}

static int xmp_readlink(const char *path, char *buf, size_t size) {
    int res;

    res = readlink(path, buf, size - 1);
    if (res == -1)
        return -errno;

    buf[res] = '\0';
    return 0;
}

static int xmp_readdir(const char *path, void *buf, fuse_fill_dir_t filler,
        off_t offset, struct fuse_file_info *fi) {
    DIR *dp;
    struct dirent *de;

    (void) offset;
    (void) fi;

    dp = opendir(path);
    if (dp == NULL)
        return -errno;

    while ((de = readdir(dp)) != NULL) {
        struct stat st;
        memset(&st, 0, sizeof (st));
        st.st_ino = de->d_ino;
        st.st_mode = de->d_type << 12;
        if (filler(buf, de->d_name, &st, 0))
            break;
    }

    closedir(dp);

    return 0;
}

static int xmp_mknod(const char *path, mode_t mode, dev_t rdev) {
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

static int xmp_mkdir(const char *path, mode_t mode) {
    int res;

    res = mkdir(path, mode);
    if (res == -1)
        return -errno;

    return 0;
}

static int xmp_unlink(const char *path) {
    int res;

    res = unlink(path);
    if (res == -1)
        return -errno;

    return 0;
}

static int xmp_rmdir(const char *path) {
    int res;

    res = rmdir(path);
    if (res == -1)
        return -errno;

    return 0;
}

static int xmp_symlink(const char *from, const char *to) {
    int res;

    res = symlink(from, to);
    if (res == -1)
        return -errno;

    make_java_call('l', fuse_get_context()->pid, 0, 0, from, to);

    return 0;
}

static int xmp_rename(const char *from, const char *to) {
    int res;

    struct timeval starttime, endtime;
    long seconds, useconds, mtime;
    gettimeofday(&starttime, NULL);

    make_java_call('r', fuse_get_context()->pid, 0, 0, from, "");
    res = rename(from, to);
    if (res == -1)
        return -errno;

    gettimeofday(&endtime, NULL);
    seconds = endtime.tv_sec - starttime.tv_sec;
    useconds = endtime.tv_usec - starttime.tv_usec;
    mtime = (seconds * 1000000 + useconds);
    int iotime = mtime;

    make_java_call('w', fuse_get_context()->pid, 0, 0, to, "");
    make_java_call('n', fuse_get_context()->pid, iotime, 0, from, to);

    return 0;
}

static int xmp_link(const char *from, const char *to) {
    int res;

    res = link(from, to);
    if (res == -1)
        return -errno;

    make_java_call('l', fuse_get_context()->pid, 0, 0, from, to);

    return 0;
}

static int xmp_chmod(const char *path, mode_t mode) {
    int res;

    res = chmod(path, mode);
    if (res == -1)
        return -errno;

    return 0;
}

static int xmp_chown(const char *path, uid_t uid, gid_t gid) {
    int res;

    res = lchown(path, uid, gid);
    if (res == -1)
        return -errno;

    return 0;
}

static int xmp_truncate(const char *path, off_t size) {
    int res;

    struct timeval starttime, endtime;
    long seconds, useconds, mtime;
    gettimeofday(&starttime, NULL);

    res = truncate(path, size);
    if (res == -1)
        return -errno;

    gettimeofday(&endtime, NULL);
    seconds = endtime.tv_sec - starttime.tv_sec;
    useconds = endtime.tv_usec - starttime.tv_usec;
    mtime = (seconds * 1000000 + useconds);
    int iotime = mtime;

    make_java_call('w', fuse_get_context()->pid, iotime, 0, path, "");

    return 0;
}

static int xmp_utimens(const char *path, const struct timespec ts[2]) {
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

static int xmp_open(const char *path, struct fuse_file_info *fi) {
    int res;

    res = open(path, fi->flags);
    if (res == -1)
        return -errno;
    /*
            if ((fi->flags & O_RDONLY) == O_RDONLY)
                    make_java_call('r', fuse_get_context()->pid, 0, 0, path, "");
            else
                    make_java_call('w', fuse_get_context()->pid, 0, 0, path, "");
     */
    close(res);
    return 0;
}

static int xmp_read(const char *path, char *buf, size_t size, off_t offset,
        struct fuse_file_info *fi) {
    int fd;
    int res;

    struct timeval starttime, endtime;
    long seconds, useconds, mtime;
    gettimeofday(&starttime, NULL);

    (void) fi;
    fd = open(path, O_RDONLY);
    if (fd == -1)
        return -errno;

    res = pread(fd, buf, size, offset);
    if (res == -1)
        res = -errno;

    close(fd);

    gettimeofday(&endtime, NULL);
    seconds = endtime.tv_sec - starttime.tv_sec;
    useconds = endtime.tv_usec - starttime.tv_usec;
    mtime = (seconds * 1000000 + useconds);
    int iotime = mtime;

    make_java_call('r', fuse_get_context()->pid, iotime, 0, path, "");

    return res;
}

static int xmp_write(const char *path, const char *buf, size_t size,
        off_t offset, struct fuse_file_info *fi) {
    int fd;
    int res;

    struct timeval starttime, endtime;
    long seconds, useconds, mtime;
    gettimeofday(&starttime, NULL);

    (void) fi;
    fd = open(path, O_WRONLY);
    if (fd == -1)
        return -errno;

    res = pwrite(fd, buf, size, offset);
    if (res == -1)
        res = -errno;

    close(fd);

    gettimeofday(&endtime, NULL);
    seconds = endtime.tv_sec - starttime.tv_sec;
    useconds = endtime.tv_usec - starttime.tv_usec;
    mtime = (seconds * 1000000 + useconds);
    int iotime = mtime;

    make_java_call('w', fuse_get_context()->pid, iotime, 0, path, "");

    return res;
}

static int xmp_statfs(const char *path, struct statvfs *stbuf) {
    int res;

    res = statvfs(path, stbuf);
    if (res == -1)
        return -errno;

    return 0;
}

static int xmp_release(const char *path, struct fuse_file_info *fi) {
    /* Just a stub.	 This method is optional and can safely be left
       unimplemented */

    (void) path;
    (void) fi;
    return 0;
}

static int xmp_fsync(const char *path, int isdatasync,
        struct fuse_file_info *fi) {
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
        size_t size, int flags) {
    int res = lsetxattr(path, name, value, size, flags);
    if (res == -1)
        return -errno;
    return 0;
}

static int xmp_getxattr(const char *path, const char *name, char *value,
        size_t size) {
    int res = lgetxattr(path, name, value, size);
    if (res == -1)
        return -errno;
    return res;
}

static int xmp_listxattr(const char *path, char *list, size_t size) {
    int res = llistxattr(path, list, size);
    if (res == -1)
        return -errno;
    return res;
}

static int xmp_removexattr(const char *path, const char *name) {
    int res = lremovexattr(path, name);
    if (res == -1)
        return -errno;
    return 0;
}
#endif /* HAVE_SETXATTR */

static struct fuse_operations xmp_oper = {
    .getattr = xmp_getattr,
    .access = xmp_access,
    .readlink = xmp_readlink,
    .readdir = xmp_readdir,
    .mknod = xmp_mknod,
    .mkdir = xmp_mkdir,
    .symlink = xmp_symlink,
    .unlink = xmp_unlink,
    .rmdir = xmp_rmdir,
    .rename = xmp_rename,
    .link = xmp_link,
    .chmod = xmp_chmod,
    .chown = xmp_chown,
    .truncate = xmp_truncate,
    .utimens = xmp_utimens,
    .open = xmp_open,
    .read = xmp_read,
    .write = xmp_write,
    .statfs = xmp_statfs,
    .release = xmp_release,
    .fsync = xmp_fsync,
#ifdef HAVE_SETXATTR
    .setxattr = xmp_setxattr,
    .getxattr = xmp_getxattr,
    .listxattr = xmp_listxattr,
    .removexattr = xmp_removexattr,
#endif
};

JNIEXPORT jint JNICALL Java_FUSEProducer_launchFUSE(JNIEnv *e, jobject o) {
    producerInstance = o;
    env = e;

    FUSEProducerClass = (*env)->FindClass(env, "FUSEProducer");
    initMethod = (*env)->GetMethodID(env, FUSEProducerClass, "<init>", "()V");
    readwriteMethod = (*env)->GetMethodID(env, FUSEProducerClass, "readwrite", "(IIIILjava/lang/String;)V");
    renameMethod = (*env)->GetMethodID(env, FUSEProducerClass, "rename", "(IIILjava/lang/String;Ljava/lang/String;)V");
    linkMethod = (*env)->GetMethodID(env, FUSEProducerClass, "link", "(IIILjava/lang/String;Ljava/lang/String;)V");

    int argc = 4;
    char *argv[4];
    argv[0] = "fusexmp";
    argv[1] = "-f";
    argv[2] = "-s";
    argv[3] = "test";

    umask(0);
    return fuse_main(argc, argv, &xmp_oper, NULL);
}

