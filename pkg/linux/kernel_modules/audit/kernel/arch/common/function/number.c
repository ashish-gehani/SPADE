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

#include "audit/kernel/arch/common/function/number.h"

/* Arch-specific implementations (e.g. audit/kernel/arch/x86_64/function/number.c) provide a strong
 * definition of kernel_function_number_to_system_call_number() that overrides this one at link
 * time. This weak no-op definition exists so archs without one yet still link successfully. */
int __weak kernel_function_number_to_system_call_number(
    int *dst,
    enum kernel_function_number f_num,
    bool default_to_func_num
)
{
    return -ENOSYS;
}
