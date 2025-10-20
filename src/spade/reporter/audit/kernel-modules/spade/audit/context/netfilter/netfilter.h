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

#ifndef _SPADE_AUDIT_CONTEXT_NETFILTER_H
#define _SPADE_AUDIT_CONTEXT_NETFILTER_H

#include <linux/init.h>

#include "spade/audit/arg/arg.h"


struct context_netfilter
{
    bool initialized;

    bool audit_hooks;

    bool use_user;

    struct type_monitor_user m_user;

    bool include_ns_info;

    enum type_monitor_connections monitor_ct;
};

/*
    Check if the context is initialized along with nested context.

    Params:
        dst     : Contains the result if successful.
        s       : The context to check for initialization.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int context_netfilter_is_initialized(
    bool *dst,
    struct context_netfilter *c
);

/*
    Initialize context with the given arg and nested context.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int context_netfilter_init(struct context_netfilter *c, struct arg *arg);

/*
    Deinitialize context.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int context_netfilter_deinit(struct context_netfilter *c);

#endif // _SPADE_AUDIT_CONTEXT_NETFILTER_H