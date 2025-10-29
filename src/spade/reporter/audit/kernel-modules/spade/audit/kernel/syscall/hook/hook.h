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

#ifndef SPADE_AUDIT_KERNEL_SYSCALL_HOOK_HOOK_H
#define SPADE_AUDIT_KERNEL_SYSCALL_HOOK_HOOK_H

#include <linux/types.h>


struct kernel_syscall_hook
{
    /*
        Get syscall number.

        Returns:
            Sys number.
    */
    int (*get_num)(void);

    /*
        Get syscall name.

        Returns:
            name    -> Success.
            NULL    -> Error.
    */
    const char* (*get_name)(void);

    /*
        Get the function that hooks the syscall.

        Returns:
            ptr     -> Success.
            NULL    -> Error.
    */
    void* (*get_hook_func)(void);

    /*
        Get the original function ptr;

        Returns:
            ptr     -> Success.
            NULL    -> Error.
    */
    void* (*get_orig_func_ptr)(void);
};


#endif // SPADE_AUDIT_KERNEL_SYSCALL_HOOK_HOOK_H