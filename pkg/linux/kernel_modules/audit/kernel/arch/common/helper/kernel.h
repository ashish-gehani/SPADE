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

#ifndef _SPADE_AUDIT_KERNEL_ARCH_COMMON_HELPER_KERNEL_H
#define _SPADE_AUDIT_KERNEL_ARCH_COMMON_HELPER_KERNEL_H

#include <linux/init.h>
#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/version.h>
#include <linux/types.h>


#define KERNEL_HELPER_KERNEL_VERSION_GTE_4_11_0 LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0)

#define KERNEL_HELPER_KERNEL_VERSION_GTE_4_17_0 LINUX_VERSION_CODE >= KERNEL_VERSION(4, 17, 0)

#define KERNEL_HELPER_KERNEL_VERSION_GTE_4_19_0 LINUX_VERSION_CODE >= KERNEL_VERSION(4, 19, 0)

#define KERNEL_HELPER_KERNEL_VERSION_GTE_5_7_0 LINUX_VERSION_CODE >= KERNEL_VERSION(5, 7, 0)

#define KERNEL_HELPER_KERNEL_VERSION_GTE_5_11_0 LINUX_VERSION_CODE >= KERNEL_VERSION(5,11,0)

/* From this version onward, the ftrace_ops function callback (e.g. fh_ftrace_thunk()) receives a
 * struct ftrace_regs *, not a struct pt_regs * -- see https://elixir.bootlin.com/linux/v5.11-rc1/A/ident/ftrace_regs */
#define KERNEL_HELPER_KERNEL_FTRACE_THUNK_HAS_FTRACE_REGS KERNEL_HELPER_KERNEL_VERSION_GTE_5_11_0

/* kallsyms_lookup_name() stopped being exported from this version onward, requiring the kprobe-based
 * lookup hack instead -- see the KPROBE_LOOKUP branch in kernel_helper_kernel_get_kallsyms_func(). */
#define KERNEL_HELPER_KERNEL_KALLSYMS_NOT_EXPORTED KERNEL_HELPER_KERNEL_VERSION_GTE_5_7_0

typedef unsigned long (*kallsyms_lookup_name_t)(const char *name);

/*
    Get kallsyms_lookup_name function.

    Returns:
        Non-null    -> Success.
        NULL        -> Error.
*/
kallsyms_lookup_name_t kernel_helper_kernel_get_kallsyms_func(void);


#endif // _SPADE_AUDIT_KERNEL_ARCH_COMMON_HELPER_KERNEL_H
