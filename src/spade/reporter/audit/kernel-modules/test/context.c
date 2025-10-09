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

#include <linux/kernel.h>
#include <linux/string.h>
#include <linux/errno.h>

#include "spade/audit/context/context.h"
#include "spade/arg/arg.h"
#include "spade/arg/parse.h"
#include "test/common.h"
#include "test/context.h"


struct arg arg;
struct context ctx;


static void reset_arg_and_ctx(void)
{
    memset(&arg, 0, sizeof(arg));
    memset(&ctx, 0, sizeof(ctx));
}

void test_context_init_basic(struct test_stats *stats)
{
    const char *test_name = "test_context_init_basic";
    int err;

    reset_arg_and_ctx();

    stats->total++;

    // Set up arg with known values
    arg.network_io = true;
    arg.include_ns_info = false;
    arg.monitor_syscalls = AMMS_ALL;
    arg.nf.monitor_ct = AMMC_ALL;
    arg.nf.use_user = true;

    // Initialize context from arg
    err = context_init(&ctx, &arg);
    if (err)
    {
        TEST_FAIL(stats, test_name, "context_init returned %d", err);
        return;
    }

    // Verify context is initialized
    if (!ctx.initialized)
    {
        TEST_FAIL(stats, test_name, "context not marked as initialized");
        context_deinit(&ctx);
        return;
    }

    // Verify syscall context values
    if (!ctx.syscall.initialized)
    {
        TEST_FAIL(stats, test_name, "syscall context not initialized");
        context_deinit(&ctx);
        return;
    }

    if (ctx.syscall.network_io != true)
    {
        TEST_FAIL(stats, test_name, "syscall.network_io: expected true, got %d", ctx.syscall.network_io);
        context_deinit(&ctx);
        return;
    }

    if (ctx.syscall.include_ns_info != false)
    {
        TEST_FAIL(stats, test_name, "syscall.include_ns_info: expected false, got %d", ctx.syscall.include_ns_info);
        context_deinit(&ctx);
        return;
    }

    if (ctx.syscall.monitor_syscalls != AMMS_ALL)
    {
        TEST_FAIL(stats, test_name, "syscall.monitor_syscalls: expected AMMS_ALL, got %d", ctx.syscall.monitor_syscalls);
        context_deinit(&ctx);
        return;
    }

    // Verify netfilter context values
    if (!ctx.netfilter.initialized)
    {
        TEST_FAIL(stats, test_name, "netfilter context not initialized");
        context_deinit(&ctx);
        return;
    }

    if (ctx.netfilter.monitor_ct != AMMC_ALL)
    {
        TEST_FAIL(stats, test_name, "netfilter.monitor_ct: expected AMMC_ALL, got %d", ctx.netfilter.monitor_ct);
        context_deinit(&ctx);
        return;
    }

    if (ctx.netfilter.use_user != true)
    {
        TEST_FAIL(stats, test_name, "netfilter.use_user: expected true, got %d", ctx.netfilter.use_user);
        context_deinit(&ctx);
        return;
    }

    // Clean up
    err = context_deinit(&ctx);
    if (err)
    {
        TEST_FAIL(stats, test_name, "context_deinit returned %d", err);
        return;
    }

    if (ctx.initialized)
    {
        TEST_FAIL(stats, test_name, "context still marked as initialized after deinit");
        return;
    }

    TEST_PASS(stats, test_name);
}

void test_context_init_with_arrays(struct test_stats *stats)
{
    const char *test_name = "test_context_init_with_arrays";
    int err;

    reset_arg_and_ctx();

    stats->total++;

    // Set up arg with array values
    arg.ignore_pids.arr[0] = 100;
    arg.ignore_pids.arr[1] = 200;
    arg.ignore_pids.arr[2] = 300;
    arg.ignore_pids.len = 3;

    arg.ignore_ppids.arr[0] = 1000;
    arg.ignore_ppids.arr[1] = 2000;
    arg.ignore_ppids.len = 2;

    arg.user.uids.arr[0] = 500;
    arg.user.uids.arr[1] = 600;
    arg.user.uids.len = 2;
    arg.user.uid_monitor_mode = AMM_CAPTURE;

    arg.network_io = true;
    arg.monitor_syscalls = AMMS_ONLY_SUCCESSFUL;

    // Initialize context from arg
    err = context_init(&ctx, &arg);
    if (err)
    {
        TEST_FAIL(stats, test_name, "context_init returned %d", err);
        return;
    }

    // Verify ignore_pids array
    if (ctx.syscall.ignore_pids.len != 3)
    {
        TEST_FAIL(stats, test_name, "ignore_pids.len: expected 3, got %zu", ctx.syscall.ignore_pids.len);
        context_deinit(&ctx);
        return;
    }

    if (ctx.syscall.ignore_pids.arr[0] != 100 ||
        ctx.syscall.ignore_pids.arr[1] != 200 ||
        ctx.syscall.ignore_pids.arr[2] != 300)
    {
        TEST_FAIL(stats, test_name, "ignore_pids values incorrect: [%d,%d,%d]",
                  ctx.syscall.ignore_pids.arr[0],
                  ctx.syscall.ignore_pids.arr[1],
                  ctx.syscall.ignore_pids.arr[2]);
        context_deinit(&ctx);
        return;
    }

    // Verify ignore_ppids array
    if (ctx.syscall.ignore_ppids.len != 2)
    {
        TEST_FAIL(stats, test_name, "ignore_ppids.len: expected 2, got %zu", ctx.syscall.ignore_ppids.len);
        context_deinit(&ctx);
        return;
    }

    if (ctx.syscall.ignore_ppids.arr[0] != 1000 ||
        ctx.syscall.ignore_ppids.arr[1] != 2000)
    {
        TEST_FAIL(stats, test_name, "ignore_ppids values incorrect: [%d,%d]",
                  ctx.syscall.ignore_ppids.arr[0],
                  ctx.syscall.ignore_ppids.arr[1]);
        context_deinit(&ctx);
        return;
    }

    // Verify user uids array (syscall context)
    if (ctx.syscall.user.uids.len != 2)
    {
        TEST_FAIL(stats, test_name, "syscall.user.uids.len: expected 2, got %zu", ctx.syscall.user.uids.len);
        context_deinit(&ctx);
        return;
    }

    if (ctx.syscall.user.uids.arr[0] != 500 ||
        ctx.syscall.user.uids.arr[1] != 600)
    {
        TEST_FAIL(stats, test_name, "syscall.user.uids values incorrect: [%u,%u]",
                  ctx.syscall.user.uids.arr[0],
                  ctx.syscall.user.uids.arr[1]);
        context_deinit(&ctx);
        return;
    }

    if (ctx.syscall.user.uid_monitor_mode != AMM_CAPTURE)
    {
        TEST_FAIL(stats, test_name, "syscall.user.uid_monitor_mode: expected AMM_CAPTURE, got %d",
                  ctx.syscall.user.uid_monitor_mode);
        context_deinit(&ctx);
        return;
    }

    // Verify user uids array (netfilter context)
    if (ctx.netfilter.user.uids.len != 2)
    {
        TEST_FAIL(stats, test_name, "netfilter.user.uids.len: expected 2, got %zu", ctx.netfilter.user.uids.len);
        context_deinit(&ctx);
        return;
    }

    if (ctx.netfilter.user.uids.arr[0] != 500 ||
        ctx.netfilter.user.uids.arr[1] != 600)
    {
        TEST_FAIL(stats, test_name, "netfilter.user.uids values incorrect: [%u,%u]",
                  ctx.netfilter.user.uids.arr[0],
                  ctx.netfilter.user.uids.arr[1]);
        context_deinit(&ctx);
        return;
    }

    if (ctx.netfilter.user.uid_monitor_mode != AMM_CAPTURE)
    {
        TEST_FAIL(stats, test_name, "netfilter.user.uid_monitor_mode: expected AMM_CAPTURE, got %d",
                  ctx.netfilter.user.uid_monitor_mode);
        context_deinit(&ctx);
        return;
    }

    // Clean up
    err = context_deinit(&ctx);
    if (err)
    {
        TEST_FAIL(stats, test_name, "context_deinit returned %d", err);
        return;
    }

    TEST_PASS(stats, test_name);
}

void test_context_init_null_arg(struct test_stats *stats)
{
    const char *test_name = "test_context_init_null_arg";
    int err;

    reset_arg_and_ctx();

    stats->total++;

    // Test with null arg
    err = context_init(&ctx, NULL);
    if (err != -EINVAL)
    {
        TEST_FAIL(stats, test_name, "Expected -EINVAL, got %d", err);
        return;
    }

    TEST_PASS(stats, test_name);
}

void test_context_init_null_context(struct test_stats *stats)
{
    const char *test_name = "test_context_init_null_context";
    int err;

    reset_arg_and_ctx();

    stats->total++;

    // Test with null context
    err = context_init(NULL, &arg);
    if (err != -EINVAL)
    {
        TEST_FAIL(stats, test_name, "Expected -EINVAL, got %d", err);
        return;
    }

    TEST_PASS(stats, test_name);
}

void test_context_double_init(struct test_stats *stats)
{
    const char *test_name = "test_context_double_init";
    int err;

    reset_arg_and_ctx();

    stats->total++;

    // First init
    err = context_init(&ctx, &arg);
    if (err)
    {
        TEST_FAIL(stats, test_name, "First context_init returned %d", err);
        return;
    }

    // Second init should fail with -EALREADY
    err = context_init(&ctx, &arg);
    if (err != -EALREADY)
    {
        TEST_FAIL(stats, test_name, "Expected -EALREADY, got %d", err);
        context_deinit(&ctx);
        return;
    }

    // Clean up
    err = context_deinit(&ctx);
    if (err)
    {
        TEST_FAIL(stats, test_name, "context_deinit returned %d", err);
        return;
    }

    TEST_PASS(stats, test_name);
}

void test_context_is_initialized(struct test_stats *stats)
{
    const char *test_name = "test_context_is_initialized";
    bool is_init;
    int err;

    reset_arg_and_ctx();

    stats->total++;

    // Check uninitialized context
    err = context_is_initialized(&is_init, &ctx);
    if (err)
    {
        TEST_FAIL(stats, test_name, "context_is_initialized returned %d", err);
        return;
    }

    if (is_init)
    {
        TEST_FAIL(stats, test_name, "Uninitialized context reported as initialized");
        return;
    }

    // Initialize context
    err = context_init(&ctx, &arg);
    if (err)
    {
        TEST_FAIL(stats, test_name, "context_init returned %d", err);
        return;
    }

    // Check initialized context
    err = context_is_initialized(&is_init, &ctx);
    if (err)
    {
        TEST_FAIL(stats, test_name, "context_is_initialized after init returned %d", err);
        context_deinit(&ctx);
        return;
    }

    if (!is_init)
    {
        TEST_FAIL(stats, test_name, "Initialized context reported as not initialized");
        context_deinit(&ctx);
        return;
    }

    // Clean up
    err = context_deinit(&ctx);
    if (err)
    {
        TEST_FAIL(stats, test_name, "context_deinit returned %d", err);
        return;
    }

    // Check deinitialized context
    err = context_is_initialized(&is_init, &ctx);
    if (err)
    {
        TEST_FAIL(stats, test_name, "context_is_initialized after deinit returned %d", err);
        return;
    }

    if (is_init)
    {
        TEST_FAIL(stats, test_name, "Deinitialized context reported as initialized");
        return;
    }

    TEST_PASS(stats, test_name);
}

int test_context_all(struct test_stats *stats)
{
    test_stats_init(stats);
    util_log_info("test_context", "Starting tests");

    test_context_init_basic(stats);
    test_context_init_with_arrays(stats);
    test_context_init_null_arg(stats);
    test_context_init_null_context(stats);
    test_context_double_init(stats);
    test_context_is_initialized(stats);
    return 0;
}