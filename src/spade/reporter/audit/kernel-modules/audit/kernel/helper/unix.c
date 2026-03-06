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

#include <linux/errno.h>
#include <linux/slab.h>
#include <linux/sched.h>
#include <linux/socket.h>
#include <linux/uaccess.h>
#include <net/scm.h>

#include "audit/kernel/helper/unix.h"
#include "audit/kernel/helper/audit_log.h"
#include "audit/util/log/log.h"

/* Large enough to hold SCM_RIGHTS with the kernel-imposed fd limit (253). */
#define UNIX_CTRL_BUF_SIZE CMSG_SPACE(SCM_MAX_FD * sizeof(int))

/*
 * Copies the msghdr header and its control buffer from userspace, finds the
 * SCM_RIGHTS cmsghdr, and populates dst with all passed file descriptors.
 */
int kernel_helper_unix_get_scm_rights_fd(struct msghdr __user *mh, struct msg_scm_fd *dst)
{
    char *ctrl_buf;
    struct msghdr kernel_mh;
    struct cmsghdr *cmsg;
    size_t copy_len;
    int ret = -ENOENT;

    if (!mh || !dst)
        return -EINVAL;

    if (copy_from_user(&kernel_mh, mh, sizeof(struct msghdr)))
        return -EFAULT;

    if (!kernel_mh.msg_control || kernel_mh.msg_controllen == 0)
        return -ENOENT;

    copy_len = min_t(size_t, kernel_mh.msg_controllen, UNIX_CTRL_BUF_SIZE);

    ctrl_buf = kmalloc(copy_len, GFP_KERNEL);
    if (!ctrl_buf)
        return -ENOMEM;

    if (copy_from_user(ctrl_buf, kernel_mh.msg_control, copy_len))
    {
        kfree(ctrl_buf);
        return -EFAULT;
    }

    kernel_mh.msg_control = ctrl_buf;
    kernel_mh.msg_controllen = copy_len;

    for (cmsg = CMSG_FIRSTHDR(&kernel_mh); cmsg != NULL; cmsg = CMSG_NXTHDR(&kernel_mh, cmsg))
    {
        if (cmsg->cmsg_level == SOL_SOCKET && cmsg->cmsg_type == SCM_RIGHTS)
        {
            int *fds = (int *)CMSG_DATA(cmsg);
            int fd_count = (cmsg->cmsg_len - CMSG_LEN(0)) / sizeof(int);
            int i;

            dst->fds_count = min_t(int, fd_count, SCM_MAX_FD);
            for (i = 0; i < dst->fds_count; i++)
                dst->fds[i] = fds[i];

            ret = 0;
            break;
        }
    }

    kfree(ctrl_buf);
    return ret;
}

int kernel_helper_unix_audit_log_scm_fd_msg(
    struct msghdr __user *mh,
    struct audit_context *audit_ctx,
    enum kernel_function_number func_num
)
{
    const char *log_id = "kernel_helper_unix_audit_log_scm_fd_msg";
    int err;
    int sys_num;
    struct msg_scm_fd *scm_fd_msg;

    scm_fd_msg = kzalloc(sizeof(*scm_fd_msg), GFP_KERNEL);
    if (!scm_fd_msg)
        return -ENOMEM;

    err = kernel_function_number_to_system_call_number(&sys_num, func_num, false);
    if (err != 0)
    {
        util_log_debug(log_id, "Failed kernel_function_number_to_system_call_number. Err: %d", err);
        sys_num = -1;
    }

    scm_fd_msg->pid = task_pid_nr(current);
    scm_fd_msg->syscall_number = sys_num;

    err = kernel_helper_unix_get_scm_rights_fd(mh, scm_fd_msg);
    if (err != 0 && err != -ENOENT)
        util_log_debug(log_id, "Failed kernel_helper_unix_get_scm_rights_fd. Err: %d", err);

    err = kernel_helper_audit_log(audit_ctx, &scm_fd_msg->header);
    if (err != 0)
        util_log_debug(log_id, "Failed kernel_helper_audit_log. Err: %d", err);

    kfree(scm_fd_msg);
    return err;
}
