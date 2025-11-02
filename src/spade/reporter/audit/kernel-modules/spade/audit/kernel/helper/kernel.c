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


#include <linux/atomic.h>
#include <linux/errno.h>
#include <linux/kernel.h>
#include <linux/version.h>

#include "spade/audit/kernel/helper/kernel.h"
#include "spade/util/log/log.h"


// Kallsyms hack @hkerma
// https://lwn.net/Articles/813350/
#if KERNEL_HELPER_KERNEL_VERSION_GTE_5_7_0
	#define KPROBE_LOOKUP 1
	#include <linux/kprobes.h>
	static struct kprobe kp = {
		.symbol_name = "kallsyms_lookup_name"
	};
#else
	#include <linux/kallsyms.h>
#endif


kallsyms_lookup_name_t kernel_helper_kernel_get_kallsyms_func(void)
{
	// Kallsyms hack @hkerma
	#ifdef KPROBE_LOOKUP
		/* typedef for kallsyms_lookup_name() so we can easily cast kp.addr */
		kallsyms_lookup_name_t kallsyms_lookup_name;

		/* register the kprobe */
		register_kprobe(&kp);

		/* assign kallsyms_lookup_name symbol to kp.addr */
		kallsyms_lookup_name = (kallsyms_lookup_name_t) kp.addr;

		/* done with the kprobe, so unregister it */
		unregister_kprobe(&kp);
	#endif

    return kallsyms_lookup_name;
}
