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

#include <linux/types.h>
#include <linux/errno.h>

#include "spade/audit/kernel/namespace/namespace.h"
#include "spade/audit/kernel/helper/kernel.h"
#include "spade/audit/kernel/setup/namespace/namespace.h"
#include "spade/util/log/log.h"


static struct proc_ns_operations* _get_ns_ops_kernel_ptr(
    kallsyms_lookup_name_t kallsyms_lookup_name,
    const char *kernel_symbol_name,
    const char *warn_msg_on_failure
)
{   
    unsigned long symbol_address = 0;

    if (!kallsyms_lookup_name || !kernel_symbol_name || !warn_msg_on_failure)
        return NULL;

    symbol_address = kallsyms_lookup_name(kernel_symbol_name);
	if (symbol_address == 0)
    {
        util_log_warn("_get_ns_ops_kernel_ptr", warn_msg_on_failure);
        return NULL;
	}
    return (struct proc_ns_operations*)symbol_address;
}

static int _init_ns_ops_kernel_ptrs(kallsyms_lookup_name_t kallsyms_lookup_name)
{
    struct kernel_namespace_pointers s;
    if (!kallsyms_lookup_name)
    {
        return -ENOENT;
    }

    s.ops_mnt = _get_ns_ops_kernel_ptr(kallsyms_lookup_name, "mntns_operations", "Failed to get mnt NS ops symbol");
    if (!s.ops_mnt)
    {
        return -ENOENT;
    }
    
	s.ops_net = _get_ns_ops_kernel_ptr(kallsyms_lookup_name, "netns_operations", "Failed to get net NS ops symbol");
    if (!s.ops_net)
    {
        return -ENOENT;
    }

	s.ops_pid = _get_ns_ops_kernel_ptr(kallsyms_lookup_name, "pidns_operations", "Failed to get pid NS ops symbol");
    if (!s.ops_pid)
    {
        return -ENOENT;
    }

	s.ops_pid_children = _get_ns_ops_kernel_ptr(kallsyms_lookup_name, "pidns_for_children_operations", "Failed to get pid children NS ops symbol");
    if (!s.ops_pid)
    {
        return -ENOENT;
    }

	s.ops_user = _get_ns_ops_kernel_ptr(kallsyms_lookup_name, "userns_operations", "Failed to get user NS ops symbol");
    if (!s.ops_user)
    {
        return -ENOENT;
    }

	s.ops_ipc = _get_ns_ops_kernel_ptr(kallsyms_lookup_name, "ipcns_operations", "Failed to get ipc NS ops symbol");
    if (!s.ops_ipc)
    {
        return -ENOENT;
    }

	s.ops_cgroup = _get_ns_ops_kernel_ptr(kallsyms_lookup_name, "cgroupns_operations", "Failed to get cgroup NS ops symbol");
    if (!s.ops_cgroup)
    {
        return -ENOENT;
    }

    return kernel_namespace_set(&s);
}

int kernel_setup_namespace_do(void)
{
    kallsyms_lookup_name_t kallsyms_func;

    kallsyms_func = kernel_helper_kernel_get_kallsyms_func();
    if (!kallsyms_func)
    {
        return -EINVAL;
    }

    return _init_ns_ops_kernel_ptrs(kallsyms_func);
}

int kernel_setup_namespace_undo(void)
{
    return kernel_namespace_unset();
}
