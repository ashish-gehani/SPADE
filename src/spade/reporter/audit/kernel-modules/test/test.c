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

#include "spade/config/config.h"
#include "spade/arg/arg.h"
#include "spade/arg/parse.h"
#include "spade/arg/print.h"
#include "test/arg.h"
#include "test/context.h"

MODULE_LICENSE("GPL");


static int __init onload(void)
{
    struct test_stats t_s_arg;
    struct test_stats t_s_context;

	test_arg_all(&t_s_arg);
    test_context_all(&t_s_context);

    test_stats_log("test_arg", &t_s_arg);
    test_stats_log("test_context", &t_s_context);
    return -1;
}

static void __exit onunload(void)
{
}

module_init(onload);
module_exit(onunload);
