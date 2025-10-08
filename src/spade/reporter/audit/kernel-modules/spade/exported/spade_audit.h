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

#ifndef SPADE_AUDIT_EXPORTED_SPADE_AUDIT_H
#define SPADE_AUDIT_EXPORTED_SPADE_AUDIT_H

#include "spade/arg/arg.h"
#include "spade/config/config.h"


/*
    Start auditing by SPADE kernel module.

    Params:
        config     : The module config.
        arg        : The module arguments to use.

    Returns:
        0    -> Success
        -ive -> Error code
*/
int exported_spade_audit_start(const struct config *config, struct arg *arg);

/*
    Stop auditing by SPADE kernel module.

    Params:
        config     : The module config.

    Returns:
        0    -> Success
        -ive -> Error code
*/
int exported_spade_audit_stop(const struct config *config);

#endif // SPADE_AUDIT_EXPORTED_SPADE_AUDIT_H