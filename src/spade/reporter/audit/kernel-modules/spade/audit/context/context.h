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

#ifndef _SPADE_AUDIT_CONTEXT_H
#define _SPADE_AUDIT_CONTEXT_H

#include <linux/init.h>
#include <linux/atomic.h>
#include <linux/proc_ns.h>

#include "spade/arg/arg.h"
#include "spade/audit/context/syscall/syscall.h"
#include "spade/audit/context/netfilter/netfilter.h"


struct context
{
    bool initialized;

    // Syscall related context.
    struct context_syscall syscall;

    // Netfilter related context.
    struct context_netfilter netfilter;
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
int context_is_initialized(
    bool *dst,
    struct context *c
);

/*
    Initialize context with the given arg and nested context.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int context_init(struct context *c, struct arg *arg);

/*
    Deinitialize context.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int context_deinit(struct context *c);

#endif // _SPADE_AUDIT_CONTEXT_H