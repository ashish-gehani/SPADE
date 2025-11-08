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

#include <linux/init.h>
#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/param.h>
#include <linux/string.h>

#include "audit/audit.h"
#include "audit/arg/print.h"
#include "audit/exported/auditing.h"
#include "audit/global/global.h"
#include "audit/util/log/log.h"
#include "audit/util/log/module.h"


MODULE_LICENSE("GPL");


const char *SPADE_MODULE_NAME = "netio";


static int __init onload(void)
{
    const char *log_id = "__init onload";
    int err = 0;
    bool dry_run = false;

    util_log_module_loading_started();

    err = global_init(dry_run);
    if (err != 0)
    {
        util_log_warn(log_id, "Failed to load. Failed to initialize. Err: %d", err);
        goto exit_fail;
    }

    goto exit_success;

exit_fail:
    util_log_module_loading_failure();
    goto exit;

exit_success:
    util_log_module_loading_success();
    goto exit;

exit:
	return err;
}

static void __exit onunload(void)
{
    util_log_module_unloading_started();
    global_deinit();
    util_log_module_unloading_success();
}

module_init(onload);
module_exit(onunload);
