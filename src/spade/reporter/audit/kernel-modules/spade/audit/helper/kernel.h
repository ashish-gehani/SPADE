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

#ifndef _SPADE_AUDIT_HELPER_KERNEL_H
#define _SPADE_AUDIT_HELPER_KERNEL_H

#include <linux/init.h>
#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/version.h>
#include <linux/types.h>


#define HELPER_KERNEL_VERSION_GTE_4_11_0 LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0)

#define HELPER_KERNEL_VERSION_GTE_4_17_0 LINUX_VERSION_CODE >= KERNEL_VERSION(4, 17, 0)

#define HELPER_KERNEL_VERSION_GTE_5_7_0 LINUX_VERSION_CODE >= KERNEL_VERSION(5, 7, 0)

#if defined(CONFIG_X86_64) && HELPER_KERNEL_VERSION_GTE_4_17_0
#define HELPER_KERNEL_PTREGS_SYSCALL_STUBS 1
#endif

typedef unsigned long (*kallsyms_lookup_name_t)(const char *name);

/*
    Get kallsyms_lookup_name function.

    Returns:
        Non-null    -> Success.
        NULL        -> Error.
*/
kallsyms_lookup_name_t helper_kernel_get_kallsyms_func(void);


#endif // _SPADE_AUDIT_HELPER_KERNEL_H