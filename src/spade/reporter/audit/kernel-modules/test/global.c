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
#include <linux/netfilter.h>
#include <linux/netfilter_ipv4.h>
#include <linux/in.h>
#include <asm/syscall.h>

#include "spade/util/log/log.h"
#include "spade/audit/global/global.h"

#include "test/common.h"
#include "test/global.h"

//

#define FF_LIST_LEN 256
#define STR_ID_LEN 64

//

union filter_func_union;

enum filter_func_type
{
    FFT_NULL = 0,
    FFT_NF_LOGGABLE_BY_USER,
    FFT_NF_LOGGABLE_BY_CONNTRACK_INFO,
    FFT_NF_LOGGING_NS_INFO,
    FFT_NETWORK_LOGGING_NS_INFO,
    FFT_SYSCALL_LOGGABLE
};

struct filter_func_common
{
    enum filter_func_type type;
    char id[STR_ID_LEN];
    /*
        Check if test passes given the filter_func.

        Returns:
            true        -> Passed.
            false       -> Failed.
    */
    bool (*test)(union filter_func_union *ffu);
};

struct filter_func_is_netfilter_loggable_by_user
{
    uid_t arg_uid;
    bool result_expected;
};

struct filter_func_is_netfilter_loggable_by_conntrack_info
{
    enum ip_conntrack_info arg_ct_info;
    bool result_expected;
};

struct filter_func_is_netfilter_logging_ns_info
{
    bool result_expected;
};

struct filter_func_is_network_logging_ns_info
{
    bool result_expected;
};

struct filter_func_is_syscall_loggable
{
    int arg_sys_num;
    bool arg_sys_success;
    pid_t arg_pid;
    pid_t arg_ppid;
    uid_t arg_uid;
    bool result_expected;
};

union filter_func_union
{
    struct filter_func_is_netfilter_loggable_by_user is_netfilter_loggable_by_user;
    struct filter_func_is_netfilter_loggable_by_conntrack_info is_netfilter_loggable_by_conntrack_info;
    struct filter_func_is_netfilter_logging_ns_info is_netfilter_logging_ns_info;
    struct filter_func_is_network_logging_ns_info is_network_logging_ns_info;
    struct filter_func_is_syscall_loggable is_syscall_loggable;
};

struct filter_func
{
    struct filter_func_common header;
    union filter_func_union ff;
};

struct filter_func_list
{
    struct filter_func list[FF_LIST_LEN];
};

enum test_config_type
{
    TCT_NULL = 0,
    TCT_NF,
    TCT_NETWORK,
    TCT_SYSCALL
};

struct test_config
{
    enum test_config_type type;
    char id[STR_ID_LEN];
    struct arg arg;
    struct filter_func_list ff_list;
};

//

#define _CREATE_NF_ARG(a_u_mode, a_u_len, a_u, a_use_u, a_h, a_m_ct, a_incl_ns) \
    { \
        .ignore_pids = {}, \
        .ignore_ppids = {}, \
        .monitor_syscalls = AMMS_ONLY_SUCCESSFUL, \
        .network_io = false, \
        .include_ns_info = a_incl_ns, \
        .nf = { \
            .hooks = a_h, \
            .monitor_ct = a_m_ct, \
            .use_user = a_use_u \
        }, \
        .user = { \
            .uid_monitor_mode = a_u_mode, \
            .uids = { \
                .len = a_u_len, \
                .arr = a_u \
            } \
        } \
    }

#define _CREATE_SYSCALL_ARG(a_u_mode, a_u_len, a_u, a_use_u) \
    { \
        \
    }

#define _CREATE_NF_FF_LOGGABLE_BY_USER(a_id, a_uid, a_result) \
    { \
        .header = { \
            .type = FFT_NF_LOGGABLE_BY_USER, \
            .id = a_id, \
            .test = handle_filter_func_is_netfilter_loggable_by_user, \
        }, \
        .ff = { \
            .is_netfilter_loggable_by_user = { \
                .arg_uid = (uid_t)a_uid, \
                .result_expected = a_result \
            } \
        } \
    }

#define _CREATE_NF_FF_LOGGING_NS_INFO(a_id, a_result) \
    { \
        .header = { \
            .type = FFT_NF_LOGGING_NS_INFO, \
            .id = a_id, \
            .test = handle_filter_func_is_netfilter_logging_ns_info, \
        }, \
        .ff = { \
            .is_netfilter_logging_ns_info = { \
                .result_expected = a_result \
            } \
        } \
    }

#define _CREATE_NF_FF_LOGGABLE_BY_CONNTRACK_INFO(a_id, a_ip_ct, a_result) \
    { \
        .header = { \
            .type = FFT_NF_LOGGABLE_BY_CONNTRACK_INFO, \
            .id = a_id, \
            .test = handle_filter_func_is_netfilter_loggable_by_conntrack_info, \
        }, \
        .ff = { \
            .is_netfilter_loggable_by_conntrack_info = { \
                .arg_ct_info = a_ip_ct, \
                .result_expected = a_result \
            } \
        } \
    }

#define _CREATE_NETWORK_FF_LOGGING_NS_INFO(a_id, a_result) \
    { \
        .header = { \
            .type = FFT_NETWORK_LOGGING_NS_INFO, \
            .id = a_id, \
            .test = handle_filter_func_is_network_logging_ns_info, \
        }, \
        .ff = { \
            .is_network_logging_ns_info = { \
                .result_expected = a_result \
            } \
        } \
    }

#define _CREATE_SYSCALL_FF_LOGGABLE(a_id, a_pid, a_ppid, a_sys_num, a_sys_success, a_uid, a_result) \
    { \
        .header = { \
            .type = FFT_SYSCALL_LOGGABLE, \
            .id = a_id, \
            .test = handle_filter_func_is_syscall_loggable, \
        }, \
        .ff = { \
            .is_syscall_loggable = { \
                .arg_pid = a_pid, \
                .arg_ppid = a_ppid, \
                .arg_sys_num = a_sys_num, \
                .arg_sys_success = a_sys_success, \
                .arg_uid = a_uid, \
                .result_expected = a_result \
            } \
        } \
    }

//

#define _CREATE_NF_LOGGABLE_BY_USER_MUST_CAPTURE(a_u_mode, a_uid) \
    _CREATE_NF_FF_LOGGABLE_BY_USER("nf_loggable_by_user_must_capture", a_uid, ((a_u_mode) == AMM_CAPTURE))

//

#define _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_USER(a_u_mode, a_uid) \
    _CREATE_NF_LOGGABLE_BY_USER_MUST_CAPTURE(a_uid, a_uid), \
    _CREATE_NF_FF_LOGGABLE_BY_USER("nf_loggable_by_user_must_ignore", a_uid+1, ((a_u_mode) == AMM_IGNORE)) \

#define _CREATE_NF_FF_LIST_ITEMS_LOGGING_NS_INFO(a_incl_ns) \
    _CREATE_NF_FF_LOGGING_NS_INFO("nf_logging_ns_info", (a_incl_ns == true))

#define _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_CONNTRACK_INFO(a_m_ct) \
    _CREATE_NF_FF_LOGGABLE_BY_CONNTRACK_INFO("nf_loggable_by_conntrack_estblshd_match_all", IP_CT_ESTABLISHED, (a_m_ct == AMMC_ALL)), \
    _CREATE_NF_FF_LOGGABLE_BY_CONNTRACK_INFO("nf_loggable_by_conntrack_rltd_match_all", IP_CT_RELATED, (a_m_ct == AMMC_ALL)), \
    _CREATE_NF_FF_LOGGABLE_BY_CONNTRACK_INFO("nf_loggable_by_conntrack_new_match_all_or_new", IP_CT_NEW, (a_m_ct == AMMC_ALL || a_m_ct == AMMC_ONLY_NEW)), \
    _CREATE_NF_FF_LOGGABLE_BY_CONNTRACK_INFO("nf_loggable_by_conntrack_rply_match_all", IP_CT_IS_REPLY, (a_m_ct == AMMC_ALL)), \
    _CREATE_NF_FF_LOGGABLE_BY_CONNTRACK_INFO("nf_loggable_by_conntrack_estblshdrply_match_all", IP_CT_ESTABLISHED_REPLY, (a_m_ct == AMMC_ALL)), \
    _CREATE_NF_FF_LOGGABLE_BY_CONNTRACK_INFO("nf_loggable_by_conntrack_rltdrply_match_all", IP_CT_RELATED_REPLY, (a_m_ct == AMMC_ALL)), \
    _CREATE_NF_FF_LOGGABLE_BY_CONNTRACK_INFO("nf_loggable_by_conntrack_num_match_all", IP_CT_NUMBER, (a_m_ct == AMMC_ALL)), \
    _CREATE_NF_FF_LOGGABLE_BY_CONNTRACK_INFO("nf_loggable_by_conntrack_untrckd_match_all", IP_CT_UNTRACKED, (a_m_ct == AMMC_ALL))

#define _CREATE_NETWORK_FF_LIST_ITEMS_LOGGING_NS_INFO(a_incl_ns) \
    _CREATE_NETWORK_FF_LOGGING_NS_INFO("network_logging_ns_info", (a_incl_ns == true))

// TODO
#define _CREATE_SYSCALL_FF_LIST_ITEMS_LOGGABLE(a_pid, a_ppid, a_sys_num, a_sys_success, a_u_mode, a_uid) \
    _CREATE_SYSCALL_FF_LOGGABLE("syscall_is_loggable_must_capture", a_pid, a_ppid, a_sys_num, a_sys_success, a_uid, ((a_u_mode) == AMM_CAPTURE)), \
    _CREATE_SYSCALL_FF_LOGGABLE("syscall_is_loggable_must_ignore", a_pid, a_ppid, a_sys_num, a_sys_success, a_uid+1, ((a_u_mode) == AMM_IGNORE))

//

static bool handle_filter_func_is_netfilter_loggable_by_user(
    union filter_func_union *ffu
)
{
    struct filter_func_is_netfilter_loggable_by_user* f = &ffu->is_netfilter_loggable_by_user;
    return f->result_expected == global_is_netfilter_loggable_by_user(f->arg_uid);
}

static bool handle_filter_func_is_netfilter_loggable_by_conntrack_info(
    union filter_func_union *ffu
)
{
    struct filter_func_is_netfilter_loggable_by_conntrack_info* f = &ffu->is_netfilter_loggable_by_conntrack_info;
    return f->result_expected == global_is_netfilter_loggable_by_conntrack_info(f->arg_ct_info);
}

static bool handle_filter_func_is_netfilter_logging_ns_info(
    union filter_func_union *ffu
)
{
    struct filter_func_is_netfilter_logging_ns_info* f = &ffu->is_netfilter_logging_ns_info;
    return f->result_expected == global_is_netfilter_logging_ns_info();
}

static bool handle_filter_func_is_network_logging_ns_info(
    union filter_func_union *ffu
)
{
    struct filter_func_is_network_logging_ns_info* f = &ffu->is_network_logging_ns_info;
    return f->result_expected == global_is_network_logging_ns_info();
}

static bool handle_filter_func_is_syscall_loggable(
    union filter_func_union *ffu
)
{
    struct filter_func_is_syscall_loggable* f = &ffu->is_syscall_loggable;
    return f->result_expected == global_is_syscall_loggable(
        f->arg_sys_num, f->arg_sys_success, f->arg_pid, f->arg_ppid, f->arg_uid
    );
}

//

static const struct test_config TC_LIST[] = {
    {
        .type = TCT_NF,
        .id = "nf_capture_all_ct_for_u_1001_with_ns",
        .arg = _CREATE_NF_ARG(
            AMM_CAPTURE, 1, {1001}, true, true, AMMC_ALL, true
        ),
        .ff_list = {
            .list = {
                _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_USER(AMM_CAPTURE, 1001),
                _CREATE_NF_FF_LIST_ITEMS_LOGGING_NS_INFO(true),
                _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_CONNTRACK_INFO(AMMC_ALL),
                _CREATE_NETWORK_FF_LIST_ITEMS_LOGGING_NS_INFO(true),
                _CREATE_SYSCALL_FF_LIST_ITEMS_LOGGABLE(0, 0, __NR_accept, true, AMM_CAPTURE, 1001),
                {}
            }
        }
    },
    {
        .type = TCT_NF,
        .id = "nf_capture_only_new_ct_for_u_1001_without_ns",
        .arg = _CREATE_NF_ARG(
            AMM_CAPTURE, 1, {1001}, true, true, AMMC_ONLY_NEW, false
        ),
        .ff_list = {
            .list = {
                _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_USER(AMM_CAPTURE, 1001),
                _CREATE_NF_FF_LIST_ITEMS_LOGGING_NS_INFO(false),
                _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_CONNTRACK_INFO(AMMC_ONLY_NEW),
                _CREATE_NETWORK_FF_LIST_ITEMS_LOGGING_NS_INFO(false),
                _CREATE_SYSCALL_FF_LIST_ITEMS_LOGGABLE(0, 0, __NR_accept, true, AMM_CAPTURE, 1001),
                {}
            }
        }
    },
    {
        .type = TCT_NF,
        .id = "nf_ignore_all_ct_for_u_1001_with_ns",
        .arg = _CREATE_NF_ARG(
            AMM_IGNORE, 1, {1001}, true, true, AMMC_ALL, true
        ),
        .ff_list = {
            .list = {
                _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_USER(AMM_IGNORE, 1001),
                _CREATE_NF_FF_LIST_ITEMS_LOGGING_NS_INFO(true),
                _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_CONNTRACK_INFO(AMMC_ALL),
                _CREATE_NETWORK_FF_LIST_ITEMS_LOGGING_NS_INFO(true),
                _CREATE_SYSCALL_FF_LIST_ITEMS_LOGGABLE(0, 0, __NR_accept, true, AMM_IGNORE, 1001),
                {}
            }
        }
    },
    {
        .type = TCT_NF,
        .id = "nf_ignore_only_new_ct_for_u_1001_with_ns",
        .arg = _CREATE_NF_ARG(
            AMM_IGNORE, 1, {1001}, true, true, AMMC_ONLY_NEW, true
        ),
        .ff_list = {
            .list = {
                _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_USER(AMM_IGNORE, 1001),
                _CREATE_NF_FF_LIST_ITEMS_LOGGING_NS_INFO(true),
                _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_CONNTRACK_INFO(AMMC_ONLY_NEW),
                _CREATE_NETWORK_FF_LIST_ITEMS_LOGGING_NS_INFO(true),
                _CREATE_SYSCALL_FF_LIST_ITEMS_LOGGABLE(0, 0, __NR_accept, true, AMM_IGNORE, 1001),
                {}
            }
        }
    },
    {
        .type = TCT_NF,
        .id = "nf_capture_all_user_ct",
        .arg = _CREATE_NF_ARG(
            AMM_IGNORE, 1, {1001}, false, true, AMMC_ALL, true
        ),
        .ff_list = {
            .list = {
                _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_USER(AMM_CAPTURE, 1001),
                _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_USER(AMM_CAPTURE, 2001),
                _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_CONNTRACK_INFO(AMMC_ALL),
                {}
            }
        }
    },
    {
        .type = TCT_NETWORK,
        .id = "network_logging_with_ns",
        .arg = _CREATE_NF_ARG(
            AMM_CAPTURE, 1, {1001}, true, true, AMMC_ALL, true
        ),
        .ff_list = {
            .list = {
                _CREATE_NETWORK_FF_LIST_ITEMS_LOGGING_NS_INFO(true),
                {}
            }
        }
    },
    {.type = TCT_NULL}
};
#define TC_LIST_LEN (sizeof(TC_LIST) / sizeof(TC_LIST[0]))

//

static void _ensure_global_reset(void)
{
    if (global_is_auditing_started())
        global_auditing_stop();

    if (global_is_context_initialized())
        global_context_deinit();

    if (global_is_state_initialized())
        global_state_deinit();
}

static void test_global_test_init_deinit(struct test_stats *stats)
{
    const char *test_name = "test_global_test_init_deinit";
    int err;
    bool dry_run = true;
    struct arg arg = {
        .ignore_pids = {},
        .ignore_ppids = {},
        .include_ns_info = true,
        .monitor_syscalls = AMMS_ALL,
        .network_io = true,
        .nf = {0},
        .user = {0}
    };

    _ensure_global_reset();

    stats->total++;

    if (global_is_state_initialized())
    {
        TEST_FAIL(stats, test_name, "global state marked as initialized without initialization");
        return;
    }

    err = global_state_init(dry_run);
    if (err != 0 || !global_is_state_initialized())
    {
        TEST_FAIL(stats, test_name, "global state failed to init. Err: %d", err);
        return;
    }

    err = global_context_init(&arg);
    if (err != 0 || !global_is_context_initialized())
    {
        TEST_FAIL(stats, test_name, "global context failed to init. Err: %d", err);
        _ensure_global_reset();
        return;
    }

    err = global_auditing_start();
    if (err != 0 || !global_is_auditing_started())
    {
        TEST_FAIL(stats, test_name, "global auditing failed to start. Err: %d", err);
        _ensure_global_reset();
        return;
    }

    err = global_auditing_stop();
    if (err != 0 || global_is_auditing_started())
    {
        TEST_FAIL(stats, test_name, "global auditing failed to stop. Err: %d", err);
        _ensure_global_reset();
        return;
    }

    err = global_context_deinit();
    if (err != 0 || global_is_context_initialized())
    {
        TEST_FAIL(stats, test_name, "global context failed to deinit. Err: %d", err);
        _ensure_global_reset();
        return;
    }

    err = global_state_deinit();
    if (err != 0 || global_is_state_initialized())
    {
        TEST_FAIL(stats, test_name, "global state failed to deinit. Err: %d", err);
        return;
    }

    TEST_PASS(stats, test_name);
}

static void test_global_test_netfilter_filter_func(struct test_stats *stats, const struct test_config *tc, struct filter_func *ff)
{
    const char *test_name = "test_global_test_netfilter_filter_func";
    bool passed;

    stats->total++;

    passed = ff->header.test(&ff->ff);

    if (!passed)
    {
        TEST_FAIL(
            stats, test_name,
            "filter func failed test at index (test_config=%s, filter_func=%s)",
            &tc->id[0], &ff->header.id[0]
        );
        return;
    }

    TEST_PASS(stats, test_name);
}

static void test_global_test_netfilter_filter_func_list(struct test_stats *stats, const struct test_config *tc, struct filter_func_list *ffl)
{
    int i;

    for (i = 0; i < FF_LIST_LEN; i++)
    {
        struct filter_func *ff = &ffl->list[i];
        if (!ff || !ff->header.test)
            continue;
        if (ff->header.type == FFT_NULL)
            break;
     
        test_global_test_netfilter_filter_func(stats, tc, ff);
    }
}

static void test_global_test_netfilter_test_configs(struct test_stats *stats)
{
    const char *test_name = "test_global_test_netfilter_test_configs";
    int err, i;
    bool dry_run = true;

    _ensure_global_reset();

    err = global_state_init(dry_run);
    if (err != 0 || !global_is_state_initialized())
    {
        TEST_FAIL(stats, test_name, "global state failed to init. Err: %d", err);
        return;
    }

    for (i = 0; i < TC_LIST_LEN; i++)
    {
        const struct test_config *tc = &TC_LIST[i];
        struct arg *arg;
        struct filter_func_list *ff_list;
        if (!tc)
            continue;
        if (tc->type == TCT_NULL)
            break;

        stats->total++;

        arg = (struct arg *)&tc->arg;
        ff_list = (struct filter_func_list *)&tc->ff_list;

        err = global_context_init(arg);
        if (err != 0 || !global_is_context_initialized())
        {
            TEST_FAIL(stats, test_name, "global context failed to init with test config at index: %d. Err: %d", i, err);
            break;
        }

        err = global_auditing_start();
        if (err != 0 || !global_is_auditing_started())
        {
            TEST_FAIL(stats, test_name, "global auditing failed to start with test config at index: %d. Err: %d", i, err);
            break;
        }

        test_global_test_netfilter_filter_func_list(stats, tc, ff_list);

        err = global_auditing_stop();
        if (err != 0 || global_is_auditing_started())
        {
            TEST_FAIL(stats, test_name, "global auditing failed to stop with test config at index: %d. Err: %d", i, err);
            break;
        }

        err = global_context_deinit();
        if (err != 0 || global_is_context_initialized())
        {
            TEST_FAIL(stats, test_name, "global context failed to deinit with test config at index: %d. Err: %d", i, err);
            break;
        }

        TEST_PASS(stats, test_name);
    }
}

int test_global_all(struct test_stats *stats)
{
    test_stats_init(stats);
    util_log_info("test_global", "Starting tests");

    test_global_test_init_deinit(stats);
    test_global_test_netfilter_test_configs(stats);

    return 0;
}