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

#ifndef _SPADE_AUDIT_EXPORTED_AUDITING_H
#define _SPADE_AUDIT_EXPORTED_AUDITING_H

#include <linux/init.h>
#include <linux/kernel.h>

#include "audit/arg/arg.h"


/*
    Function exported by the main kernel module to start auditing
    with the given arguments.

    The hash for the callee and the caller must match.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int exported_auditing_spade_start(const type_build_hash_t hash, struct arg *arg);

/*
    Function exported by the main kernel module to stop auditing.

    The hash for the callee and the caller must match.

    Returns:
        0       -> Success.
        -ive    -> Error code.
*/
int exported_auditing_spade_stop(const type_build_hash_t hash);


#endif // _SPADE_AUDIT_EXPORTED_AUDITING_H