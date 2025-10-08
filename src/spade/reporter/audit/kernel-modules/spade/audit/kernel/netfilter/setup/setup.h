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

#ifndef SPADE_AUDIT_KERNEL_NETFILTER_SETUP_SETUP_H
#define SPADE_AUDIT_KERNEL_NETFILTER_SETUP_SETUP_H

#include <linux/types.h>


/*
    Hook all netfilter hooks.

    Returns:
        0       -> Successfully hooked all.
        -ive    -> Error code.
*/
int kernel_netfilter_hook_setup_do(void);

/*
    Unhook all netfilter hooks.

    Returns:
        0       -> Successfully unhooked all.
        -ive    -> Error code.
*/
int kernel_netfilter_hook_setup_undo(void);


#endif // SPADE_AUDIT_KERNEL_NETFILTER_SETUP_SETUP_H