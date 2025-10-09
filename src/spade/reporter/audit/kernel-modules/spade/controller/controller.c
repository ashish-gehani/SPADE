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

#include "spade/controller/controller.h"
#include "spade/config/config.h"
#include "spade/arg/print.h"
#include "spade/controller/param.h"
#include "spade/exported/spade_audit.h"
#include "spade/util/log/log.h"
#include "spade/util/log/module.h"


MODULE_LICENSE("GPL");


const char *SPADE_MODULE_NAME = "netio_controller";


static int __init onload(void)
{
	const char *log_id = "__init onload";
	int ret;
	struct arg arg;

	util_log_module_loading_started();

	if ((ret = param_copy_validated_args(&arg)) != 0)
	{
		util_log_warn(log_id, "Failed to load module. Error in copying validated arguments: %d", ret);
		util_log_module_loading_failure();
		return ret;
	}

	arg_print(&arg);

	if ((ret = exported_spade_audit_start(&CONFIG_GLOBAL, &arg)) != 0)
	{
		util_log_warn(log_id, "Failed to load module. Error in starting auditing: %d", ret);
		util_log_module_loading_failure();
		return ret;
	}

	util_log_module_loading_success();
	return ret;
}

static void __exit onunload(void)
{
	const char *log_id = "__exit onunload";
	int err = 0;

	util_log_module_unloading_started();

	err = exported_spade_audit_stop(&CONFIG_GLOBAL);
	if (err != 0)
	{
		util_log_warn(log_id, "Failed to stop audit. Error in stopping auditing: %d", err);
		util_log_module_unloading_failure();
	} else
	{
		util_log_module_unloading_success();
	}
}

module_init(onload);
module_exit(onunload);
