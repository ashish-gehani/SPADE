/*
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

#include "libMacFUSE.h"

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

JNIEXPORT jint JNICALL Java_spade_reporter_MacFUSE_launchFUSE(JNIEnv *e, jobject o, jstring mountPoint) {
    reporterInstance = o;
    env = e;

    FUSEReporterClass = (*env)->FindClass(env, "spade/reporter/MacFUSE");
    readwriteMethod = (*env)->GetMethodID(env, FUSEReporterClass, "readwrite", "(IIILjava/lang/String;)V");
    renameMethod = (*env)->GetMethodID(env, FUSEReporterClass, "rename", "(IILjava/lang/String;Ljava/lang/String;)V");
    linkMethod = (*env)->GetMethodID(env, FUSEReporterClass, "link", "(ILjava/lang/String;Ljava/lang/String;)V");
    unlinkMethod = (*env)->GetMethodID(env, FUSEReporterClass, "unlink", "(ILjava/lang/String;)V");

    int argc = 4;
    char *argv[4];
    argv[0] = "libMacFUSE";
    argv[1] = "-f";
    argv[2] = "-s";
    argv[3] = (char*)(*env)->GetStringUTFChars(env, mountPoint, NULL);

    umask(0);
    return fuse_main(argc, argv, &xmp_oper, NULL);
}


/*
 
#include <AvailabilityMacros.h>

#if !defined(AVAILABLE_MAC_OS_X_VERSION_10_5_AND_LATER)
#error "This file system requires Leopard and above."
#endif

#define FUSE_USE_VERSION 26

#define _GNU_SOURCE

#include <fuse.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <errno.h>
#include <sys/time.h>
#include <sys/xattr.h>
#include <sys/attr.h>
#include <sys/param.h>

#if defined(_POSIX_C_SOURCE)
typedef unsigned char  u_char;
typedef unsigned short u_short;
typedef unsigned int   u_int;
typedef unsigned long  u_long;
#endif

#define G_PREFIX                       "org"
#define G_KAUTH_FILESEC_XATTR G_PREFIX ".apple.system.Security"
#define A_PREFIX                       "com"
#define A_KAUTH_FILESEC_XATTR A_PREFIX ".apple.system.Security"
#define XATTR_APPLE_PREFIX             "com.apple."

static int
loopback_getattr(const char *path, struct stat *stbuf)
{
    int res;

    res = lstat(path, stbuf);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
loopback_fgetattr(const char *path, struct stat *stbuf,
                  struct fuse_file_info *fi)
{
    int res;

    (void)path;

    res = fstat(fi->fh, stbuf);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
loopback_readlink(const char *path, char *buf, size_t size)
{
    int res;

    res = readlink(path, buf, size - 1);
    if (res == -1) {
        return -errno;
    }

    buf[res] = '\0';

    return 0;
}

struct loopback_dirp {
    DIR *dp;
    struct dirent *entry;
    off_t offset;
};

static int
loopback_opendir(const char *path, struct fuse_file_info *fi)
{
    int res;

    struct loopback_dirp *d = malloc(sizeof(struct loopback_dirp));
    if (d == NULL) {
        return -ENOMEM;
    }

    d->dp = opendir(path);
    if (d->dp == NULL) {
        res = -errno;
        free(d);
        return res;
    }

    d->offset = 0;
    d->entry = NULL;

    fi->fh = (unsigned long)d;

    return 0;
}

static inline struct loopback_dirp *
get_dirp(struct fuse_file_info *fi)
{
    return (struct loopback_dirp *)(uintptr_t)fi->fh;
}

static int
loopback_readdir(const char *path, void *buf, fuse_fill_dir_t filler,
                 off_t offset, struct fuse_file_info *fi)
{
    struct loopback_dirp *d = get_dirp(fi);

    (void)path;

    if (offset != d->offset) {
        seekdir(d->dp, offset);
        d->entry = NULL;
        d->offset = offset;
    }

    while (1) {
        struct stat st;
        off_t nextoff;

        if (!d->entry) {
            d->entry = readdir(d->dp);
            if (!d->entry) {
                break;
            }
        }

        memset(&st, 0, sizeof(st));
        st.st_ino = d->entry->d_ino;
        st.st_mode = d->entry->d_type << 12;
        nextoff = telldir(d->dp);
        if (filler(buf, d->entry->d_name, &st, nextoff)) {
            break;
        }

        d->entry = NULL;
        d->offset = nextoff;
    }

    return 0;
}

static int
loopback_releasedir(const char *path, struct fuse_file_info *fi)
{
    struct loopback_dirp *d = get_dirp(fi);

    (void)path;

    closedir(d->dp);
    free(d);

    return 0;
}

static int
loopback_mknod(const char *path, mode_t mode, dev_t rdev)
{
    int res;

    if (S_ISFIFO(mode)) {
        res = mkfifo(path, mode);
    } else {
        res = mknod(path, mode, rdev);
    }

    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
loopback_mkdir(const char *path, mode_t mode)
{
    int res;

    res = mkdir(path, mode);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
loopback_unlink(const char *path)
{
    int res;

    res = unlink(path);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
loopback_rmdir(const char *path)
{
    int res;

    res = rmdir(path);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
loopback_symlink(const char *from, const char *to)
{
    int res;

    res = symlink(from, to);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
loopback_rename(const char *from, const char *to)
{
    int res;

    res = rename(from, to);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
loopback_exchange(const char *path1, const char *path2, unsigned long options)
{
    int res;

    res = exchangedata(path1, path2, options);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
loopback_link(const char *from, const char *to)
{
    int res;

    res = link(from, to);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
loopback_fsetattr_x(const char *path, struct setattr_x *attr,
                    struct fuse_file_info *fi)
{
    int res;
    uid_t uid = -1;
    gid_t gid = -1;

    if (SETATTR_WANTS_MODE(attr)) {
        res = lchmod(path, attr->mode);
        if (res == -1) {
            return -errno;
        }
    }

    if (SETATTR_WANTS_UID(attr)) {
        uid = attr->uid;
    }

    if (SETATTR_WANTS_GID(attr)) {
        gid = attr->gid;
    }

    if ((uid != -1) || (gid != -1)) {
        res = lchown(path, uid, gid);
        if (res == -1) {
            return -errno;
        }
    }

    if (SETATTR_WANTS_SIZE(attr)) {
        if (fi) {
            res = ftruncate(fi->fh, attr->size);
        } else {
            res = truncate(path, attr->size);
        }
        if (res == -1) {
            return -errno;
        }
    }

    if (SETATTR_WANTS_MODTIME(attr)) {
        struct timeval tv[2];
        if (!SETATTR_WANTS_ACCTIME(attr)) {
            gettimeofday(&tv[0], NULL);
        } else {
            tv[0].tv_sec = attr->acctime.tv_sec;
            tv[0].tv_usec = attr->acctime.tv_nsec / 1000;
        }
        tv[1].tv_sec = attr->modtime.tv_sec;
        tv[1].tv_usec = attr->modtime.tv_nsec / 1000;
        res = utimes(path, tv);
        if (res == -1) {
            return -errno;
        }
    }

    if (SETATTR_WANTS_CRTIME(attr)) {
        struct attrlist attributes;

        attributes.bitmapcount = ATTR_BIT_MAP_COUNT;
        attributes.reserved = 0;
        attributes.commonattr = ATTR_CMN_CRTIME;
        attributes.dirattr = 0;
        attributes.fileattr = 0;
        attributes.forkattr = 0;
        attributes.volattr = 0;

        res = setattrlist(path, &attributes, &attr->crtime,
                  sizeof(struct timespec), FSOPT_NOFOLLOW);

        if (res == -1) {
            return -errno;
        }
    }

    if (SETATTR_WANTS_CHGTIME(attr)) {
        struct attrlist attributes;

        attributes.bitmapcount = ATTR_BIT_MAP_COUNT;
        attributes.reserved = 0;
        attributes.commonattr = ATTR_CMN_CHGTIME;
        attributes.dirattr = 0;
        attributes.fileattr = 0;
        attributes.forkattr = 0;
        attributes.volattr = 0;

        res = setattrlist(path, &attributes, &attr->chgtime,
                  sizeof(struct timespec), FSOPT_NOFOLLOW);

        if (res == -1) {
            return -errno;
        }
    }

    if (SETATTR_WANTS_BKUPTIME(attr)) {
        struct attrlist attributes;

        attributes.bitmapcount = ATTR_BIT_MAP_COUNT;
        attributes.reserved = 0;
        attributes.commonattr = ATTR_CMN_BKUPTIME;
        attributes.dirattr = 0;
        attributes.fileattr = 0;
        attributes.forkattr = 0;
        attributes.volattr = 0;

        res = setattrlist(path, &attributes, &attr->bkuptime,
                  sizeof(struct timespec), FSOPT_NOFOLLOW);

        if (res == -1) {
            return -errno;
        }
    }

    if (SETATTR_WANTS_FLAGS(attr)) {
        res = lchflags(path, attr->flags);
        if (res == -1) {
            return -errno;
        }
    }

    return 0;
}

static int
loopback_setattr_x(const char *path, struct setattr_x *attr)
{
    return loopback_fsetattr_x(path, attr, (struct fuse_file_info *)0);
}

static int
loopback_getxtimes(const char *path, struct timespec *bkuptime,
                   struct timespec *crtime)
{
    int res = 0;
    struct attrlist attributes;

    attributes.bitmapcount = ATTR_BIT_MAP_COUNT;
    attributes.reserved    = 0;
    attributes.commonattr  = 0;
    attributes.dirattr     = 0;
    attributes.fileattr    = 0;
    attributes.forkattr    = 0;
    attributes.volattr     = 0;



    struct xtimeattrbuf {
        uint32_t size;
        struct timespec xtime;
    } __attribute__ ((packed));


    struct xtimeattrbuf buf;

    attributes.commonattr = ATTR_CMN_BKUPTIME;
    res = getattrlist(path, &attributes, &buf, sizeof(buf), FSOPT_NOFOLLOW);
    if (res == 0) {
        (void)memcpy(bkuptime, &(buf.xtime), sizeof(struct timespec));
    } else {
        (void)memset(bkuptime, 0, sizeof(struct timespec));
    }

    attributes.commonattr = ATTR_CMN_CRTIME;
    res = getattrlist(path, &attributes, &buf, sizeof(buf), FSOPT_NOFOLLOW);
    if (res == 0) {
        (void)memcpy(crtime, &(buf.xtime), sizeof(struct timespec));
    } else {
        (void)memset(crtime, 0, sizeof(struct timespec));
    }

    return 0;
}

static int
loopback_create(const char *path, mode_t mode, struct fuse_file_info *fi)
{
    int fd;

    fd = open(path, fi->flags, mode);
    if (fd == -1) {
        return -errno;
    }

    fi->fh = fd;
    return 0;
}

static int
loopback_open(const char *path, struct fuse_file_info *fi)
{
    int fd;

    fd = open(path, fi->flags);
    if (fd == -1) {
        return -errno;
    }

    fi->fh = fd;
    return 0;
}

static int
loopback_read(const char *path, char *buf, size_t size, off_t offset,
              struct fuse_file_info *fi)
{
    int res;

    (void)path;
    res = pread(fi->fh, buf, size, offset);
    if (res == -1) {
        res = -errno;
    }

    return res;
}

static int
loopback_write(const char *path, const char *buf, size_t size,
               off_t offset, struct fuse_file_info *fi)
{
    int res;

    (void)path;

    res = pwrite(fi->fh, buf, size, offset);
    if (res == -1) {
        res = -errno;
    }

    return res;
}

static int
loopback_statfs(const char *path, struct statvfs *stbuf)
{
    int res;

    res = statvfs(path, stbuf);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
loopback_flush(const char *path, struct fuse_file_info *fi)
{
    int res;

    (void)path;

    res = close(dup(fi->fh));
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
loopback_release(const char *path, struct fuse_file_info *fi)
{
    (void)path;

    close(fi->fh);

    return 0;
}

static int
loopback_fsync(const char *path, int isdatasync, struct fuse_file_info *fi)
{
    int res;

    (void)path;

    (void)isdatasync;

    res = fsync(fi->fh);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
loopback_setxattr(const char *path, const char *name, const char *value,
                  size_t size, int flags, uint32_t position)
{
    int res;

    if (!strncmp(name, XATTR_APPLE_PREFIX, sizeof(XATTR_APPLE_PREFIX) - 1)) {
        flags &= ~(XATTR_NOSECURITY);
    }

    if (!strcmp(name, A_KAUTH_FILESEC_XATTR)) {

        char new_name[MAXPATHLEN];

        memcpy(new_name, A_KAUTH_FILESEC_XATTR, sizeof(A_KAUTH_FILESEC_XATTR));
        memcpy(new_name, G_PREFIX, sizeof(G_PREFIX) - 1);

        res = setxattr(path, new_name, value, size, position, flags);

    } else {
        res = setxattr(path, name, value, size, position, flags);
    }

    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
loopback_getxattr(const char *path, const char *name, char *value, size_t size,
                  uint32_t position)
{
    int res;

    if (strcmp(name, A_KAUTH_FILESEC_XATTR) == 0) {

        char new_name[MAXPATHLEN];

        memcpy(new_name, A_KAUTH_FILESEC_XATTR, sizeof(A_KAUTH_FILESEC_XATTR));
        memcpy(new_name, G_PREFIX, sizeof(G_PREFIX) - 1);

        res = getxattr(path, new_name, value, size, position, XATTR_NOFOLLOW);

    } else {
        res = getxattr(path, name, value, size, position, XATTR_NOFOLLOW);
    }

    if (res == -1) {
        return -errno;
    }

    return res;
}

static int
loopback_listxattr(const char *path, char *list, size_t size)
{
    ssize_t res = listxattr(path, list, size, XATTR_NOFOLLOW);
    if (res > 0) {
        if (list) {
            size_t len = 0;
            char *curr = list;
            do { 
                size_t thislen = strlen(curr) + 1;
                if (strcmp(curr, G_KAUTH_FILESEC_XATTR) == 0) {
                    memmove(curr, curr + thislen, res - len - thislen);
                    res -= thislen;
                    break;
                }
                curr += thislen;
                len += thislen;
            } while (len < res);
        }
    }

    if (res == -1) {
        return -errno;
    }

    return res;
}

static int
loopback_removexattr(const char *path, const char *name)
{
    int res;

    if (strcmp(name, A_KAUTH_FILESEC_XATTR) == 0) {

        char new_name[MAXPATHLEN];

        memcpy(new_name, A_KAUTH_FILESEC_XATTR, sizeof(A_KAUTH_FILESEC_XATTR));
        memcpy(new_name, G_PREFIX, sizeof(G_PREFIX) - 1);

        res = removexattr(path, new_name, XATTR_NOFOLLOW);

    } else {
        res = removexattr(path, name, XATTR_NOFOLLOW);
    }

    if (res == -1) {
        return -errno;
    }

    return 0;
}

void *
loopback_init(struct fuse_conn_info *conn)
{
    FUSE_ENABLE_SETVOLNAME(conn);
    FUSE_ENABLE_XTIMES(conn);

    return NULL;
}

void
loopback_destroy(void *userdata)
{
}

static struct fuse_operations loopback_oper = {
    .init        = loopback_init,
    .destroy     = loopback_destroy,
    .getattr     = loopback_getattr,
    .fgetattr    = loopback_fgetattr,
    .readlink    = loopback_readlink,
    .opendir     = loopback_opendir,
    .readdir     = loopback_readdir,
    .releasedir  = loopback_releasedir,
    .mknod       = loopback_mknod,
    .mkdir       = loopback_mkdir,
    .symlink     = loopback_symlink,
    .unlink      = loopback_unlink,
    .rmdir       = loopback_rmdir,
    .rename      = loopback_rename,
    .link        = loopback_link,
    .create      = loopback_create,
    .open        = loopback_open,
    .read        = loopback_read,
    .write       = loopback_write,
    .statfs      = loopback_statfs,
    .flush       = loopback_flush,
    .release     = loopback_release,
    .fsync       = loopback_fsync,
    .setxattr    = loopback_setxattr,
    .getxattr    = loopback_getxattr,
    .listxattr   = loopback_listxattr,
    .removexattr = loopback_removexattr,
    .exchange    = loopback_exchange,
    .getxtimes   = loopback_getxtimes,
    .setattr_x   = loopback_setattr_x,
    .fsetattr_x  = loopback_fsetattr_x,
};

int
main(int argc, char *argv[])
{
    umask(0);

    return fuse_main(argc, argv, &loopback_oper, NULL);
}
 
 */