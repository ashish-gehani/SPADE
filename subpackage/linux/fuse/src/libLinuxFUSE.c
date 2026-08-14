/*
--------------------------------------------------------------------------------

FUSE: Filesystem in Userspace
Copyright (C) 2001-2007  Miklos Szeredi <miklos@szeredi.hu>

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
#ifdef HAVE_SETXATTR
#include <sys/xattr.h>
#endif

JavaVM* jvm;
JNIEnv* env;

jclass FUSEReporterClass;
jobject reporterInstance;

jmethodID readMethod;
jmethodID writeMethod;
jmethodID readlinkMethod;
jmethodID renameMethod;
jmethodID linkMethod;
jmethodID unlinkMethod;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *pvt) {
    jvm = vm;
    return JNI_VERSION_1_2;
}

static int default_getattr(const char *path, struct stat *stbuf) {
    int res;

    res = lstat(path, stbuf);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int default_access(const char *path, int mask) {
    int res;

    res = access(path, mask);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int spade_readlink(const char *path, char *buf, size_t size) {
    (*jvm)->AttachCurrentThread(jvm, (void**) &env, NULL);
    jstring jpath = (*env)->NewStringUTF(env, path);

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

    (*env)->CallVoidMethod(env, reporterInstance, readlinkMethod, fuse_get_context()->pid, iotime, jpath);

    return 0;
}

static int default_readdir(const char *path, void *buf, fuse_fill_dir_t filler, off_t offset, struct fuse_file_info *fi) {
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

static int default_mknod(const char *path, mode_t mode, dev_t rdev) {
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

static int default_mkdir(const char *path, mode_t mode) {
    int res;

    res = mkdir(path, mode);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int spade_unlink(const char *path) {
    (*jvm)->AttachCurrentThread(jvm, (void**) &env, NULL);
    jstring jpath = (*env)->NewStringUTF(env, path);

    int res;

    res = unlink(path);
    if (res == -1) {
        return -errno;
    }

    (*env)->CallVoidMethod(env, reporterInstance, unlinkMethod, fuse_get_context()->pid, jpath);

    return 0;
}

static int default_rmdir(const char *path) {
    int res;

    res = rmdir(path);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int spade_symlink(const char *from, const char *to) {
    (*jvm)->AttachCurrentThread(jvm, (void**) &env, NULL);
    jstring jpathOriginal = (*env)->NewStringUTF(env, from);
    jstring jpathLink = (*env)->NewStringUTF(env, to);

    int res;

    res = symlink(from, to);
    if (res == -1) {
        return -errno;
    }

    (*env)->CallVoidMethod(env, reporterInstance, linkMethod, fuse_get_context()->pid, jpathOriginal, jpathLink);

    return 0;
}

static int spade_rename(const char *from, const char *to) {
    (*jvm)->AttachCurrentThread(jvm, (void**) &env, NULL);
    jstring jpathOld = (*env)->NewStringUTF(env, from);
    jstring jpathNew = (*env)->NewStringUTF(env, to);

    int link;
    struct stat file_stat;
    lstat(from, &file_stat);
    if ((file_stat.st_mode & S_IFMT) == S_IFLNK) {
        link = 1;
    } else {
        link = 0;
    }

    int res;

    (*env)->CallVoidMethod(env, reporterInstance, renameMethod, fuse_get_context()->pid, 0, jpathOld, jpathNew, link, 0);

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

    (*env)->CallVoidMethod(env, reporterInstance, renameMethod, fuse_get_context()->pid, iotime, jpathOld, jpathNew, link, 1);

    return 0;
}

static int spade_link(const char *from, const char *to) {
    (*jvm)->AttachCurrentThread(jvm, (void**) &env, NULL);
    jstring jpathOriginal = (*env)->NewStringUTF(env, from);
    jstring jpathLink = (*env)->NewStringUTF(env, to);

    int res;

    res = link(from, to);
    if (res == -1) {
        return -errno;
    }

    (*env)->CallVoidMethod(env, reporterInstance, linkMethod, fuse_get_context()->pid, jpathOriginal, jpathLink);

    return 0;
}

static int default_chmod(const char *path, mode_t mode) {
    int res;

    res = chmod(path, mode);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int default_chown(const char *path, uid_t uid, gid_t gid) {
    int res;

    res = lchown(path, uid, gid);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int spade_truncate(const char *path, off_t size) {
    (*jvm)->AttachCurrentThread(jvm, (void**) &env, NULL);
    jstring jpath = (*env)->NewStringUTF(env, path);

    int link;
    struct stat file_stat;
    lstat(path, &file_stat);
    if ((file_stat.st_mode & S_IFMT) == S_IFLNK) {
        link = 1;
    } else {
        link = 0;
    }

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

    (*env)->CallVoidMethod(env, reporterInstance, writeMethod, fuse_get_context()->pid, iotime, jpath, link);

    return 0;
}

static int default_utimens(const char *path, const struct timespec ts[2]) {
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

static int default_open(const char *path, struct fuse_file_info *fi) {
    int res;

    res = open(path, fi->flags);
    if (res == -1) {
        return -errno;
    }

    close(res);
    return 0;
}

static int spade_read(const char *path, char *buf, size_t size, off_t offset, struct fuse_file_info *fi) {
    (*jvm)->AttachCurrentThread(jvm, (void**) &env, NULL);
    jstring jpath = (*env)->NewStringUTF(env, path);

    int link;
    struct stat file_stat;
    lstat(path, &file_stat);
    if ((file_stat.st_mode & S_IFMT) == S_IFLNK) {
        link = 1;
    } else {
        link = 0;
    }

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

    (*env)->CallVoidMethod(env, reporterInstance, readMethod, fuse_get_context()->pid, iotime, jpath, link);

    return res;
}

static int spade_write(const char *path, const char *buf, size_t size, off_t offset, struct fuse_file_info *fi) {
    (*jvm)->AttachCurrentThread(jvm, (void**) &env, NULL);
    jstring jpath = (*env)->NewStringUTF(env, path);

    int link;
    struct stat file_stat;
    lstat(path, &file_stat);
    if ((file_stat.st_mode & S_IFMT) == S_IFLNK) {
        link = 1;
    } else {
        link = 0;
    }

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

    (*env)->CallVoidMethod(env, reporterInstance, writeMethod, fuse_get_context()->pid, iotime, jpath, link);

    return res;
}

static int default_statfs(const char *path, struct statvfs *stbuf) {
    int res;

    res = statvfs(path, stbuf);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int default_release(const char *path, struct fuse_file_info *fi) {
    /* Just a stub.	 This method is optional and can safely be left
       unimplemented */

    (void) path;
    (void) fi;
    return 0;
}

static int default_fsync(const char *path, int isdatasync, struct fuse_file_info *fi) {
    /* Just a stub.	 This method is optional and can safely be left
       unimplemented */

    (void) path;
    (void) isdatasync;
    (void) fi;
    return 0;
}

#ifdef HAVE_SETXATTR

/* xattr operations are optional and can safely be left unimplemented */
static int default_setxattr(const char *path, const char *name, const char *value,
        size_t size, int flags) {
    int res = lsetxattr(path, name, value, size, flags);
    if (res == -1)
        return -errno;
    return 0;
}

static int default_getxattr(const char *path, const char *name, char *value,
        size_t size) {
    int res = lgetxattr(path, name, value, size);
    if (res == -1)
        return -errno;
    return res;
}

static int default_listxattr(const char *path, char *list, size_t size) {
    int res = llistxattr(path, list, size);
    if (res == -1)
        return -errno;
    return res;
}

static int default_removexattr(const char *path, const char *name) {
    int res = lremovexattr(path, name);
    if (res == -1)
        return -errno;
    return 0;
}
#endif /* HAVE_SETXATTR */

static struct fuse_operations spade_oper = {
    .getattr = default_getattr,
    .access = default_access,
    .readlink = spade_readlink,
    .readdir = default_readdir,
    .mknod = default_mknod,
    .mkdir = default_mkdir,
    .symlink = spade_symlink,
    .unlink = spade_unlink,
    .rmdir = default_rmdir,
    .rename = spade_rename,
    .link = spade_link,
    .chmod = default_chmod,
    .chown = default_chown,
    .truncate = spade_truncate,
    .utimens = default_utimens,
    .open = default_open,
    .read = spade_read,
    .write = spade_write,
    .statfs = default_statfs,
    .release = default_release,
    .fsync = default_fsync,
#ifdef HAVE_SETXATTR
    .setxattr = default_setxattr,
    .getxattr = default_getxattr,
    .listxattr = default_listxattr,
    .removexattr = default_removexattr,
#endif
};

JNIEXPORT jint JNICALL Java_spade_reporter_LinuxFUSE_launchFUSE(JNIEnv *e, jobject o, jstring mountPoint) {
    reporterInstance = o;
    env = e;

    FUSEReporterClass = (*env)->FindClass(env, "spade/reporter/LinuxFUSE");
    readMethod = (*env)->GetMethodID(env, FUSEReporterClass, "read", "(IILjava/lang/String;I)V");
    writeMethod = (*env)->GetMethodID(env, FUSEReporterClass, "write", "(IILjava/lang/String;I)V");
    readlinkMethod = (*env)->GetMethodID(env, FUSEReporterClass, "readlink", "(IILjava/lang/String;)V");
    renameMethod = (*env)->GetMethodID(env, FUSEReporterClass, "rename", "(IILjava/lang/String;Ljava/lang/String;II)V");
    linkMethod = (*env)->GetMethodID(env, FUSEReporterClass, "link", "(ILjava/lang/String;Ljava/lang/String;)V");
    unlinkMethod = (*env)->GetMethodID(env, FUSEReporterClass, "unlink", "(ILjava/lang/String;)V");

    int argc = 4;
    char *argv[argc];
    argv[0] = "libLinuxFUSE";
    argv[1] = "-f";
    argv[2] = "-s";
    argv[3] = (char*) (*env)->GetStringUTFChars(env, mountPoint, NULL);
    // argv[4] = "-oallow_other";

    umask(0);
    return fuse_main(argc, argv, &spade_oper, NULL);
}
