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

#ifndef _SPADE_AUDIT_GLOBAL_NETFILTER_NETFILTER_H
#define _SPADE_AUDIT_GLOBAL_NETFILTER_NETFILTER_H

#include <linux/types.h>
#include <linux/netfilter.h>

#include "spade/audit/context/netfilter/netfilter.h"


/*
    Is netfilter event actionable based on the information.

    Params:
        dst             : The result pointer.
        ctx             : The netfilter context.
        uid             : Uid of the process which generated the event.

    Returns:
        0    -> dst is successfully set.
        -ive -> Error code and dst cannot be used.
*/
int global_netfilter_uid_is_actionable(
    bool *dst,
    struct context_netfilter *ctx,
    uid_t uid
);

/*
    Is netfilter event actionable based on the information.

    Params:
        dst             : The result pointer.
        ctx             : The netfilter context.
        ct_info         : The conntrack state of a sk buff.

    Returns:
        0    -> dst is successfully set.
        -ive -> Error code and dst cannot be used.
*/
int global_netfilter_conntrack_info_is_actionable(
    bool *dst,
    struct context_netfilter *ctx,
    enum ip_conntrack_info ct_info
);

#endif // _SPADE_AUDIT_GLOBAL_NETFILTER_NETFILTER_H