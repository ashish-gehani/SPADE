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

#ifndef SPADE_AUDIT_KERNEL_SYSCALL_HOOK_LIST_H
#define SPADE_AUDIT_KERNEL_SYSCALL_HOOK_LIST_H

#include <linux/types.h>

#include "spade/audit/kernel/syscall/hook/hook.h"
#include "spade/audit/kernel/syscall/hook/function/accept/accept.h"
#include "spade/audit/kernel/syscall/hook/function/accept4/accept4.h"
#include "spade/audit/kernel/syscall/hook/function/bind/bind.h"
#include "spade/audit/kernel/syscall/hook/function/clone/clone.h"
#include "spade/audit/kernel/syscall/hook/function/connect/connect.h"
#include "spade/audit/kernel/syscall/hook/function/fork/fork.h"
#include "spade/audit/kernel/syscall/hook/function/kill/kill.h"
#include "spade/audit/kernel/syscall/hook/function/recvfrom/recvfrom.h"
#include "spade/audit/kernel/syscall/hook/function/recvmsg/recvmsg.h"
#include "spade/audit/kernel/syscall/hook/function/sendmsg/sendmsg.h"
#include "spade/audit/kernel/syscall/hook/function/sendto/sendto.h"
#include "spade/audit/kernel/syscall/hook/function/setns/setns.h"
#include "spade/audit/kernel/syscall/hook/function/unshare/unshare.h"
#include "spade/audit/kernel/syscall/hook/function/vfork/vfork.h"


extern const struct kernel_syscall_hook KERNEL_SYSCALL_HOOK_LIST[];


#define KERNEL_SYSCALL_HOOK_LIST_LEN 14 // todo... use const

#endif // SPADE_AUDIT_KERNEL_SYSCALL_HOOK_LIST_H