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

#include "spade/audit/type/parse.h"
#include "spade/audit/type/print.h"

#include "test/kernel/spade/audit/arg.h"


static char global_uid_array_str[2048];
static char global_pid_array_str[2048];

static struct arg arg = {0};

static void _ensure_global_arg_is_reset(void)
{
	memset(&arg, 0, sizeof(arg));
}

static void init_global_uid_array_str(void)
{
	uid_t i;
	char *pos = global_uid_array_str;

	for (i = 0; i < TYPE_ARRAY_UID_MAX_LEN + 1; i++)
	{
		pos += snprintf(pos, sizeof(global_uid_array_str) - (pos - global_uid_array_str), "%u%s", i + 1, i < TYPE_ARRAY_UID_MAX_LEN ? "," : "");
	}
}

static void init_global_pid_array_str(void)
{
	pid_t i;
	char *pos = global_pid_array_str;

	for (i = 0; i < TYPE_ARRAY_PID_MAX_LEN + 1; i++)
	{
		pos += snprintf(pos, sizeof(global_pid_array_str) - (pos - global_pid_array_str), "%d%s", i + 1, i < TYPE_ARRAY_PID_MAX_LEN ? "," : "");
	}
}

static void init_global_array_strs(void)
{
	init_global_uid_array_str();
	init_global_pid_array_str();
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

	err = type_parse_bool(test_name, "network_io", "1", &arg.network_io);
	if (err) {
		TEST_FAIL(stats, test_name, "parse '1' (true) returned %d", err);
		return;
	}
	if (arg.network_io != true) {
		TEST_FAIL(stats, test_name, "Expected true, got %d", arg.network_io);
		return;
	}

	err = type_parse_bool(test_name, "include_ns_info", "0", &arg.include_ns_info);
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
	err = type_parse_bool(test_name, "network_io", "invalid", &arg.network_io);
	if (!err) {
		TEST_FAIL(stats, test_name, "Expected parse failure for 'invalid' but succeeded");
		return;
	}
	if (arg.network_io != original_value) {
		TEST_FAIL(stats, test_name, "Value modified on failed parse");
		return;
	}

	// Negative test: numeric string
	err = type_parse_bool(test_name, "network_io", "123", &arg.network_io);
	if (!err) {
		TEST_FAIL(stats, test_name, "Expected parse failure for '123' but succeeded");
		return;
	}

	// Negative test: empty string
	err = type_parse_bool(test_name, "network_io", "", &arg.network_io);
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
	enum type_monitor_mode original_value;
	_ensure_global_arg_is_reset();

	stats->total++;

	// Test TMM_CAPTURE (0)
	err = type_parse_monitor_mode(test_name, "uid_monitor_mode", "0", &arg.monitor_user.m_mode);
	if (err)
	{
		TEST_FAIL(stats, test_name, "parse '0' (TMM_CAPTURE) returned %d", err);
		return;
	}
	if (arg.monitor_user.m_mode != TMM_CAPTURE)
	{
		TEST_FAIL(stats, test_name, "Expected TMM_CAPTURE, got %d", arg.monitor_user.m_mode);
		return;
	}

	// Test TMM_IGNORE (1)
	err = type_parse_monitor_mode(test_name, "uid_monitor_mode", "1", &arg.monitor_user.m_mode);
	if (err)
	{
		TEST_FAIL(stats, test_name, "parse '1' (TMM_IGNORE) returned %d", err);
		return;
	}
	if (arg.monitor_user.m_mode != TMM_IGNORE)
	{
		TEST_FAIL(stats, test_name, "Expected TMM_IGNORE, got %d", arg.monitor_user.m_mode);
		return;
	}

	// Negative test: invalid monitor mode
	original_value = arg.monitor_user.m_mode;
	err = type_parse_monitor_mode(test_name, "uid_monitor_mode", "invalid", &arg.monitor_user.m_mode);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for 'invalid' but succeeded");
		return;
	}
	if (arg.monitor_user.m_mode != original_value)
	{
		TEST_FAIL(stats, test_name, "Value modified on failed parse");
		return;
	}

	// Negative test: empty string
	err = type_parse_monitor_mode(test_name, "uid_monitor_mode", "", &arg.monitor_user.m_mode);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for empty string but succeeded");
		return;
	}

	// Negative test: invalid numeric value
	err = type_parse_monitor_mode(test_name, "uid_monitor_mode", "123", &arg.monitor_user.m_mode);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for '123' but succeeded");
		return;
	}

	util_log_info(test_name, "Testing type_parse_monitor_mode");
	arg_print(&arg);
	TEST_PASS(stats, test_name);
}

static void test_arg_parse_monitor_syscalls(struct test_stats *stats)
{
	const char *test_name = "test_arg_parse_monitor_syscalls";
	int err;
	enum type_monitor_syscalls original_value;
	_ensure_global_arg_is_reset();

	stats->total++;

	// Test TMS_ALL (-1)
	err = type_parse_monitor_syscalls(test_name, "monitor_syscalls", "-1", &arg.monitor_syscalls);
	if (err)
	{
		TEST_FAIL(stats, test_name, "parse '-1' (TMS_ALL) returned %d", err);
		return;
	}
	if (arg.monitor_syscalls != TMS_ALL)
	{
		TEST_FAIL(stats, test_name, "Expected TMS_ALL, got %d", arg.monitor_syscalls);
		return;
	}

	// Test TMS_ONLY_FAILED (0)
	err = type_parse_monitor_syscalls(test_name, "monitor_syscalls", "0", &arg.monitor_syscalls);
	if (err)
	{
		TEST_FAIL(stats, test_name, "parse '0' (TMS_ONLY_FAILED) returned %d", err);
		return;
	}
	if (arg.monitor_syscalls != TMS_ONLY_FAILED)
	{
		TEST_FAIL(stats, test_name, "Expected TMS_ONLY_FAILED, got %d", arg.monitor_syscalls);
		return;
	}

	// Test TMS_ONLY_SUCCESSFUL (1)
	err = type_parse_monitor_syscalls(test_name, "monitor_syscalls", "1", &arg.monitor_syscalls);
	if (err)
	{
		TEST_FAIL(stats, test_name, "parse '1' (TMS_ONLY_SUCCESSFUL) returned %d", err);
		return;
	}
	if (arg.monitor_syscalls != TMS_ONLY_SUCCESSFUL)
	{
		TEST_FAIL(stats, test_name, "Expected TMS_ONLY_SUCCESSFUL, got %d", arg.monitor_syscalls);
		return;
	}

	// Negative test: invalid monitor syscalls
	original_value = arg.monitor_syscalls;
	err = type_parse_monitor_syscalls(test_name, "monitor_syscalls", "invalid", &arg.monitor_syscalls);
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
	err = type_parse_monitor_syscalls(test_name, "monitor_syscalls", "", &arg.monitor_syscalls);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for empty string but succeeded");
		return;
	}

	// Negative test: invalid numeric value
	err = type_parse_monitor_syscalls(test_name, "monitor_syscalls", "999", &arg.monitor_syscalls);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for '999' but succeeded");
		return;
	}

	util_log_info(test_name, "Testing type_parse_monitor_syscalls");
	arg_print(&arg);
	TEST_PASS(stats, test_name);
}

static void test_arg_parse_monitor_connections(struct test_stats *stats)
{
	const char *test_name = "test_arg_parse_monitor_connections";
	int err;
	enum type_monitor_connections original_value;
	_ensure_global_arg_is_reset();

	stats->total++;

	// Test TMC_ALL (-1)
	err = type_parse_monitor_connections(test_name, "monitor_ct", "-1", &arg.nf.monitor_ct);
	if (err)
	{
		TEST_FAIL(stats, test_name, "parse '-1' (TMC_ALL) returned %d", err);
		return;
	}
	if (arg.nf.monitor_ct != TMC_ALL)
	{
		TEST_FAIL(stats, test_name, "Expected TMC_ALL, got %d", arg.nf.monitor_ct);
		return;
	}

	// Test TMC_ONLY_NEW (0)
	err = type_parse_monitor_connections(test_name, "monitor_ct", "0", &arg.nf.monitor_ct);
	if (err)
	{
		TEST_FAIL(stats, test_name, "parse '0' (TMC_ONLY_NEW) returned %d", err);
		return;
	}
	if (arg.nf.monitor_ct != TMC_ONLY_NEW)
	{
		TEST_FAIL(stats, test_name, "Expected TMC_ONLY_NEW, got %d", arg.nf.monitor_ct);
		return;
	}

	// Negative test: invalid monitor connections
	original_value = arg.nf.monitor_ct;
	err = type_parse_monitor_connections(test_name, "monitor_ct", "invalid", &arg.nf.monitor_ct);
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
	err = type_parse_monitor_connections(test_name, "monitor_ct", "", &arg.nf.monitor_ct);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for empty string but succeeded");
		return;
	}

	// Negative test: invalid numeric value
	err = type_parse_monitor_connections(test_name, "monitor_ct", "42", &arg.nf.monitor_ct);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for '42' but succeeded");
		return;
	}

	util_log_info(test_name, "Testing type_parse_monitor_connections");
	arg_print(&arg);
	TEST_PASS(stats, test_name);
}

static void test_arg_parse_pid_array(struct test_stats *stats)
{
	const char *test_name = "test_arg_parse_pid_array";
	int err;
	_ensure_global_arg_is_reset();

	stats->total++;

	err = type_parse_pid_array(test_name, "pids", "100,200,300", &arg.monitor_pid.pids);
	if (err)
	{
		TEST_FAIL(stats, test_name, "parse PIDs returned %d", err);
		return;
	}
	if (arg.monitor_pid.pids.len != 3)
	{
		TEST_FAIL(stats, test_name, "Expected 3 PIDs, got %zu", arg.monitor_pid.pids.len);
		return;
	}
	if (arg.monitor_pid.pids.arr[0] != 100 || arg.monitor_pid.pids.arr[1] != 200 || arg.monitor_pid.pids.arr[2] != 300)
	{
		TEST_FAIL(stats, test_name, "PID values incorrect: [%d,%d,%d]", arg.monitor_pid.pids.arr[0], arg.monitor_pid.pids.arr[1], arg.monitor_pid.pids.arr[2]);
		return;
	}

	// Negative test: empty array
	err = type_parse_pid_array(test_name, "ppids", "", &arg.monitor_ppid.ppids);
	if (err)
	{
		TEST_FAIL(stats, test_name, "Parse failure for empty string");
		return;
	}
	if (arg.monitor_ppid.ppids.len != 0)
	{
		TEST_FAIL(stats, test_name, "Expected 0 PIDs, got %zu", arg.monitor_ppid.ppids.len);
		return;
	}

	// Negative test: invalid array (non-numeric)
	err = type_parse_pid_array(test_name, "ppids", "100,abc,300", &arg.monitor_ppid.ppids);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for invalid PID 'abc' but succeeded");
		return;
	}

	// Negative test: array exceeding TYPE_ARRAY_PID_MAX_LEN (64)
	err = type_parse_pid_array(test_name, "ppids", global_pid_array_str, &arg.monitor_ppid.ppids);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for array size > TYPE_ARRAY_PID_MAX_LEN but succeeded");
		return;
	}

	util_log_info(test_name, "Testing type_parse_pid_array");
	arg_print(&arg);
	TEST_PASS(stats, test_name);
}

static void test_arg_parse_uid_array(struct test_stats *stats)
{
	const char *test_name = "test_arg_parse_uid_array";
	int err;
	_ensure_global_arg_is_reset();

	stats->total++;

	err = type_parse_uid_array(test_name, "uids", "1000,1001,1002", &arg.monitor_user.uids);
	if (err)
	{
		TEST_FAIL(stats, test_name, "parse UIDs returned %d", err);
		return;
	}
	if (arg.monitor_user.uids.len != 3)
	{
		TEST_FAIL(stats, test_name, "Expected 3 UIDs, got %zu", arg.monitor_user.uids.len);
		return;
	}
	if (arg.monitor_user.uids.arr[0] != 1000 || arg.monitor_user.uids.arr[1] != 1001 || arg.monitor_user.uids.arr[2] != 1002)
	{
		TEST_FAIL(stats, test_name, "UID values incorrect: [%u,%u,%u]", arg.monitor_user.uids.arr[0], arg.monitor_user.uids.arr[1], arg.monitor_user.uids.arr[2]);
		return;
	}

	err = type_parse_monitor_mode(test_name, "uid_monitor_mode", "0", &arg.monitor_user.m_mode);
	if (err)
	{
		TEST_FAIL(stats, test_name, "parse monitor mode returned %d", err);
		return;
	}
	if (arg.monitor_user.m_mode != TMM_CAPTURE)
	{
		TEST_FAIL(stats, test_name, "Expected TMM_CAPTURE, got %d", arg.monitor_user.m_mode);
		return;
	}

	// Negative test: empty array
	arg.monitor_user.uids.len = 0;
	err = type_parse_uid_array(test_name, "uids", "", &arg.monitor_user.uids);
	if (err)
	{
		TEST_FAIL(stats, test_name, "Parse failure for empty string");
		return;
	}
	if (arg.monitor_user.uids.len != 0)
	{
		TEST_FAIL(stats, test_name, "Expected 0 UIDs, got %zu", arg.monitor_user.uids.len);
		return;
	}

	// Negative test: invalid array (non-numeric)
	err = type_parse_uid_array(test_name, "uids", "1000,invalid,1002", &arg.monitor_user.uids);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for invalid UID 'invalid' but succeeded");
		return;
	}

	// Negative test: negative number (UID is unsigned)
	err = type_parse_uid_array(test_name, "uids", "1000,-1,1002", &arg.monitor_user.uids);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for negative UID but succeeded");
		return;
	}

	// Negative test: array exceeding TYPE_ARRAY_UID_MAX_LEN (64)
	err = type_parse_uid_array(test_name, "uids", global_uid_array_str, &arg.monitor_user.uids);
	if (!err)
	{
		TEST_FAIL(stats, test_name, "Expected parse failure for array size > TYPE_ARRAY_UID_MAX_LEN but succeeded");
		return;
	}

	util_log_info(test_name, "Testing type_parse_uid_array");
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

	init_global_array_strs();

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
