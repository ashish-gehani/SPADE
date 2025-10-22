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

#include "spade/audit/exported/auditing.h"
#include "spade/audit/param/param.h"
#include "spade/audit/arg/print.h"
#include "spade/util/log/log.h"
#include "spade/util/log/module.h"


MODULE_LICENSE("GPL");


extern const type_build_hash_t spade_build_hash;


const char *SPADE_MODULE_NAME = "netio_controller";


static struct arg global_local_arg;


static int _start(struct arg *arg)
{
    return exported_auditing_spade_start(spade_build_hash, arg);
}

static int _stop(void)
{
    return exported_auditing_spade_stop(spade_build_hash);
}

static int __init onload(void)
{
    const char *log_id = "__init onload";
    int err = 0;

    util_log_module_loading_started();

	if ((err = param_copy_validated_args(&global_local_arg)) != 0)
	{
		util_log_warn(log_id, "Failed to load module. Error in copying validated arguments: %d", err);
		goto exit_fail;
	}

    arg_print(&global_local_arg);

	if ((err = _start(&global_local_arg)) != 0)
	{
		util_log_warn(log_id, "Failed to load module. Error in starting auditing: %d", err);
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
    _stop();
    util_log_module_unloading_success();
}

module_init(onload);
module_exit(onunload);
