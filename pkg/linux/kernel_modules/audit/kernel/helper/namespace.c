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

#include <linux/errno.h>
#include <linux/nsproxy.h>
#include <linux/pid_namespace.h>
#include <linux/sched/task.h>

#include "audit/kernel/helper/namespace.h"

#include "audit/kernel/helper/task.h"
#include "audit/kernel/helper/audit_log.h"
#include "audit/msg/namespace/create.h"
#include "audit/state/state.h"
#include "audit/util/log/log.h"
#include "audit/kernel/namespace/namespace.h"


static long _get_ns_inum(
    struct task_struct *struct_task_struct,
	const struct proc_ns_operations *proc_ns_operations
)
{
	long inum = -1;
	struct ns_common *struct_ns_common;
	
    if (!struct_task_struct || !proc_ns_operations)
    {
        return inum;
    }

	struct_ns_common = proc_ns_operations->get(struct_task_struct);
    if (!struct_ns_common)
        return -1;

    inum = struct_ns_common->inum;

    // Release
    proc_ns_operations->put(struct_ns_common);
	return inum;
}

/*
 * The PID namespace the task's next children go into. Read from nsproxy rather
 * than through pidns_for_children_operations: its get() returns no namespace
 * until the namespace has a child reaper, so right after unshare(CLONE_NEWPID)
 * it hides the very namespace the caller just created. nsproxy already points
 * to it, and its inode number is allocated when it is created.
 */
static long _get_pid_for_children_ns_inum(struct task_struct *struct_task_struct)
{
	long inum = -1;

	if (!struct_task_struct)
		return inum;

	task_lock(struct_task_struct);
	if (struct_task_struct->nsproxy && struct_task_struct->nsproxy->pid_ns_for_children)
		inum = struct_task_struct->nsproxy->pid_ns_for_children->ns.inum;
	task_unlock(struct_task_struct);

	return inum;
}

int kernel_helper_namespace_populate_msg(
    struct msg_namespace *msg,
    enum kernel_function_number func_num, long target_pid, bool sys_success,
    enum msg_namespace_operation op
)
{
    const char *log_id = "kernel_helper_namespace_populate_msg";
	struct pid *pid_struct;
	struct task_struct *pid_task_struct = NULL;
    struct kernel_namespace_pointers *k_ns_op_ptrs;
    long host_pid = 0;
    pid_t ns_pid = 0;
    int err;
    int sys_num;
    bool sys_num_default_to_func_num = false;

    if (!msg)
        return -EINVAL;

    k_ns_op_ptrs = kernel_namespace_get_pointers();
    if (
        !k_ns_op_ptrs
        || !k_ns_op_ptrs->ops_cgroup
        || !k_ns_op_ptrs->ops_ipc
        || !k_ns_op_ptrs->ops_mnt
        || !k_ns_op_ptrs->ops_net
        || !k_ns_op_ptrs->ops_user
        || !k_ns_op_ptrs->ops_pid
        || !k_ns_op_ptrs->ops_pid_children
    )
        return -EINVAL;

    msg_namespace_create(msg);

	rcu_read_lock();
	pid_struct = find_vpid(target_pid);
	// target_pid is seen from the caller's PID namespace (e.g. clone's return value). A child created
	// into another PID namespace has a different pid in its own, e.g. 1 for that namespace's first process.
	if (pid_struct)
	{
		ns_pid = pid_nr_ns(pid_struct, ns_of_pid(pid_struct));
		host_pid = pid_nr(pid_struct);
		// pid_task needs the RCU read lock, and the task may exit once it is released: hold a
		// reference to it while its namespaces are read below.
		pid_task_struct = pid_task(pid_struct, PIDTYPE_PID);
		if (pid_task_struct)
			get_task_struct(pid_task_struct);
	}
    rcu_read_unlock();

	if (!pid_struct)
    {
        util_log_debug(log_id, "NULL pid_struct");
        return -ESRCH;
    }
	
    if (!pid_task_struct)
    {
        util_log_debug(log_id, "NULL pid_task_struct");
        return -ESRCH;
    }

    err = kernel_function_number_to_system_call_number(
        &sys_num, func_num, sys_num_default_to_func_num
    );
    if (err != 0)
    {
        put_task_struct(pid_task_struct);
        util_log_debug(log_id, "Failed to get syscall number from function number: %d. Err: %d", func_num, err);
        return err;
    }

    msg->ns_inum_pid = _get_ns_inum(pid_task_struct, k_ns_op_ptrs->ops_pid);
    msg->ns_inum_pid_children = _get_pid_for_children_ns_inum(pid_task_struct);
	msg->ns_inum_mnt = _get_ns_inum(pid_task_struct, k_ns_op_ptrs->ops_mnt);
	msg->ns_inum_net = _get_ns_inum(pid_task_struct, k_ns_op_ptrs->ops_net);
	msg->ns_inum_usr = _get_ns_inum(pid_task_struct, k_ns_op_ptrs->ops_user);
	msg->ns_inum_ipc = _get_ns_inum(pid_task_struct, k_ns_op_ptrs->ops_ipc);
	msg->ns_inum_cgroup = _get_ns_inum(pid_task_struct, k_ns_op_ptrs->ops_cgroup);
    put_task_struct(pid_task_struct);

    msg->host_pid = host_pid;
    msg->ns_pid = ns_pid;
    msg->op = op;
    msg->syscall_number = sys_num;

	return 0;
}

int kernel_helper_namespace_log_msg_to_audit(
    struct msg_namespace *msg
)
{
    const char *log_id = "kernel_helper_namespace_log_msg_to_audit";
    int err;
    struct audit_context *audit_ctx;

    if (!msg)
    {
        return -EINVAL;
    }
    
	audit_ctx = kernel_helper_task_current_audit_context();
    if (!audit_ctx)
    {
        util_log_debug(log_id, "NULL audit context");
    }
    err = kernel_helper_audit_log(
        audit_ctx,
        &msg->header
    );
    return err;
}