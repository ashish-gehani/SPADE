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

#include "spade/audit/kernel/syscall/hook/list.h"
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


const struct kernel_syscall_hook *KERNEL_SYSCALL_HOOK_LIST[] = {
    &kernel_syscall_hook_accept,
    &kernel_syscall_hook_accept4,
    &kernel_syscall_hook_bind,
    &kernel_syscall_hook_clone,
    &kernel_syscall_hook_connect,
    &kernel_syscall_hook_fork,
    &kernel_syscall_hook_kill,
    &kernel_syscall_hook_recvfrom,
    &kernel_syscall_hook_recvmsg,
    &kernel_syscall_hook_sendmsg,
    &kernel_syscall_hook_sendto,
    &kernel_syscall_hook_setns,
    &kernel_syscall_hook_unshare,
    &kernel_syscall_hook_vfork,
    0 // NULL-TERMINATED
};
