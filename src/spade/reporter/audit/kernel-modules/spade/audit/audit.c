/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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

#include "spade/audit/audit.h"
#include "spade/audit/param/param.h"
#include "spade/audit/arg/print.h"
#include "spade/audit/global/global.h"
#include "spade/util/log/log.h"
#include "spade/util/log/module.h"


MODULE_LICENSE("GPL");


const char *SPADE_MODULE_NAME = "netio";


static struct arg global_local_arg;


static bool ensure_global_initialization(const char *log_id)
{
    bool init;

    init = global_is_initialized();
    if (!init)
    {
        util_log_warn(log_id, "Failed to start auditing. Global not initialized.");
    }
    return init;
}

static int spade_audit_start(void)
{
    const char *log_id = "spade_audit_start";
    int err = 0;

    if (!ensure_global_initialization(log_id))
        return -EINVAL;

    err = global_auditing_start();
    if (err != 0)
    {
        util_log_warn(log_id, "Failed to start auditing.");
        return -EINVAL;
    }

    return 0;
}

static int spade_audit_stop(void)
{
    return global_auditing_stop();
}

static void _deinit_all(void)
{
    spade_audit_stop();
    global_deinit();
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

    err = global_init(&global_local_arg);
    if (err != 0)
    {
        util_log_warn(log_id, "Failed to load. Failed to initialize. Err: %d", err);
        goto exit_fail;
    }

	if ((err = spade_audit_start()) != 0)
	{
		util_log_warn(log_id, "Failed to load module. Error in starting auditing: %d", err);
		goto exit_fail_deinit;
	}

    goto exit_success;

exit_fail_deinit:
    global_deinit();
    goto exit_fail;

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
    _deinit_all();
    util_log_module_unloading_success();
}

module_init(onload);
module_exit(onunload);
