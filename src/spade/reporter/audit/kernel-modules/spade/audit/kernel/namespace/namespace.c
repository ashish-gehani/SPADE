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

#include <linux/kernel.h>
#include <linux/types.h>
#include <linux/errno.h>

#include "spade/audit/kernel/namespace/namespace.h"


static struct kernel_namespace_pointers global = {};


int kernel_namespace_set(struct kernel_namespace_pointers *k)
{
    if (!k)
    {
        return -EINVAL;
    }

    global.ops_cgroup = k->ops_cgroup;
    global.ops_ipc = k->ops_ipc;
    global.ops_mnt = k->ops_mnt;
    global.ops_net = k->ops_net;
    global.ops_pid = k->ops_pid;
    global.ops_user = k->ops_user;
    
    return 0;
}

int kernel_namespace_unset(void)
{
    global.ops_cgroup = global.ops_ipc = global.ops_mnt = global.ops_net = global.ops_pid = global.ops_user = NULL;
    return 0;
}

struct kernel_namespace_pointers* kernel_namespace_get_pointers(void)
{
    return &global;
}
