/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2025 SRI International

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

#include <linux/sched.h>
#include <linux/cred.h>
#include <linux/kernel.h>
#include <linux/string.h>
#include <linux/pid_namespace.h>

#include "spade/audit/kernel/helper/task.h"
#include "spade/audit/util/log/log.h"

uid_t kernel_helper_task_host_view_current_uid()
{
    return from_kuid(&init_user_ns, current_cred()->uid);
}

uid_t kernel_helper_task_host_view_current_euid()
{
    return from_kuid(&init_user_ns, current_cred()->euid);
}

uid_t kernel_helper_task_host_view_current_suid()
{
    return from_kuid(&init_user_ns, current_cred()->suid);
}

uid_t kernel_helper_task_host_view_current_fsuid()
{
    return from_kuid(&init_user_ns, current_cred()->fsuid);
}

gid_t kernel_helper_task_host_view_current_gid()
{
    return from_kgid(&init_user_ns, current_cred()->gid);
}

gid_t kernel_helper_task_host_view_current_egid()
{
    return from_kgid(&init_user_ns, current_cred()->egid);
}

gid_t kernel_helper_task_host_view_current_sgid()
{
    return from_kgid(&init_user_ns, current_cred()->sgid);
}

gid_t kernel_helper_task_host_view_current_fsgid()
{
    return from_kgid(&init_user_ns, current_cred()->fsgid);
}

pid_t kernel_helper_task_task_view_get_tgid(pid_t pid)
{
    pid_t tgid;
    const char *log_id = "kernel_helper_task_task_view_get_tgid";
	struct pid *pid_struct;
	struct task_struct *pid_task_struct;

	rcu_read_lock();
	pid_struct = find_vpid(pid);
	if (!pid_struct)
    {
        rcu_read_unlock();
        util_log_debug(log_id, "NULL pid_struct");
        return -ESRCH;
    }

    pid_task_struct = pid_task(pid_struct, PIDTYPE_PID);
    if (!pid_task_struct)
    {
        rcu_read_unlock();
        util_log_debug(log_id, "NULL pid_task_struct");
        return -ESRCH;
    }

    tgid = pid_task_struct->tgid;
    rcu_read_unlock();

    return tgid;
}

pid_t kernel_helper_task_task_view_current_ppid()
{
    return current->real_parent->pid;
}

pid_t kernel_helper_task_task_view_current_pid()
{
    return current->pid;
}

struct audit_context *kernel_helper_task_current_audit_context()
{
#ifdef CONFIG_AUDITSYSCALL
    return current->audit_context;
#endif
    return NULL;
}

int kernel_helper_task_populate_process_info_from_current_task(
    struct msg_common_process *proc
)
{
    int ret;

    if (!proc)
        return -EINVAL;

    get_task_comm(proc->comm, current);

    if (ret != 0)
        return -EINVAL;

    proc->gid = kernel_helper_task_host_view_current_gid();
    proc->egid = kernel_helper_task_host_view_current_egid();
    proc->sgid = kernel_helper_task_host_view_current_sgid();
    proc->fsgid = kernel_helper_task_host_view_current_fsgid();
    proc->uid = kernel_helper_task_host_view_current_uid();
    proc->euid = kernel_helper_task_host_view_current_euid();
    proc->suid = kernel_helper_task_host_view_current_suid();
    proc->fsuid = kernel_helper_task_host_view_current_fsuid();
    proc->pid = kernel_helper_task_task_view_current_pid();
    proc->ppid = kernel_helper_task_task_view_current_ppid();

    return 0;
}
