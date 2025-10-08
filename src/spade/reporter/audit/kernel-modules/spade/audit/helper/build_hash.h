/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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

#ifndef _SPADE_AUDIT_HELPER_BUILD_HASH_H
#define _SPADE_AUDIT_HELPER_BUILD_HASH_H


#include <linux/kernel.h>
#include <linux/string.h>
#include <linux/errno.h>

#include "spade/config/config.h"


/*
    Check if given build hash structs match.

    Params:
        dst             : The result of the equality check.
        build_hash_a    : Hash.
        build_hash_a    : Hash.

    Returns:
        0       -> Successfully set dst.
        -ive    -> Error code.
*/
int helper_build_build_hashes_match(
    bool *dst,
    const struct config_build_hash *build_hash_a,
    const struct config_build_hash *build_hash_b
);

#endif // _SPADE_AUDIT_HELPER_BUILD_HASH_H