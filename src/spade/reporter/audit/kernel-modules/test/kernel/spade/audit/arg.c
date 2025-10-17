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

#include <linux/kernel.h>
#include <linux/string.h>

#include "test/kernel/spade/audit/arg.h"


static char g_too_big_array[2048];
static struct arg arg = {0};

static void _ensure_global_arg_is_reset(void)
{
	memset(&arg, 0, sizeof(arg));
}

static void init_too_big_array(void)
{
	size_t i;
	char *pos = g_too_big_array;

	for (i = 0; i < ARG_ARRAY_MAX + 1; i++)
	{
		pos += snprintf(pos, sizeof(g_too_big_array) - (pos - g_too_big_array), "%zu%s", i + 1, i < ARG_ARRAY_MAX ? "," : "");
	}
}

static void test_arg_print_null(struct test_stats *stats)
{
	const char *test_name = "test_arg_print_null";

	stats->total++;
	util_log_info(test_name, "Testing arg_print with NULL");
	arg_print(NULL);
	TEST_PASS(stats, test_name);
}

static void test_arg_print_empty(struct test_stats *stats)
{
	const char *test_name = "test_arg_print_empty";
	_ensure_global_arg_is_reset();

	stats->total++;
	util_log_info(test_name, "Testing arg_print with empty struct");
	arg_print(&arg);
	TEST_PASS(stats, test_name);
}

static void test_arg_parse_bool(struct test_stats *stats)
{
	const char *test_name = "test_arg_parse_bool";
	int err;
	bool original_value;
	_ensure_global_arg_is_reset();

	stats->total++;

	err = arg_parse_bool(test_name, "network_io", "1", &arg.network_io);
	if (err) {
		TEST_FAIL(stats, test_name, "parse '1' (true) returned %d", err);
		return;
	}
	if (arg.network_io != true) {
		TEST_FAIL(stats, test_name, "Expected true, got %d", arg.network_io);
		return;
	}

	err = arg_parse_bool(test_name, "include_ns_info", "0", &arg.include_ns_info);
	if (err) {
		TEST_FAIL(stats, test_name, "parse '0' (false) returned %d", err);
		return;
	}
	if (arg.include_ns_info != false) {
		TEST_FAIL(stats, test_name, "Expected false, got %d", arg.include_ns_info);
		return;
	}

	// Negative test: invalid boolean string
	original_value = arg.network_io;
	err = arg_parse_bool(test_name, "network_io", "invalid", &arg.network_io);
	if (!err) {
		TEST_FAIL(stats, test_name, "Expected parse failure for 'invalid' but succeeded");
		return;
	}
	if (arg.network_io != original_value) {
		TEST_FAIL(stats, test_name, "Value modified on failed parse");
		return;
	}

	// Negative test: numeric string
	err = arg_parse_bool(test_name, "network_io", "123", &arg.network_io);
	if (!err) {
		TEST_FAIL(stats, test_name, "Expected parse failure for '123' but succeeded");
		return;
	}

	// Negative test: empty string
	err = arg_parse_bool(test_name, "network_io", "", &arg.network_io);
	if (!err) {
		TEST_FAIL(stats, test_name, "Expected parse failure for empty string but succeeded");
		return;
	}

	util_log_info(test_name, "Testing arg_parse_bool");
	arg_print(&arg);
	TEST_PASS(stats, test_name);
}

static void test_arg_parse_monitor_mode(struct test_stats *stats)
{
	const char *test_name = "test_arg_parse_monitor_mode";
	int err;
	enum arg_monitor_mode original_value;
	_ensure_global_arg_is_reset();

	stats->total++;

	// Test AMM_CAPTURE (0)
	err = arg_parse_monitor_mode(test_name, "uid_monitor_mode", "0", &arg.user.uid_monitor_mode);
	if (err)
	{
		TEST_FAIL(stats, test_name, "parse '0' (AMM_CAPTURE) returned %d", err);
		return;
	}
	if (arg.user.uid_monitor_mode != AMM_CAPTURE)
	{
		TEST_FAIL(stats, test_name, "Expected AMM_CAPTURE, got %d", arg.user.uid_monitor_mode);
		return;
	}

	// Test AMM_IGNORE (1)
	err = arg_parse_monitor_mode(test_name, "uid_monitor_mode", "1", &arg.user.uid_monitor_mode);
	if (err)
	{
		TEST_FAIL(stats, test_name, "parse '1' (AMM_IGNORE) returned %d", err);
		return;
	}
	if (arg.user.uid_monitor_mode != AMM_IGNORE)
	{
		TEST_FAIL(stats, test_name, "Expected AMM_IGNORE, got %d", arg.user.uid_monitor_mode);
		return;
	}

	// Negative test: invalid monitor mode
	original_value = arg.user.uid_monitor_mode;
	err = arg_parse_monitor_mode(test_name, "uid_monitor_mode", "invalid", &arg.user.uid_monitor_mode);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for 'invalid' but succeeded");
		return;
	}
	if (arg.user.uid_monitor_mode != original_value)
	{
		TEST_FAIL(stats, test_name, "Value modified on failed parse");
		return;
	}

	// Negative test: empty string
	err = arg_parse_monitor_mode(test_name, "uid_monitor_mode", "", &arg.user.uid_monitor_mode);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for empty string but succeeded");
		return;
	}

	// Negative test: invalid numeric value
	err = arg_parse_monitor_mode(test_name, "uid_monitor_mode", "123", &arg.user.uid_monitor_mode);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for '123' but succeeded");
		return;
	}

	util_log_info(test_name, "Testing arg_parse_monitor_mode");
	arg_print(&arg);
	TEST_PASS(stats, test_name);
}

static void test_arg_parse_monitor_syscalls(struct test_stats *stats)
{
	const char *test_name = "test_arg_parse_monitor_syscalls";
	int err;
	enum arg_monitor_syscalls original_value;
	_ensure_global_arg_is_reset();

	stats->total++;

	// Test AMMS_ALL (-1)
	err = arg_parse_monitor_syscalls(test_name, "monitor_syscalls", "-1", &arg.monitor_syscalls);
	if (err)
	{
		TEST_FAIL(stats, test_name, "parse '-1' (AMMS_ALL) returned %d", err);
		return;
	}
	if (arg.monitor_syscalls != AMMS_ALL)
	{
		TEST_FAIL(stats, test_name, "Expected AMMS_ALL, got %d", arg.monitor_syscalls);
		return;
	}

	// Test AMMS_ONLY_FAILED (0)
	err = arg_parse_monitor_syscalls(test_name, "monitor_syscalls", "0", &arg.monitor_syscalls);
	if (err)
	{
		TEST_FAIL(stats, test_name, "parse '0' (AMMS_ONLY_FAILED) returned %d", err);
		return;
	}
	if (arg.monitor_syscalls != AMMS_ONLY_FAILED)
	{
		TEST_FAIL(stats, test_name, "Expected AMMS_ONLY_FAILED, got %d", arg.monitor_syscalls);
		return;
	}

	// Test AMMS_ONLY_SUCCESSFUL (1)
	err = arg_parse_monitor_syscalls(test_name, "monitor_syscalls", "1", &arg.monitor_syscalls);
	if (err)
	{
		TEST_FAIL(stats, test_name, "parse '1' (AMMS_ONLY_SUCCESSFUL) returned %d", err);
		return;
	}
	if (arg.monitor_syscalls != AMMS_ONLY_SUCCESSFUL)
	{
		TEST_FAIL(stats, test_name, "Expected AMMS_ONLY_SUCCESSFUL, got %d", arg.monitor_syscalls);
		return;
	}

	// Negative test: invalid monitor syscalls
	original_value = arg.monitor_syscalls;
	err = arg_parse_monitor_syscalls(test_name, "monitor_syscalls", "invalid", &arg.monitor_syscalls);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for 'invalid' but succeeded");
		return;
	}
	if (arg.monitor_syscalls != original_value)
	{
		TEST_FAIL(stats, test_name, "Value modified on failed parse");
		return;
	}

	// Negative test: empty string
	err = arg_parse_monitor_syscalls(test_name, "monitor_syscalls", "", &arg.monitor_syscalls);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for empty string but succeeded");
		return;
	}

	// Negative test: invalid numeric value
	err = arg_parse_monitor_syscalls(test_name, "monitor_syscalls", "999", &arg.monitor_syscalls);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for '999' but succeeded");
		return;
	}

	util_log_info(test_name, "Testing arg_parse_monitor_syscalls");
	arg_print(&arg);
	TEST_PASS(stats, test_name);
}

static void test_arg_parse_monitor_connections(struct test_stats *stats)
{
	const char *test_name = "test_arg_parse_monitor_connections";
	int err;
	enum arg_monitor_connections original_value;
	_ensure_global_arg_is_reset();

	stats->total++;

	// Test AMMC_ALL (-1)
	err = arg_parse_monitor_connections(test_name, "monitor_ct", "-1", &arg.nf.monitor_ct);
	if (err)
	{
		TEST_FAIL(stats, test_name, "parse '-1' (AMMC_ALL) returned %d", err);
		return;
	}
	if (arg.nf.monitor_ct != AMMC_ALL)
	{
		TEST_FAIL(stats, test_name, "Expected AMMC_ALL, got %d", arg.nf.monitor_ct);
		return;
	}

	// Test AMMC_ONLY_NEW (0)
	err = arg_parse_monitor_connections(test_name, "monitor_ct", "0", &arg.nf.monitor_ct);
	if (err)
	{
		TEST_FAIL(stats, test_name, "parse '0' (AMMC_ONLY_NEW) returned %d", err);
		return;
	}
	if (arg.nf.monitor_ct != AMMC_ONLY_NEW)
	{
		TEST_FAIL(stats, test_name, "Expected AMMC_ONLY_NEW, got %d", arg.nf.monitor_ct);
		return;
	}

	// Negative test: invalid monitor connections
	original_value = arg.nf.monitor_ct;
	err = arg_parse_monitor_connections(test_name, "monitor_ct", "invalid", &arg.nf.monitor_ct);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for 'invalid' but succeeded");
		return;
	}
	if (arg.nf.monitor_ct != original_value)
	{
		TEST_FAIL(stats, test_name, "Value modified on failed parse");
		return;
	}

	// Negative test: empty string
	err = arg_parse_monitor_connections(test_name, "monitor_ct", "", &arg.nf.monitor_ct);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for empty string but succeeded");
		return;
	}

	// Negative test: invalid numeric value
	err = arg_parse_monitor_connections(test_name, "monitor_ct", "42", &arg.nf.monitor_ct);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for '42' but succeeded");
		return;
	}

	util_log_info(test_name, "Testing arg_parse_monitor_connections");
	arg_print(&arg);
	TEST_PASS(stats, test_name);
}

static void test_arg_parse_pid_array(struct test_stats *stats)
{
	const char *test_name = "test_arg_parse_pid_array";
	int err;
	_ensure_global_arg_is_reset();

	stats->total++;

	err = arg_parse_pid_array(test_name, "ignore_pids", "100,200,300", &arg.ignore_pids);
	if (err)
	{
		TEST_FAIL(stats, test_name, "parse PIDs returned %d", err);
		return;
	}
	if (arg.ignore_pids.len != 3)
	{
		TEST_FAIL(stats, test_name, "Expected 3 PIDs, got %zu", arg.ignore_pids.len);
		return;
	}
	if (arg.ignore_pids.arr[0] != 100 || arg.ignore_pids.arr[1] != 200 || arg.ignore_pids.arr[2] != 300)
	{
		TEST_FAIL(stats, test_name, "PID values incorrect: [%d,%d,%d]", arg.ignore_pids.arr[0], arg.ignore_pids.arr[1], arg.ignore_pids.arr[2]);
		return;
	}

	// Negative test: empty array
	err = arg_parse_pid_array(test_name, "ignore_ppids", "", &arg.ignore_ppids);
	if (err)
	{
		TEST_FAIL(stats, test_name, "Parse failure for empty string");
		return;
	}
	if (arg.ignore_ppids.len != 0)
	{
		TEST_FAIL(stats, test_name, "Expected 0 PIDs, got %zu", arg.ignore_ppids.len);
		return;
	}

	// Negative test: invalid array (non-numeric)
	err = arg_parse_pid_array(test_name, "ignore_ppids", "100,abc,300", &arg.ignore_ppids);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for invalid PID 'abc' but succeeded");
		return;
	}

	// Negative test: array exceeding ARG_ARRAY_MAX (64)
	err = arg_parse_pid_array(test_name, "ignore_ppids", g_too_big_array, &arg.ignore_ppids);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for array size > ARG_ARRAY_MAX but succeeded");
		return;
	}

	util_log_info(test_name, "Testing arg_parse_pid_array");
	arg_print(&arg);
	TEST_PASS(stats, test_name);
}

static void test_arg_parse_uid_array(struct test_stats *stats)
{
	const char *test_name = "test_arg_parse_uid_array";
	int err;
	_ensure_global_arg_is_reset();

	stats->total++;

	err = arg_parse_uid_array(test_name, "uids", "1000,1001,1002", &arg.user.uids);
	if (err)
	{
		TEST_FAIL(stats, test_name, "parse UIDs returned %d", err);
		return;
	}
	if (arg.user.uids.len != 3)
	{
		TEST_FAIL(stats, test_name, "Expected 3 UIDs, got %zu", arg.user.uids.len);
		return;
	}
	if (arg.user.uids.arr[0] != 1000 || arg.user.uids.arr[1] != 1001 || arg.user.uids.arr[2] != 1002)
	{
		TEST_FAIL(stats, test_name, "UID values incorrect: [%u,%u,%u]", arg.user.uids.arr[0], arg.user.uids.arr[1], arg.user.uids.arr[2]);
		return;
	}

	err = arg_parse_monitor_mode(test_name, "uid_monitor_mode", "0", &arg.user.uid_monitor_mode);
	if (err)
	{
		TEST_FAIL(stats, test_name, "parse monitor mode returned %d", err);
		return;
	}
	if (arg.user.uid_monitor_mode != AMM_CAPTURE)
	{
		TEST_FAIL(stats, test_name, "Expected AMM_CAPTURE, got %d", arg.user.uid_monitor_mode);
		return;
	}

	// Negative test: empty array
	arg.user.uids.len = 0;
	err = arg_parse_uid_array(test_name, "uids", "", &arg.user.uids);
	if (err)
	{
		TEST_FAIL(stats, test_name, "Parse failure for empty string");
		return;
	}
	if (arg.user.uids.len != 0)
	{
		TEST_FAIL(stats, test_name, "Expected 0 UIDs, got %zu", arg.user.uids.len);
		return;
	}

	// Negative test: invalid array (non-numeric)
	err = arg_parse_uid_array(test_name, "uids", "1000,invalid,1002", &arg.user.uids);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for invalid UID 'invalid' but succeeded");
		return;
	}

	// Negative test: negative number (UID is unsigned)
	err = arg_parse_uid_array(test_name, "uids", "1000,-1,1002", &arg.user.uids);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for negative UID but succeeded");
		return;
	}

	// Negative test: array exceeding ARG_ARRAY_MAX (64)
	err = arg_parse_uid_array(test_name, "uids", g_too_big_array, &arg.user.uids);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for array size > ARG_ARRAY_MAX but succeeded");
		return;
	}

	util_log_info(test_name, "Testing arg_parse_uid_array");
	arg_print(&arg);
	TEST_PASS(stats, test_name);
}

static void test_arg_config_file(struct test_stats *stats)
{
	const char *test_name = "test_arg_config_file";
	const char *test_path = "/opt/spade/audit/audit.config";
	const char *test_path_custom = "/custom/path/to/config.cfg";
	_ensure_global_arg_is_reset();

	stats->total++;

	// Test setting a valid config file path
	strncpy(arg.config_file, test_path, PATH_MAX - 1);
	arg.config_file[PATH_MAX - 1] = '\0';

	if (strcmp(arg.config_file, test_path) != 0)
	{
		TEST_FAIL(stats, test_name, "Config file path mismatch. Expected '%s', got '%s'", test_path, arg.config_file);
		return;
	}

	// Test setting a custom config file path
	strncpy(arg.config_file, test_path_custom, PATH_MAX - 1);
	arg.config_file[PATH_MAX - 1] = '\0';

	if (strcmp(arg.config_file, test_path_custom) != 0)
	{
		TEST_FAIL(stats, test_name, "Custom config file path mismatch. Expected '%s', got '%s'", test_path_custom, arg.config_file);
		return;
	}

	// Test that path is properly null-terminated
	if (strlen(arg.config_file) >= PATH_MAX)
	{
		TEST_FAIL(stats, test_name, "Config file path not properly bounded");
		return;
	}

	util_log_info(test_name, "Testing config_file field");
	arg_print(&arg);
	TEST_PASS(stats, test_name);
}

int test_arg_all(struct test_stats *stats)
{
	if (!stats)
	{
		return 0;
	}

	test_stats_init(stats);
	util_log_info("test_arg", "Starting tests");

	init_too_big_array();

	test_arg_print_null(stats);
	test_arg_print_empty(stats);
	test_arg_parse_bool(stats);
	test_arg_parse_monitor_mode(stats);
	test_arg_parse_monitor_syscalls(stats);
	test_arg_parse_monitor_connections(stats);
	test_arg_parse_pid_array(stats);
	test_arg_parse_uid_array(stats);
	test_arg_config_file(stats);

	return 0;
}
