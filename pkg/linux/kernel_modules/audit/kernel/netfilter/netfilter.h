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

#ifndef SPADE_AUDIT_KERNEL_NETFILTER_H
#define SPADE_AUDIT_KERNEL_NETFILTER_H


#include <linux/kernel.h>
#include <linux/skbuff.h>
#include <linux/netfilter.h>


/*
   Function called first on all hook points.

   Definition: See (struct nf_hook_ops)->(nf_hookfn *hook).
*/
nf_hookfn kernel_netfilter_hook_first;

/*
   Function called last on all hook points.

   Definition: See (struct nf_hook_ops)->(nf_hookfn *hook).
*/
nf_hookfn kernel_netfilter_hook_last;

#endif // SPADE_AUDIT_KERNEL_NETFILTER_H