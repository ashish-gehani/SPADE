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
#include "spade/arg/print.h"
#include "spade/config/print.h"
#include "spade/audit/global/global.h"
#include "spade/util/log/log.h"
#include "spade/audit/helper/build_hash.h"
#include "spade/util/log/module.h"


MODULE_LICENSE("GPL");


const char *SPADE_MODULE_NAME = "netio";


static bool ensure_global_state_is_initialized(const char *log_id)
{
    bool state_module_initialized;

    state_module_initialized = global_is_state_initialized();
    if (!state_module_initialized)
    {
        util_log_warn(log_id, "Failed to start auditing. State is not initialized.");
    }
    return state_module_initialized;
}

static bool ensure_global_build_hash_matches(
    const char *log_id,
    const char *log_msg,
    const struct config_build_hash *build_hash
)
{
    bool matched = false;
    int err = 0;

    err = helper_build_build_hashes_match(
        &matched, build_hash, &CONFIG_GLOBAL.build_hash
    );

    if (err != 0 || !matched)
    {
        util_log_warn(log_id, log_msg);
        return false;
    }

    return true;
}

static int spade_audit_start(const struct config *config, struct arg *arg)
{
    const char *log_id = "spade_audit_start";
    int err = 0;

    if (!config || !arg)
    {
        util_log_warn(log_id, "Failed to start auditing. Invalid parameters.");
        return -EINVAL;
    }

    config_print(config);

    if (!ensure_global_build_hash_matches(
        log_id, "Failed to start auditing. Invalid hash.", &config->build_hash
    ))
        return -EINVAL;

    if (!ensure_global_state_is_initialized(log_id))
        return -EINVAL;

    arg_print(arg);

    // init context and start audit
    err = global_context_init(arg);
    if (err != 0)
    {
        util_log_warn(log_id, "Failed to start auditing. Context initialization failed.");
        return -EINVAL;
    }

    err = global_auditing_start();
    if (err != 0)
    {
        util_log_warn(log_id, "Failed to start auditing.");
        return -EINVAL;
    }

    return 0;
}

static int spade_audit_stop(const struct config *config)
{
    const char *log_id = "spade_audit_stop";
    if (!config)
    {
        util_log_warn(log_id, "Failed to stop auditing. Invalid parameters.");
        return -EINVAL;
    }

    config_print(config);

    if (!ensure_global_build_hash_matches(
        log_id, "Failed to stop auditing. Invalid hash.", &config->build_hash
    ))
        return -EINVAL;

    global_auditing_stop();
    global_context_deinit();

    return 0;
}

static void _deinit_all(void)
{
    spade_audit_stop(&CONFIG_GLOBAL);
    global_state_deinit();
}

static int __init onload(void)
{
    const char *log_id = "__init onload";
    int err = 0;
    struct arg arg;
    bool dry_run = false;

    util_log_module_loading_started();

    err = global_state_init(dry_run);
    if (err != 0)
    {
        util_log_warn(log_id, "Failed to load. State failed to initialize. Err: %d", err);
        goto exit_fail;
    }

	if ((err = param_copy_validated_args(&arg)) != 0)
	{
		util_log_warn(log_id, "Failed to load module. Error in copying validated arguments: %d", err);
		goto exit_fail_deinit_state;
	}

	if ((err = spade_audit_start(&CONFIG_GLOBAL, &arg)) != 0)
	{
		util_log_warn(log_id, "Failed to load module. Error in starting auditing: %d", err);
		goto exit_fail_deinit_state;
	}

    goto exit_success;

exit_fail_deinit_state:
    global_state_deinit();
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
