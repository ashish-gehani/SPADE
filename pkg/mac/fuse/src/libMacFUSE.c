/*
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

#include <AvailabilityMacros.h>

#if !defined(AVAILABLE_MAC_OS_X_VERSION_10_5_AND_LATER)
#error "This file system requires Leopard and above."
#endif

#define FUSE_USE_VERSION 26

#include "spade_reporter_MacFUSE.h"

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
#include <jni.h>

#if defined(_POSIX_C_SOURCE)
typedef unsigned char u_char;
typedef unsigned short u_short;
typedef unsigned int u_int;
typedef unsigned long u_long;
#endif

#define G_PREFIX                       "org"
#define G_KAUTH_FILESEC_XATTR G_PREFIX ".apple.system.Security"
#define A_PREFIX                       "com"
#define A_KAUTH_FILESEC_XATTR A_PREFIX ".apple.system.Security"
#define XATTR_APPLE_PREFIX             "com.apple."

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

static int
spade_getattr(const char *path, struct stat *stbuf) {
    int res;

    res = lstat(path, stbuf);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
spade_fgetattr(const char *path, struct stat *stbuf,
        struct fuse_file_info *fi) {
    int res;

    (void) path;

    res = fstat(fi->fh, stbuf);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
spade_readlink(const char *path, char *buf, size_t size) {
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

struct spade_dirp {
    DIR *dp;
    struct dirent *entry;
    off_t offset;
};

static int
spade_opendir(const char *path, struct fuse_file_info *fi) {
    int res;

    struct spade_dirp *d = malloc(sizeof (struct spade_dirp));
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

    fi->fh = (unsigned long) d;

    return 0;
}

static inline struct spade_dirp *
get_dirp(struct fuse_file_info *fi) {
    return (struct spade_dirp *) (uintptr_t) fi->fh;
}

static int
spade_readdir(const char *path, void *buf, fuse_fill_dir_t filler,
        off_t offset, struct fuse_file_info *fi) {
    struct spade_dirp *d = get_dirp(fi);

    (void) path;

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

        memset(&st, 0, sizeof (st));
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
spade_releasedir(const char *path, struct fuse_file_info *fi) {
    struct spade_dirp *d = get_dirp(fi);

    (void) path;

    closedir(d->dp);
    free(d);

    return 0;
}

static int
spade_mknod(const char *path, mode_t mode, dev_t rdev) {
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
spade_mkdir(const char *path, mode_t mode) {
    int res;

    res = mkdir(path, mode);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
spade_unlink(const char *path) {
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

static int
spade_rmdir(const char *path) {
    int res;

    res = rmdir(path);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
spade_symlink(const char *from, const char *to) {
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

static int
spade_rename(const char *from, const char *to) {
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

static int
spade_exchange(const char *path1, const char *path2, unsigned long options) {
    int res;

    res = exchangedata(path1, path2, options);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
spade_link(const char *from, const char *to) {
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

static int
spade_fsetattr_x(const char *path, struct setattr_x *attr,
        struct fuse_file_info *fi) {
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
                sizeof (struct timespec), FSOPT_NOFOLLOW);

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
                sizeof (struct timespec), FSOPT_NOFOLLOW);

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
                sizeof (struct timespec), FSOPT_NOFOLLOW);

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
spade_setattr_x(const char *path, struct setattr_x *attr) {
    return spade_fsetattr_x(path, attr, (struct fuse_file_info *) 0);
}

static int
spade_getxtimes(const char *path, struct timespec *bkuptime,
        struct timespec *crtime) {
    int res = 0;
    struct attrlist attributes;

    attributes.bitmapcount = ATTR_BIT_MAP_COUNT;
    attributes.reserved = 0;
    attributes.commonattr = 0;
    attributes.dirattr = 0;
    attributes.fileattr = 0;
    attributes.forkattr = 0;
    attributes.volattr = 0;

    struct xtimeattrbuf {
        uint32_t size;
        struct timespec xtime;
    } __attribute__((packed));


    struct xtimeattrbuf buf;

    attributes.commonattr = ATTR_CMN_BKUPTIME;
    res = getattrlist(path, &attributes, &buf, sizeof (buf), FSOPT_NOFOLLOW);
    if (res == 0) {
        (void) memcpy(bkuptime, &(buf.xtime), sizeof (struct timespec));
    } else {
        (void) memset(bkuptime, 0, sizeof (struct timespec));
    }

    attributes.commonattr = ATTR_CMN_CRTIME;
    res = getattrlist(path, &attributes, &buf, sizeof (buf), FSOPT_NOFOLLOW);
    if (res == 0) {
        (void) memcpy(crtime, &(buf.xtime), sizeof (struct timespec));
    } else {
        (void) memset(crtime, 0, sizeof (struct timespec));
    }

    return 0;
}

static int
spade_create(const char *path, mode_t mode, struct fuse_file_info *fi) {
    int fd;

    fd = open(path, fi->flags, mode);
    if (fd == -1) {
        return -errno;
    }

    fi->fh = fd;
    return 0;
}

static int
spade_open(const char *path, struct fuse_file_info *fi) {
    int fd;

    fd = open(path, fi->flags);
    if (fd == -1) {
        return -errno;
    }

    fi->fh = fd;
    return 0;
}

static int
spade_read(const char *path, char *buf, size_t size, off_t offset,
        struct fuse_file_info *fi) {
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

    (void) path;
    res = pread(fi->fh, buf, size, offset);
    if (res == -1) {
        res = -errno;
    }

    gettimeofday(&endtime, NULL);
    seconds = endtime.tv_sec - starttime.tv_sec;
    useconds = endtime.tv_usec - starttime.tv_usec;
    mtime = (seconds * 1000000 + useconds);
    int iotime = mtime;

    (*env)->CallVoidMethod(env, reporterInstance, readMethod, fuse_get_context()->pid, iotime, jpath, link);

    return res;
}

static int
spade_write(const char *path, const char *buf, size_t size,
        off_t offset, struct fuse_file_info *fi) {
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

    (void) path;
    res = pwrite(fi->fh, buf, size, offset);
    if (res == -1) {
        res = -errno;
    }

    gettimeofday(&endtime, NULL);
    seconds = endtime.tv_sec - starttime.tv_sec;
    useconds = endtime.tv_usec - starttime.tv_usec;
    mtime = (seconds * 1000000 + useconds);
    int iotime = mtime;

    (*env)->CallVoidMethod(env, reporterInstance, writeMethod, fuse_get_context()->pid, iotime, jpath, link);

    return res;
}

static int
spade_statfs(const char *path, struct statvfs *stbuf) {
    int res;

    res = statvfs(path, stbuf);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
spade_flush(const char *path, struct fuse_file_info *fi) {
    int res;

    (void) path;

    res = close(dup(fi->fh));
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
spade_release(const char *path, struct fuse_file_info *fi) {
    (void) path;

    close(fi->fh);

    return 0;
}

static int
spade_fsync(const char *path, int isdatasync, struct fuse_file_info *fi) {
    int res;

    (void) path;

    (void) isdatasync;

    res = fsync(fi->fh);
    if (res == -1) {
        return -errno;
    }

    return 0;
}

static int
spade_setxattr(const char *path, const char *name, const char *value,
        size_t size, int flags, uint32_t position) {
    int res;

    if (!strncmp(name, XATTR_APPLE_PREFIX, sizeof (XATTR_APPLE_PREFIX) - 1)) {
        flags &= ~(XATTR_NOSECURITY);
    }

    if (!strcmp(name, A_KAUTH_FILESEC_XATTR)) {

        char new_name[MAXPATHLEN];

        memcpy(new_name, A_KAUTH_FILESEC_XATTR, sizeof (A_KAUTH_FILESEC_XATTR));
        memcpy(new_name, G_PREFIX, sizeof (G_PREFIX) - 1);

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
spade_getxattr(const char *path, const char *name, char *value, size_t size,
        uint32_t position) {
    int res;

    if (strcmp(name, A_KAUTH_FILESEC_XATTR) == 0) {

        char new_name[MAXPATHLEN];

        memcpy(new_name, A_KAUTH_FILESEC_XATTR, sizeof (A_KAUTH_FILESEC_XATTR));
        memcpy(new_name, G_PREFIX, sizeof (G_PREFIX) - 1);

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
spade_listxattr(const char *path, char *list, size_t size) {
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
spade_removexattr(const char *path, const char *name) {
    int res;

    if (strcmp(name, A_KAUTH_FILESEC_XATTR) == 0) {

        char new_name[MAXPATHLEN];

        memcpy(new_name, A_KAUTH_FILESEC_XATTR, sizeof (A_KAUTH_FILESEC_XATTR));
        memcpy(new_name, G_PREFIX, sizeof (G_PREFIX) - 1);

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
spade_init(struct fuse_conn_info *conn) {
    FUSE_ENABLE_SETVOLNAME(conn);
    FUSE_ENABLE_XTIMES(conn);

    return NULL;
}

void
spade_destroy(void *userdata) {
}

static struct fuse_operations spade_oper = {
    .init = spade_init,
    .destroy = spade_destroy,
    .getattr = spade_getattr,
    .fgetattr = spade_fgetattr,
    .readlink = spade_readlink,
    .opendir = spade_opendir,
    .readdir = spade_readdir,
    .releasedir = spade_releasedir,
    .mknod = spade_mknod,
    .mkdir = spade_mkdir,
    .symlink = spade_symlink,
    .unlink = spade_unlink,
    .rmdir = spade_rmdir,
    .rename = spade_rename,
    .link = spade_link,
    .create = spade_create,
    .open = spade_open,
    .read = spade_read,
    .write = spade_write,
    .statfs = spade_statfs,
    .flush = spade_flush,
    .release = spade_release,
    .fsync = spade_fsync,
    .setxattr = spade_setxattr,
    .getxattr = spade_getxattr,
    .listxattr = spade_listxattr,
    .removexattr = spade_removexattr,
    .exchange = spade_exchange,
    .getxtimes = spade_getxtimes,
    .setattr_x = spade_setattr_x,
    .fsetattr_x = spade_fsetattr_x,
};

JNIEXPORT jint JNICALL Java_spade_reporter_MacFUSE_launchFUSE(JNIEnv *e, jobject o, jstring mountPoint) {
    reporterInstance = o;
    env = e;

    FUSEReporterClass = (*env)->FindClass(env, "spade/reporter/MacFUSE");
    readMethod = (*env)->GetMethodID(env, FUSEReporterClass, "read", "(IILjava/lang/String;I)V");
    writeMethod = (*env)->GetMethodID(env, FUSEReporterClass, "write", "(IILjava/lang/String;I)V");
    readlinkMethod = (*env)->GetMethodID(env, FUSEReporterClass, "readlink", "(IILjava/lang/String;)V");
    renameMethod = (*env)->GetMethodID(env, FUSEReporterClass, "rename", "(IILjava/lang/String;Ljava/lang/String;II)V");
    linkMethod = (*env)->GetMethodID(env, FUSEReporterClass, "link", "(ILjava/lang/String;Ljava/lang/String;)V");
    unlinkMethod = (*env)->GetMethodID(env, FUSEReporterClass, "unlink", "(ILjava/lang/String;)V");

    int argc = 5;
    char *argv[argc];
    argv[0] = "libMacFUSE";
    argv[1] = "-f";
    argv[2] = "-s";
    argv[3] = (char*) (*env)->GetStringUTFChars(env, mountPoint, NULL);
    argv[4] = "volname=SPADE-MacFUSE";
    // argv[4] = "-oallow_other,volname=SPADE-MacFUSE";

    umask(0);
    return fuse_main(argc, argv, &spade_oper, NULL);
}
