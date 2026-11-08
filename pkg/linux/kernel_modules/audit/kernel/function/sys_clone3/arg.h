/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

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

#ifndef SPADE_AUDIT_KERNEL_FUNCTION_SYS_CLONE3_ARG_H
#define SPADE_AUDIT_KERNEL_FUNCTION_SYS_CLONE3_ARG_H

#include <linux/types.h>

struct kernel_function_sys_clone3_arg
{
    /*
        clone3's flags as clone takes them: clone_args.flags, with
        clone_args.exit_signal in the low byte (CSIGNAL). 0 if they could not
        be read from the caller's memory.
    */
    unsigned long long flags;
};

#endif // SPADE_AUDIT_KERNEL_FUNCTION_SYS_CLONE3_ARG_H
