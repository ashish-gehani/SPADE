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

#ifndef _SPADE_AUDIT_GLOBAL_H
#define _SPADE_AUDIT_GLOBAL_H

#include <linux/netfilter.h>

#include "audit/arg/arg.h"
#include "audit/state/state.h"
#include "audit/context/context.h"
#include "audit/kernel/function/number.h"


/*

    Sequence of actions:

        1.      Init
        ...
        N.      Perform any ops like auditing start/stop, etc.
        N+1.    Deinit

*/

struct global
{
    struct state s;
    struct context c;
    atomic_t inited;
    atomic_t auditing_started;
};

/*
    Init.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int global_init(bool dry_run);

/*
    Deinit.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int global_deinit(void);

/*
    Is initialized?

    Returns:
        true/false.
*/
bool global_is_initialized(void);

/*
    Start auditing.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int global_auditing_start(const struct arg *arg);

/*
    Stop auditing.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int global_auditing_stop(void);

/*
    Is auditing started?

    Returns:
        true/false.
*/
bool global_is_auditing_started(void);


#endif // _SPADE_AUDIT_GLOBAL_H