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
#include <net/net_namespace.h>

#include "spade/audit/kernel/setup/netfilter/list.h"
#include "spade/audit/kernel/setup/netfilter/netfilter.h"
#include "spade/audit/util/log/log.h"


extern const struct nf_hook_ops kernel_netfilter_setup_list_hooks[];
extern const int kernel_netfilter_setup_list_hooks_size;


int kernel_setup_netfilter_do()
{
    return nf_register_net_hooks(
        &init_net, kernel_netfilter_setup_list_hooks, kernel_netfilter_setup_list_hooks_size
    );
}

int kernel_setup_netfilter_undo()
{
    nf_unregister_net_hooks(
        &init_net, kernel_netfilter_setup_list_hooks, kernel_netfilter_setup_list_hooks_size
    );

    return 0;
}