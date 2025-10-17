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

#ifndef SPADE_AUDIT_CONFIG_CONFIG_H
#define SPADE_AUDIT_CONFIG_CONFIG_H

#include <linux/types.h>

enum config_syscall_hook_type
{
    CONFIG_SYSCALL_HOOK_TABLE,
    CONFIG_SYSCALL_HOOK_FTRACE
};

struct config
{
    bool debug;
    enum config_syscall_hook_type sys_hook_type;
};

extern const struct config CONFIG_GLOBAL;

#endif // SPADE_AUDIT_CONFIG_CONFIG_H