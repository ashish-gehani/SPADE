/*
--------------------------------------------------------------------------------

FUSE: Filesystem in Userspace
Copyright (C) 2001-2007  Miklos Szeredi <miklos@szeredi.hu>

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

 Compile with:
 
 javah -o spade/reporter/spade_reporter_LinuxFUSE.h spade.reporter.LinuxFUSE
 gcc -shared -Wl,-soname,libjfuse.so -I/usr/java/jdk1.6.0_21/include -I/usr/java/jdk1.6.0_21/include/linux -L. -ljvm -Wall `pkg-config fuse --cflags --libs` spade/reporter/spade_reporter_FUSE.c -o libjfuse.so

--------------------------------------------------------------------------------
*/

#define FUSE_USE_VERSION 26

#include "spade_reporter_LinuxFUSE.h"

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

JavaVM* jvm;
JNIEnv* env;

jclass FUSEReporterClass;
jobject reporterInstance;

jmethodID readwriteMethod;
jmethodID renameMethod;
jmethodID linkMethod;
jmethodID unlinkMethod;
jmethodID shutdownMethod;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *pvt) {
    jvm = vm;
    return JNI_VERSION_1_2;
}

static int make_java_call(char method, int pid, int var, const char *path1, const char *path2) {
    (*jvm)->AttachCurrentThread(jvm, (void**) &env, NULL);
    jstring jpath1 = (*env)->NewStringUTF(env, path1);
    jstring jpath2 = (*env)->NewStringUTF(env, path2);

    switch (method) {
        case 'r': // read
            (*env)->CallVoidMethod(env, reporterInstance, readwriteMethod, 0, pid, var, jpath1);
            break;
        case 'w': // write
            (*env)->CallVoidMethod(env, reporterInstance, readwriteMethod, 1, pid, var, jpath1);
            break;
        case 'n': // rename
            (*env)->CallVoidMethod(env, reporterInstance, renameMethod, pid, var, jpath1, jpath2);
            break;
        case 'l': // link
            (*env)->CallVoidMethod(env, reporterInstance, linkMethod, pid, jpath1, jpath2);
            break;
        case 'u': // link
            (*env)->CallVoidMethod(env, reporterInstance, unlinkMethod, pid, jpath1);
            break;
    }

    return 0;
}

static int xmp_getattr(const char *path, struct stat *stbuf) {
    int res;

    res = lstat(path, stbuf);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int xmp_access(const char *path, int mask) {
    int res;

    res = access(path, mask);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int xmp_readlink(const char *path, char *buf, size_t size) {
    int res;

    struct timeval starttime, endtime;
    long seconds, useconds, mtime;
    gettimeofday(&starttime, NULL);

    res = readlink(path, buf, size - 1);
    if (res == -1) {
        return -errno;
    }
    buf[res] = '\0';

    gettimeofday(&endtime, NULL);
    seconds = endtime.tv_sec - starttime.tv_sec;
    useconds = endtime.tv_usec - starttime.tv_usec;
    mtime = (seconds * 1000000 + useconds);
    int iotime = mtime;

    make_java_call('r', fuse_get_context()->pid, iotime, path, "");

    return 0;
}

static int xmp_readdir(const char *path, void *buf, fuse_fill_dir_t filler, off_t offset, struct fuse_file_info *fi) {
    DIR *dp;
    struct dirent *de;

    (void) offset;
    (void) fi;

    dp = opendir(path);
    if (dp == NULL) {
        return -errno;
    }

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
        if (res >= 0) {
            res = close(res);
        }
    } else if (S_ISFIFO(mode)) {
        res = mkfifo(path, mode);
    } else {
        res = mknod(path, mode, rdev);
    }

    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int xmp_mkdir(const char *path, mode_t mode) {
    int res;

    res = mkdir(path, mode);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int xmp_unlink(const char *path) {
    int res;

    res = unlink(path);
    if (res == -1) {
        return -errno;
    }

    make_java_call('u', fuse_get_context()->pid, 0, path, "");

    return 0;
}

static int xmp_rmdir(const char *path) {
    int res;

    res = rmdir(path);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int xmp_symlink(const char *from, const char *to) {
    int res;

    res = symlink(from, to);
    if (res == -1) {
        return -errno;
    }

    make_java_call('r', fuse_get_context()->pid, 0, from, "");
    make_java_call('w', fuse_get_context()->pid, 0, to, "");
    make_java_call('l', fuse_get_context()->pid, 0, from, to);

    return 0;
}

static int xmp_rename(const char *from, const char *to) {
    int res;

    struct timeval starttime, endtime;
    long seconds, useconds, mtime;
    gettimeofday(&starttime, NULL);

    res = rename(from, to);
    if (res == -1) {
        return -errno;
    }

    gettimeofday(&endtime, NULL);
    seconds = endtime.tv_sec - starttime.tv_sec;
    useconds = endtime.tv_usec - starttime.tv_usec;
    mtime = (seconds * 1000000 + useconds);
    int iotime = mtime;

    make_java_call('r', fuse_get_context()->pid, 0, from, "");
    make_java_call('w', fuse_get_context()->pid, 0, to, "");
    make_java_call('n', fuse_get_context()->pid, iotime, from, to);

    return 0;
}

static int xmp_link(const char *from, const char *to) {
    int res;

    res = link(from, to);
    if (res == -1) {
        return -errno;
    }

    make_java_call('r', fuse_get_context()->pid, 0, from, "");
    make_java_call('w', fuse_get_context()->pid, 0, to, "");
    make_java_call('l', fuse_get_context()->pid, 0, from, to);

    return 0;
}

static int xmp_chmod(const char *path, mode_t mode) {
    int res;

    res = chmod(path, mode);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int xmp_chown(const char *path, uid_t uid, gid_t gid) {
    int res;

    res = lchown(path, uid, gid);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int xmp_truncate(const char *path, off_t size) {
    int res;

    struct timeval starttime, endtime;
    long seconds, useconds, mtime;
    gettimeofday(&starttime, NULL);

    res = truncate(path, size);
    if (res == -1) {
        return -errno;
    }

    gettimeofday(&endtime, NULL);
    seconds = endtime.tv_sec - starttime.tv_sec;
    useconds = endtime.tv_usec - starttime.tv_usec;
    mtime = (seconds * 1000000 + useconds);
    int iotime = mtime;

    make_java_call('w', fuse_get_context()->pid, iotime, path, "");

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
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int xmp_open(const char *path, struct fuse_file_info *fi) {
    int res;

    res = open(path, fi->flags);
    if (res == -1) {
        return -errno;
    }

    close(res);
    return 0;
}

static int xmp_read(const char *path, char *buf, size_t size, off_t offset, struct fuse_file_info *fi) {
    int fd;
    int res;

    struct timeval starttime, endtime;
    long seconds, useconds, mtime;
    gettimeofday(&starttime, NULL);

    (void) fi;
    fd = open(path, O_RDONLY);
    if (fd == -1) {
        return -errno;
    }

    res = pread(fd, buf, size, offset);
    if (res == -1) {
        res = -errno;
    }

    close(fd);

    gettimeofday(&endtime, NULL);
    seconds = endtime.tv_sec - starttime.tv_sec;
    useconds = endtime.tv_usec - starttime.tv_usec;
    mtime = (seconds * 1000000 + useconds);
    int iotime = mtime;

    make_java_call('r', fuse_get_context()->pid, iotime, path, "");

    return res;
}

static int xmp_write(const char *path, const char *buf, size_t size, off_t offset, struct fuse_file_info *fi) {
    int fd;
    int res;

    struct timeval starttime, endtime;
    long seconds, useconds, mtime;
    gettimeofday(&starttime, NULL);

    (void) fi;
    fd = open(path, O_WRONLY);
    if (fd == -1) {
        return -errno;
    }

    res = pwrite(fd, buf, size, offset);
    if (res == -1) {
        res = -errno;
    }

    close(fd);

    gettimeofday(&endtime, NULL);
    seconds = endtime.tv_sec - starttime.tv_sec;
    useconds = endtime.tv_usec - starttime.tv_usec;
    mtime = (seconds * 1000000 + useconds);
    int iotime = mtime;

    make_java_call('w', fuse_get_context()->pid, iotime, path, "");

    return res;
}

static int xmp_statfs(const char *path, struct statvfs *stbuf) {
    int res;

    res = statvfs(path, stbuf);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int xmp_release(const char *path, struct fuse_file_info *fi) {
    /* Just a stub.	 This method is optional and can safely be left
       unimplemented */

    (void) path;
    (void) fi;
    return 0;
}

static int xmp_fsync(const char *path, int isdatasync, struct fuse_file_info *fi) {
    /* Just a stub.	 This method is optional and can safely be left
       unimplemented */

    (void) path;
    (void) isdatasync;
    (void) fi;
    return 0;
}

static struct fuse_operations xmp_oper = {
    .getattr = xmp_getattr,
    .access = xmp_access,
    .readdir = xmp_readdir,
    .mknod = xmp_mknod,
    .mkdir = xmp_mkdir,
    .rmdir = xmp_rmdir,
    .chmod = xmp_chmod,
    .chown = xmp_chown,
    .utimens = xmp_utimens,
    .open = xmp_open,
    .statfs = xmp_statfs,
    .release = xmp_release,
    .fsync = xmp_fsync,
    .link = xmp_link,
    .symlink = xmp_symlink,
    .readlink = xmp_readlink,
    .unlink = xmp_unlink,
    .read = xmp_read,
    .write = xmp_write,
    .rename = xmp_rename,
    .truncate = xmp_truncate
};

JNIEXPORT jint JNICALL Java_spade_reporter_LinuxFUSE_launchFUSE(JNIEnv *e, jobject o, jstring mountPoint) {
    reporterInstance = o;
    env = e;

    FUSEReporterClass = (*env)->FindClass(env, "spade/reporter/LinuxFUSE");
    readwriteMethod = (*env)->GetMethodID(env, FUSEReporterClass, "readwrite", "(IIILjava/lang/String;)V");
    renameMethod = (*env)->GetMethodID(env, FUSEReporterClass, "rename", "(IILjava/lang/String;Ljava/lang/String;)V");
    linkMethod = (*env)->GetMethodID(env, FUSEReporterClass, "link", "(ILjava/lang/String;Ljava/lang/String;)V");
    unlinkMethod = (*env)->GetMethodID(env, FUSEReporterClass, "unlink", "(ILjava/lang/String;)V");

    int argc = 4;
    char *argv[4];
    argv[0] = "spade_reporter_LinuxFUSE";
    argv[1] = "-f";
    argv[2] = "-s";
    argv[3] = (*env)->GetStringUTFChars(env, mountPoint, NULL);

    umask(0);
    return fuse_main(argc, argv, &xmp_oper, NULL);
}
