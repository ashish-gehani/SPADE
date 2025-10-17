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

#include "test/kernel/spade/audit/test.h"
#include "spade/audit/config/config.h"
#include "spade/audit/arg/arg.h"
#include "spade/audit/arg/parse.h"
#include "spade/audit/arg/print.h"
#include "spade/util/log/module.h"

#include "test/kernel/spade/audit/arg.h"
#include "test/kernel/spade/audit/context.h"
#include "test/kernel/spade/audit/global.h"
#include "test/kernel/spade/audit/msg.h"
#include "test/kernel/spade/audit/state.h"


MODULE_LICENSE("GPL");

const char* SPADE_MODULE_NAME = "spade_audit_test";

static int __init onload(void)
{
    struct test_stats t_s_arg;
    struct test_stats t_s_context;
    struct test_stats t_s_global;
    struct test_stats t_s_msg;
    struct test_stats t_s_state;

    util_log_module_loading_started();

	test_arg_all(&t_s_arg);
    test_context_all(&t_s_context);
    test_global_all(&t_s_global);
    test_msg_all(&t_s_msg);
    test_state_all(&t_s_state);

    test_stats_log("test_arg", &t_s_arg);
    test_stats_log("test_context", &t_s_context);
    test_stats_log("test_global", &t_s_global);
    test_stats_log("test_msg", &t_s_msg);
    test_stats_log("test_state", &t_s_state);

    util_log_module_loading_success();
    return 0;
}

static void __exit onunload(void)
{
    util_log_module_unloading_started();
    util_log_module_unloading_success();
}

module_init(onload);
module_exit(onunload);
