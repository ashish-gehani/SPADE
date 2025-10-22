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

#include <linux/errno.h>

#include "spade/audit/exported/auditing.h"
#include "spade/audit/global/global.h"


extern const type_build_hash_t spade_build_hash;


static int build_hashes_equal(const type_build_hash_t a, const type_build_hash_t b)
{
    if (
        !a
        || !b
        || memcmp(a, b, sizeof(type_build_hash_t)) != 0
    )
        return -EPERM;
    return 0;
}

int exported_auditing_spade_start(const type_build_hash_t caller_hash, struct arg *arg)
{
    int err;

    if (!arg)
        return -EINVAL;

    if ((err = build_hashes_equal(caller_hash, spade_build_hash)) != 0)
        return err;

    err = global_auditing_start(arg);

    return err;
}
EXPORT_SYMBOL_GPL(exported_auditing_spade_start);

int exported_auditing_spade_stop(const type_build_hash_t caller_hash)
{
    int err;

    if ((err = build_hashes_equal(caller_hash, spade_build_hash)) != 0)
        return err;

    err = global_auditing_stop();

    return err;
}
EXPORT_SYMBOL_GPL(exported_auditing_spade_stop);
