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

#ifndef SPADE_AUDIT_STATE_NETFILTER_H
#define SPADE_AUDIT_STATE_NETFILTER_H

#include <linux/types.h>


struct state_netfilter
{
    bool initialized;

    bool dry_run;

    /*
        Event discarded because of the event didn't match the filter.
    */
    unsigned long discarded_events_count;
};

/*
    Check if the state is initialized along with nested state.

    Params:
        dst     : Contains the result if successful.
        s       : The state to check for initialization.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int state_netfilter_is_initialized(
    bool *dst,
    struct state_netfilter *s
);

/*
    Initialize state with the given arg and nested state.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int state_netfilter_init(
    struct state_netfilter *s, bool dry_run
);

/*
    Deinitialize state.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int state_netfilter_deinit(
    struct state_netfilter *s
);


#endif // SPADE_AUDIT_STATE_NETFILTER_H