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
#include <linux/kernel.h>
#include <linux/string.h>
#include <linux/errno.h>

#include "spade/util/log/log.h"
#include "spade/audit/kernel/namespace/namespace.h"

#include "test/kernel/common.h"
#include "test/kernel/state.h"


static struct state state = {};


static void ensure_state_deinit(void)
{
    const char *log_id = "ensure_state_deinit";
    int err;
    bool inited;

    err = state_is_initialized(&inited, &state);
    if (err != 0)
    {
        util_log_warn(log_id, "Failed to reset state. Err: %d", err);
        return;
    }

    if (!inited)
    {
        return;
    }
    
    err = state_deinit(&state);
    if (err != 0)
    {
        util_log_warn(log_id, "Failed to deinit state. Err: %d", err);
        return;
    }
}

void test_state_init_basic(struct test_stats *stats)
{
    const char *test_name = "test_state_init_basic";
    int err;
    bool dry_run = false;
    struct kernel_namespace_pointers *k_ptrs;

    ensure_state_deinit();

    stats->total++;

    err = state_init(&state, dry_run);
    if (err)
    {
        TEST_FAIL(stats, test_name, "state_init returned %d", err);
        return;
    }

    if (!state.initialized)
    {
        TEST_FAIL(stats, test_name, "state not marked as initialized");
        ensure_state_deinit();
        return;
    }

    if (!state.netfilter.initialized)
    {
        TEST_FAIL(stats, test_name, "state.netfilter not marked as initialized");
        ensure_state_deinit();
        return;
    }

    if (!state.syscall.initialized)
    {
        TEST_FAIL(stats, test_name, "state.syscall not marked as initialized");
        ensure_state_deinit();
        return;
    }

    if (!state.syscall.ns.initialized)
    {
        TEST_FAIL(stats, test_name, "state.syscall.ns not marked as initialized");
        ensure_state_deinit();
        return;
    }

    k_ptrs = kernel_namespace_get_pointers();
    if (!k_ptrs)
    {
        TEST_FAIL(stats, test_name, "kernel_namespace_get_pointers returned NULL");
        ensure_state_deinit();
        return;
    }

    if (!k_ptrs->ops_cgroup)
    {
        TEST_FAIL(stats, test_name, "state.syscall.ns.ops_cgroup not initialized");
        ensure_state_deinit();
        return;
    }

    if (!k_ptrs->ops_ipc)
    {
        TEST_FAIL(stats, test_name, "state.syscall.ns.ops_ipc not initialized");
        ensure_state_deinit();
        return;
    }

    if (!k_ptrs->ops_mnt)
    {
        TEST_FAIL(stats, test_name, "state.syscall.ns.ops_mnt not initialized");
        ensure_state_deinit();
        return;
    }

    if (!k_ptrs->ops_net)
    {
        TEST_FAIL(stats, test_name, "state.syscall.ns.ops_net not initialized");
        ensure_state_deinit();
        return;
    }

    if (!k_ptrs->ops_pid)
    {
        TEST_FAIL(stats, test_name, "state.syscall.ns.ops_pid not initialized");
        ensure_state_deinit();
        return;
    }

    if (!k_ptrs->ops_user)
    {
        TEST_FAIL(stats, test_name, "state.syscall.ns.ops_user not initialized");
        ensure_state_deinit();
        return;
    }

    if (!state.syscall.hook.initialized)
    {
        TEST_FAIL(stats, test_name, "state.syscall.hook not marked as initialized");
        ensure_state_deinit();
        return;
    }

    if (!state.syscall.hook.ftrace.initialized)
    {
        TEST_FAIL(stats, test_name, "state.syscall.hook.ftrace not marked as initialized");
        ensure_state_deinit();
        return;
    }

    // Clean up
    err = state_deinit(&state);
    if (err)
    {
        TEST_FAIL(stats, test_name, "state_deinit returned %d", err);
        return;
    }

    if (state.initialized)
    {
        TEST_FAIL(stats, test_name, "state still marked as initialized after deinit");
        return;
    }

    TEST_PASS(stats, test_name);
}

int test_state_all(struct test_stats *stats)
{
    test_stats_init(stats);
    util_log_info("test_state", "Starting tests");

    test_state_init_basic(stats);
    return 0;
}