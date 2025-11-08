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

#include "spade/audit/util/log/log.h"
#include "spade/audit/global/global.h"
#include "spade/audit/global/filter.h"
#include "spade/audit/kernel/function/number.h"

#include "test/kernel/spade/audit/common.h"
#include "test/kernel/spade/audit/global.h"

//

#define FF_LIST_LEN 256
#define STR_ID_LEN 64

static struct arg global_arg = {};

static void _ensure_global_arg_is_reset(void)
{
	memset(&global_arg, 0, sizeof(global_arg));
}

//

union filter_func_union;

enum filter_func_type
{
    FFT_NULL = 0,
    FFT_NF_ACTIONABLE_BY_USER,
    FFT_NF_ACTIONABLE_BY_CONNTRACK_INFO,
    FFT_NF_INCLUDE_NS_INFO,
    FFT_NF_AUDIT_HOOKS_ON,
    FFT_NETWORK_INCLUDE_NS_INFO,
    FFT_FUNCTION_POST_EXECUTION_ACTIONABLE,
    FFT_FUNCTION_ACTIONABLE_BY_FUNCTION_NUMBER,
    FFT_FUNCTION_ACTIONABLE_BY_FUNCTION_SUCCESS,
    FFT_FUNCTION_ACTIONABLE_BY_PID,
    FFT_FUNCTION_ACTIONABLE_BY_PPID,
    FFT_FUNCTION_ACTIONABLE_BY_UID
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

struct filter_func_is_netfilter_actionable_by_user
{
    uid_t arg_uid;
    bool result_expected;
};

struct filter_func_is_netfilter_actionable_by_conntrack_info
{
    enum ip_conntrack_info arg_ct_info;
    bool result_expected;
};

struct filter_func_is_netfilter_include_ns_info
{
    bool result_expected;
};

struct filter_func_is_netfilter_audit_hooks_on
{
    bool result_expected;
};

struct filter_func_is_network_include_ns_info
{
    bool result_expected;
};

struct filter_func_is_function_post_execution_actionable
{
    int arg_func_num;
    bool arg_func_success;
    pid_t arg_pid;
    pid_t arg_ppid;
    uid_t arg_uid;
    bool result_expected;
};

struct filter_func_is_function_actionable_by_function_number
{
    int arg_func_num;
    bool result_expected;
};

struct filter_func_is_function_actionable_by_function_success
{
    bool arg_func_success;
    bool result_expected;
};

struct filter_func_is_function_actionable_by_pid
{
    pid_t arg_pid;
    bool result_expected;
};

struct filter_func_is_function_actionable_by_ppid
{
    pid_t arg_ppid;
    bool result_expected;
};

struct filter_func_is_function_actionable_by_uid
{
    uid_t arg_uid;
    bool result_expected;
};

union filter_func_union
{
    struct filter_func_is_netfilter_actionable_by_user is_netfilter_actionable_by_user;
    struct filter_func_is_netfilter_actionable_by_conntrack_info is_netfilter_actionable_by_conntrack_info;
    struct filter_func_is_netfilter_include_ns_info is_netfilter_include_ns_info;
    struct filter_func_is_netfilter_audit_hooks_on is_netfilter_audit_hooks_on;
    struct filter_func_is_network_include_ns_info is_network_include_ns_info;
    struct filter_func_is_function_post_execution_actionable is_function_post_execution_actionable;
    struct filter_func_is_function_actionable_by_function_number is_function_actionable_by_function_number;
    struct filter_func_is_function_actionable_by_function_success is_function_actionable_by_function_success;
    struct filter_func_is_function_actionable_by_pid is_function_actionable_by_pid;
    struct filter_func_is_function_actionable_by_ppid is_function_actionable_by_ppid;
    struct filter_func_is_function_actionable_by_uid is_function_actionable_by_uid;
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
    TCT_FUNCTION
};

struct test_config
{
    enum test_config_type type;
    char id[STR_ID_LEN];
    struct arg arg;
    struct filter_func_list ff_list;
};

//

#define _CREATE_NF_FF_ACTIONABLE_BY_USER(a_id, a_uid, a_result) \
    { \
        .header = { \
            .type = FFT_NF_ACTIONABLE_BY_USER, \
            .id = a_id, \
            .test = handle_filter_func_is_netfilter_actionable_by_user, \
        }, \
        .ff = { \
            .is_netfilter_actionable_by_user = { \
                .arg_uid = (uid_t)a_uid, \
                .result_expected = a_result \
            } \
        } \
    }

#define _CREATE_NF_FF_INCLUDE_NS_INFO(a_id, a_result) \
    { \
        .header = { \
            .type = FFT_NF_INCLUDE_NS_INFO, \
            .id = a_id, \
            .test = handle_filter_func_is_netfilter_include_ns_info, \
        }, \
        .ff = { \
            .is_netfilter_include_ns_info = { \
                .result_expected = a_result \
            } \
        } \
    }

#define _CREATE_NF_FF_AUDIT_HOOKS_ON(a_id, a_result) \
    { \
        .header = { \
            .type = FFT_NF_AUDIT_HOOKS_ON, \
            .id = a_id, \
            .test = handle_filter_func_is_netfilter_audit_hooks_on, \
        }, \
        .ff = { \
            .is_netfilter_audit_hooks_on = { \
                .result_expected = a_result \
            } \
        } \
    }

#define _CREATE_NF_FF_ACTIONABLE_BY_CONNTRACK_INFO(a_id, a_ip_ct, a_result) \
    { \
        .header = { \
            .type = FFT_NF_ACTIONABLE_BY_CONNTRACK_INFO, \
            .id = a_id, \
            .test = handle_filter_func_is_netfilter_actionable_by_conntrack_info, \
        }, \
        .ff = { \
            .is_netfilter_actionable_by_conntrack_info = { \
                .arg_ct_info = a_ip_ct, \
                .result_expected = a_result \
            } \
        } \
    }

#define _CREATE_NETWORK_FF_INCLUDE_NS_INFO(a_id, a_result) \
    { \
        .header = { \
            .type = FFT_NETWORK_INCLUDE_NS_INFO, \
            .id = a_id, \
            .test = handle_filter_func_is_network_include_ns_info, \
        }, \
        .ff = { \
            .is_network_include_ns_info = { \
                .result_expected = a_result \
            } \
        } \
    }

#define _CREATE_FUNCTION_FF_POST_EXECUTION_ACTIONABLE(a_id, a_pid, a_ppid, a_func_num, a_func_success, a_uid, a_result) \
    { \
        .header = { \
            .type = FFT_FUNCTION_POST_EXECUTION_ACTIONABLE, \
            .id = a_id, \
            .test = handle_filter_func_is_function_post_execution_actionable, \
        }, \
        .ff = { \
            .is_function_post_execution_actionable = { \
                .arg_pid = a_pid, \
                .arg_ppid = a_ppid, \
                .arg_func_num = a_func_num, \
                .arg_func_success = a_func_success, \
                .arg_uid = a_uid, \
                .result_expected = a_result \
            } \
        } \
    }

#define _CREATE_FUNCTION_FF_ACTIONABLE_BY_FUNC_NUM(a_id, a_func_num, a_result) \
    { \
        .header = { \
            .type = FFT_FUNCTION_ACTIONABLE_BY_FUNCTION_NUMBER, \
            .id = a_id, \
            .test = handle_filter_func_is_function_actionable_by_function_number, \
        }, \
        .ff = { \
            .is_function_actionable_by_function_number = { \
                .arg_func_num = a_func_num, \
                .result_expected = a_result \
            } \
        } \
    }

#define _CREATE_FUNCTION_FF_ACTIONABLE_BY_FUNC_SUCCESS(a_id, a_func_success, a_result) \
    { \
        .header = { \
            .type = FFT_FUNCTION_ACTIONABLE_BY_FUNCTION_SUCCESS, \
            .id = a_id, \
            .test = handle_filter_func_is_function_actionable_by_function_success, \
        }, \
        .ff = { \
            .is_function_actionable_by_function_success = { \
                .arg_func_success = a_func_success, \
                .result_expected = a_result \
            } \
        } \
    }

#define _CREATE_FUNCTION_FF_ACTIONABLE_BY_PID(a_id, a_pid, a_result) \
    { \
        .header = { \
            .type = FFT_FUNCTION_ACTIONABLE_BY_PID, \
            .id = a_id, \
            .test = handle_filter_func_is_function_actionable_by_pid, \
        }, \
        .ff = { \
            .is_function_actionable_by_pid = { \
                .arg_pid = a_pid, \
                .result_expected = a_result \
            } \
        } \
    }

#define _CREATE_FUNCTION_FF_ACTIONABLE_BY_PPID(a_id, a_ppid, a_result) \
    { \
        .header = { \
            .type = FFT_FUNCTION_ACTIONABLE_BY_PPID, \
            .id = a_id, \
            .test = handle_filter_func_is_function_actionable_by_ppid, \
        }, \
        .ff = { \
            .is_function_actionable_by_ppid = { \
                .arg_ppid = a_ppid, \
                .result_expected = a_result \
            } \
        } \
    }

#define _CREATE_FUNCTION_FF_ACTIONABLE_BY_UID(a_id, a_uid, a_result) \
    { \
        .header = { \
            .type = FFT_FUNCTION_ACTIONABLE_BY_UID, \
            .id = a_id, \
            .test = handle_filter_func_is_function_actionable_by_uid, \
        }, \
        .ff = { \
            .is_function_actionable_by_uid = { \
                .arg_uid = a_uid, \
                .result_expected = a_result \
            } \
        } \
    }

//

// Wrapper macros for netfilter actionable by user tests
#define _CREATE_NF_ACTIONABLE_BY_USER_MUST_CAPTURE(a_u_mode, a_uid) \
    _CREATE_NF_FF_ACTIONABLE_BY_USER("nf_actionable_by_user_must_capture", a_uid, ((a_u_mode) == TMM_CAPTURE))

#define _CREATE_NF_ACTIONABLE_BY_USER_MUST_IGNORE(a_u_mode, a_uid) \
    _CREATE_NF_FF_ACTIONABLE_BY_USER("nf_actionable_by_user_must_ignore", (a_uid)+1, ((a_u_mode) == TMM_IGNORE))

// Wrapper macro for netfilter including namespace info
#define _CREATE_NF_INCLUDE_NS_INFO(a_incl_ns) \
    _CREATE_NF_FF_INCLUDE_NS_INFO("nf_include_ns_info", ((a_incl_ns) == true))

// Wrapper macro for netfilter audit hooks on
#define _CREATE_NF_AUDIT_HOOKS_ON(a_audit_hooks) \
    _CREATE_NF_FF_AUDIT_HOOKS_ON("nf_audit_hooks_on", ((a_audit_hooks) == true))

// Wrapper macros for netfilter actionable by conntrack info tests
#define _CREATE_NF_ACTIONABLE_BY_CONNTRACK_ESTABLISHED(a_m_ct) \
    _CREATE_NF_FF_ACTIONABLE_BY_CONNTRACK_INFO("nf_actionable_by_conntrack_estblshd_match_all", IP_CT_ESTABLISHED, ((a_m_ct) == TMC_ALL))

#define _CREATE_NF_ACTIONABLE_BY_CONNTRACK_RELATED(a_m_ct) \
    _CREATE_NF_FF_ACTIONABLE_BY_CONNTRACK_INFO("nf_actionable_by_conntrack_rltd_match_all", IP_CT_RELATED, ((a_m_ct) == TMC_ALL))

#define _CREATE_NF_ACTIONABLE_BY_CONNTRACK_NEW(a_m_ct) \
    _CREATE_NF_FF_ACTIONABLE_BY_CONNTRACK_INFO("nf_actionable_by_conntrack_new_match_all_or_new", IP_CT_NEW, ((a_m_ct) == TMC_ALL || (a_m_ct) == TMC_ONLY_NEW))

#define _CREATE_NF_ACTIONABLE_BY_CONNTRACK_REPLY(a_m_ct) \
    _CREATE_NF_FF_ACTIONABLE_BY_CONNTRACK_INFO("nf_actionable_by_conntrack_rply_match_all", IP_CT_IS_REPLY, ((a_m_ct) == TMC_ALL))

#define _CREATE_NF_ACTIONABLE_BY_CONNTRACK_ESTABLISHED_REPLY(a_m_ct) \
    _CREATE_NF_FF_ACTIONABLE_BY_CONNTRACK_INFO("nf_actionable_by_conntrack_estblshdrply_match_all", IP_CT_ESTABLISHED_REPLY, ((a_m_ct) == TMC_ALL))

#define _CREATE_NF_ACTIONABLE_BY_CONNTRACK_RELATED_REPLY(a_m_ct) \
    _CREATE_NF_FF_ACTIONABLE_BY_CONNTRACK_INFO("nf_actionable_by_conntrack_rltdrply_match_all", IP_CT_RELATED_REPLY, ((a_m_ct) == TMC_ALL))

#define _CREATE_NF_ACTIONABLE_BY_CONNTRACK_NUMBER(a_m_ct) \
    _CREATE_NF_FF_ACTIONABLE_BY_CONNTRACK_INFO("nf_actionable_by_conntrack_num_match_all", IP_CT_NUMBER, ((a_m_ct) == TMC_ALL))

#define _CREATE_NF_ACTIONABLE_BY_CONNTRACK_UNTRACKED(a_m_ct) \
    _CREATE_NF_FF_ACTIONABLE_BY_CONNTRACK_INFO("nf_actionable_by_conntrack_untrckd_match_all", IP_CT_UNTRACKED, ((a_m_ct) == TMC_ALL))

// Wrapper macro for network including namespace info
#define _CREATE_NETWORK_INCLUDE_NS_INFO(a_incl_ns) \
    _CREATE_NETWORK_FF_INCLUDE_NS_INFO("network_include_ns_info", ((a_incl_ns) == true))

// Wrapper macros for function post-exec actionable tests - Full parameters
#define _CREATE_FUNCTION_POST_EXECUTION_ACTIONABLE_MUST_CAPTURE(a_pid, a_ppid, a_func_num, a_func_success, a_uid) \
    _CREATE_FUNCTION_FF_POST_EXECUTION_ACTIONABLE("function_is_post_execution_actionable_must_capture", a_pid, a_ppid, a_func_num, a_func_success, a_uid, true)

#define _CREATE_FUNCTION_POST_EXECUTION_ACTIONABLE_MUST_IGNORE(a_pid, a_ppid, a_func_num, a_func_success, a_uid) \
    _CREATE_FUNCTION_FF_POST_EXECUTION_ACTIONABLE("function_is_post_execution_actionable_must_ignore", a_pid, a_ppid, a_func_num, a_func_success, (a_uid)+1, false)

// Wrapper macros for function actionable tests - By function number
#define _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_CAPTURE(a_func_num) \
    _CREATE_FUNCTION_FF_ACTIONABLE_BY_FUNC_NUM("function_actionable_by_func_num_must_capture", a_func_num, true)

#define _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_IGNORE(a_func_num) \
    _CREATE_FUNCTION_FF_ACTIONABLE_BY_FUNC_NUM("function_actionable_by_func_num_must_ignore", a_func_num, false)

// Wrapper macros for function actionable tests - By success/failure
#define _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_SUCCESS_MUST_CAPTURE(a_func_success) \
    _CREATE_FUNCTION_FF_ACTIONABLE_BY_FUNC_SUCCESS("function_actionable_by_func_success_must_capture", a_func_success, true)

#define _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_SUCCESS_MUST_IGNORE(a_func_success) \
    _CREATE_FUNCTION_FF_ACTIONABLE_BY_FUNC_SUCCESS("function_actionable_by_func_success_must_ignore", a_func_success, false)

// Wrapper macros for function actionable tests - By PID
#define _CREATE_FUNCTION_ACTIONABLE_BY_PID_MUST_CAPTURE(a_pid) \
    _CREATE_FUNCTION_FF_ACTIONABLE_BY_PID("function_actionable_by_pid_must_capture", a_pid, true)

#define _CREATE_FUNCTION_ACTIONABLE_BY_PID_MUST_IGNORE(a_pid) \
    _CREATE_FUNCTION_FF_ACTIONABLE_BY_PID("function_actionable_by_pid_must_ignore", a_pid, false)

// Wrapper macros for function actionable tests - By PPID
#define _CREATE_FUNCTION_ACTIONABLE_BY_PPID_MUST_CAPTURE(a_ppid) \
    _CREATE_FUNCTION_FF_ACTIONABLE_BY_PPID("function_actionable_by_ppid_must_capture", a_ppid, true)

#define _CREATE_FUNCTION_ACTIONABLE_BY_PPID_MUST_IGNORE(a_ppid) \
    _CREATE_FUNCTION_FF_ACTIONABLE_BY_PPID("function_actionable_by_ppid_must_ignore", a_ppid, false)

// Wrapper macros for function actionable tests - By UID
#define _CREATE_FUNCTION_ACTIONABLE_BY_UID_MUST_CAPTURE(a_uid) \
    _CREATE_FUNCTION_FF_ACTIONABLE_BY_UID("function_actionable_by_uid_must_capture", a_uid, true)

#define _CREATE_FUNCTION_ACTIONABLE_BY_UID_MUST_IGNORE(a_uid) \
    _CREATE_FUNCTION_FF_ACTIONABLE_BY_UID("function_actionable_by_uid_must_ignore", a_uid, false)

//

#define _CREATE_NF_FF_LIST_ITEMS_ACTIONABLE_BY_USER(a_u_mode, a_uid) \
    _CREATE_NF_ACTIONABLE_BY_USER_MUST_CAPTURE(a_u_mode, a_uid), \
    _CREATE_NF_ACTIONABLE_BY_USER_MUST_IGNORE(a_u_mode, a_uid)

#define _CREATE_NF_FF_LIST_ITEMS_INCLUDE_NS_INFO(a_incl_ns) \
    _CREATE_NF_INCLUDE_NS_INFO(a_incl_ns)

#define _CREATE_NF_FF_LIST_ITEMS_ACTIONABLE_BY_CONNTRACK_INFO(a_m_ct) \
    _CREATE_NF_ACTIONABLE_BY_CONNTRACK_ESTABLISHED(a_m_ct), \
    _CREATE_NF_ACTIONABLE_BY_CONNTRACK_RELATED(a_m_ct), \
    _CREATE_NF_ACTIONABLE_BY_CONNTRACK_NEW(a_m_ct), \
    _CREATE_NF_ACTIONABLE_BY_CONNTRACK_REPLY(a_m_ct), \
    _CREATE_NF_ACTIONABLE_BY_CONNTRACK_ESTABLISHED_REPLY(a_m_ct), \
    _CREATE_NF_ACTIONABLE_BY_CONNTRACK_RELATED_REPLY(a_m_ct), \
    _CREATE_NF_ACTIONABLE_BY_CONNTRACK_NUMBER(a_m_ct), \
    _CREATE_NF_ACTIONABLE_BY_CONNTRACK_UNTRACKED(a_m_ct)

#define _CREATE_NETWORK_FF_LIST_ITEMS_INCLUDE_NS_INFO(a_incl_ns) \
    _CREATE_NETWORK_INCLUDE_NS_INFO(a_incl_ns)


//

static bool handle_filter_func_is_netfilter_actionable_by_user(
    union filter_func_union *ffu
)
{
    struct filter_func_is_netfilter_actionable_by_user* f = &ffu->is_netfilter_actionable_by_user;
    return f->result_expected == global_filter_netfilter_user_is_actionable(f->arg_uid);
}

static bool handle_filter_func_is_netfilter_actionable_by_conntrack_info(
    union filter_func_union *ffu
)
{
    struct filter_func_is_netfilter_actionable_by_conntrack_info* f = &ffu->is_netfilter_actionable_by_conntrack_info;
    return f->result_expected == global_filter_netfilter_conntrack_info_is_actionable(f->arg_ct_info);
}

static bool handle_filter_func_is_netfilter_include_ns_info(
    union filter_func_union *ffu
)
{
    struct filter_func_is_netfilter_include_ns_info* f = &ffu->is_netfilter_include_ns_info;
    return f->result_expected == global_filter_netfilter_include_ns_info();
}

static bool handle_filter_func_is_netfilter_audit_hooks_on(
    union filter_func_union *ffu
)
{
    struct filter_func_is_netfilter_audit_hooks_on* f = &ffu->is_netfilter_audit_hooks_on;
    return f->result_expected == global_filter_netfilter_audit_hooks_on();
}

static bool handle_filter_func_is_network_include_ns_info(
    union filter_func_union *ffu
)
{
    struct filter_func_is_network_include_ns_info* f = &ffu->is_network_include_ns_info;
    return f->result_expected == global_filter_function_network_include_ns_info();
}

static bool handle_filter_func_is_function_post_execution_actionable(
    union filter_func_union *ffu
)
{
    struct filter_func_is_function_post_execution_actionable* f = &ffu->is_function_post_execution_actionable;
    return f->result_expected == global_filter_function_post_execution_is_actionable(
        f->arg_func_num, f->arg_func_success, f->arg_pid, f->arg_ppid, f->arg_uid
    );
}

static bool handle_filter_func_is_function_actionable_by_function_number(
    union filter_func_union *ffu
)
{
    struct filter_func_is_function_actionable_by_function_number* f = &ffu->is_function_actionable_by_function_number;
    return f->result_expected == global_filter_function_number_is_actionable(f->arg_func_num);
}

static bool handle_filter_func_is_function_actionable_by_function_success(
    union filter_func_union *ffu
)
{
    struct filter_func_is_function_actionable_by_function_success* f = &ffu->is_function_actionable_by_function_success;
    return f->result_expected == global_filter_function_success_is_actionable(f->arg_func_success);
}

static bool handle_filter_func_is_function_actionable_by_pid(
    union filter_func_union *ffu
)
{
    struct filter_func_is_function_actionable_by_pid* f = &ffu->is_function_actionable_by_pid;
    return f->result_expected == global_filter_function_pid_is_actionable(f->arg_pid);
}

static bool handle_filter_func_is_function_actionable_by_ppid(
    union filter_func_union *ffu
)
{
    struct filter_func_is_function_actionable_by_ppid* f = &ffu->is_function_actionable_by_ppid;
    return f->result_expected == global_filter_function_ppid_is_actionable(f->arg_ppid);
}

static bool handle_filter_func_is_function_actionable_by_uid(
    union filter_func_union *ffu
)
{
    struct filter_func_is_function_actionable_by_uid* f = &ffu->is_function_actionable_by_uid;
    return f->result_expected == global_filter_function_uid_is_actionable(f->arg_uid);
}

//

static const struct test_config TC_LIST[] = {
    {
        .type = TCT_NF,
        .id = "nf_capture_all_ct_for_u_1001_with_ns",
        .arg = {
            .include_ns_info = true,
            .nf = { .audit_hooks = false, .monitor_ct = TMC_ALL, .use_user = true },
            .monitor_user = { .m_mode = TMM_CAPTURE, .uids = { .len = 1, .arr = {1001} } } 
        },
        .ff_list = {
            .list = {
                _CREATE_NF_FF_LIST_ITEMS_ACTIONABLE_BY_USER(TMM_CAPTURE, 1001),
                _CREATE_NF_FF_LIST_ITEMS_INCLUDE_NS_INFO(true),
                _CREATE_NF_AUDIT_HOOKS_ON(false),
                _CREATE_NF_FF_LIST_ITEMS_ACTIONABLE_BY_CONNTRACK_INFO(TMC_ALL),
                {}
            }
        }
    },
    {
        .type = TCT_NF,
        .id = "nf_capture_only_new_ct_for_u_1001_without_ns",
        .arg = {
            .include_ns_info = false,
            .nf = { .audit_hooks = true, .monitor_ct = TMC_ONLY_NEW, .use_user = true },
            .monitor_user = { .m_mode = TMM_CAPTURE, .uids = { .len = 1, .arr = {1001} } }
        },
        .ff_list = {
            .list = {
                _CREATE_NF_FF_LIST_ITEMS_ACTIONABLE_BY_USER(TMM_CAPTURE, 1001),
                _CREATE_NF_FF_LIST_ITEMS_INCLUDE_NS_INFO(false),
                _CREATE_NF_AUDIT_HOOKS_ON(true),
                _CREATE_NF_FF_LIST_ITEMS_ACTIONABLE_BY_CONNTRACK_INFO(TMC_ONLY_NEW),
                {}
            }
        }
    },
    {
        .type = TCT_NF,
        .id = "nf_ignore_all_ct_for_u_1001_with_ns",
        .arg = {
            .include_ns_info = true,
            .nf = { .audit_hooks = true, .monitor_ct = TMC_ALL, .use_user = true },
            .monitor_user = { .m_mode = TMM_IGNORE, .uids = { .len = 1, .arr = {1001} } }
        },
        .ff_list = {
            .list = {
                _CREATE_NF_FF_LIST_ITEMS_ACTIONABLE_BY_USER(TMM_IGNORE, 1001),
                _CREATE_NF_FF_LIST_ITEMS_INCLUDE_NS_INFO(true),
                _CREATE_NF_AUDIT_HOOKS_ON(true),
                _CREATE_NF_FF_LIST_ITEMS_ACTIONABLE_BY_CONNTRACK_INFO(TMC_ALL),
                {}
            }
        }
    },
    {
        .type = TCT_NF,
        .id = "nf_ignore_only_new_ct_for_u_1001_with_ns",
        .arg = {
            .include_ns_info = true,
            .nf = { .audit_hooks = true, .monitor_ct = TMC_ONLY_NEW, .use_user = true },
            .monitor_user = { .m_mode = TMM_IGNORE, .uids = { .len = 1, .arr = {1001} } }
        },
        .ff_list = {
            .list = {
                _CREATE_NF_FF_LIST_ITEMS_ACTIONABLE_BY_USER(TMM_IGNORE, 1001),
                _CREATE_NF_FF_LIST_ITEMS_INCLUDE_NS_INFO(true),
                _CREATE_NF_AUDIT_HOOKS_ON(true),
                _CREATE_NF_FF_LIST_ITEMS_ACTIONABLE_BY_CONNTRACK_INFO(TMC_ONLY_NEW),
                {}
            }
        }
    },
    {
        .type = TCT_NF,
        .id = "nf_capture_all_users_because_not_using_users",
        .arg = {
            .include_ns_info = true,
            .nf = { .audit_hooks = true, .monitor_ct = TMC_ALL, .use_user = false },
            .monitor_user = { .m_mode = TMM_IGNORE, .uids = { .len = 1, .arr = {1001} } }
        },
        .ff_list = {
            .list = {
                _CREATE_NF_ACTIONABLE_BY_USER_MUST_CAPTURE(TMM_CAPTURE, 1001),
                _CREATE_NF_ACTIONABLE_BY_USER_MUST_CAPTURE(TMM_CAPTURE, 2001),
                {}
            }
        }
    },
    {
        .type = TCT_NETWORK,
        .id = "network_include_with_ns",
        .arg = {
            .include_ns_info = true,
            .nf = { .audit_hooks = true, .monitor_ct = TMC_ALL, .use_user = true },
            .monitor_user = { .m_mode = TMM_CAPTURE, .uids = { .len = 1, .arr = {1001} } }
        },
        .ff_list = {
            .list = {
                _CREATE_NETWORK_FF_LIST_ITEMS_INCLUDE_NS_INFO(true),
                {}
            }
        }
    },
    {
        .type = TCT_FUNCTION,
        .id = "function_1",
        .arg = {
            .monitor_user = {
                .m_mode = TMM_CAPTURE,
                .uids = {.len = 1, .arr = {1001}},
            },
            .monitor_pid = {
                .m_mode = TMM_IGNORE,
                .pids = {.len = 1, .arr = {101}}
            },
            .monitor_ppid = {
                .m_mode = TMM_IGNORE,
                .ppids = {.len = 1, .arr = {101}}
            },
            .monitor_function_result = TMFR_ONLY_SUCCESSFUL,
            .network_io = true,
            .include_ns_info = true,
            .nf = {}
        },
        .ff_list = {
            .list = {
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_CAPTURE(KERN_F_NUM_SYS_ACCEPT),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_CAPTURE(KERN_F_NUM_SYS_ACCEPT4),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_CAPTURE(KERN_F_NUM_SYS_BIND),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_CAPTURE(KERN_F_NUM_SYS_CONNECT),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_CAPTURE(KERN_F_NUM_SYS_KILL),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_CAPTURE(KERN_F_NUM_SYS_RECVFROM),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_CAPTURE(KERN_F_NUM_SYS_RECVMSG),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_CAPTURE(KERN_F_NUM_SYS_SENDMSG),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_CAPTURE(KERN_F_NUM_SYS_SENDMSG),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_CAPTURE(KERN_F_NUM_SYS_CLONE),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_CAPTURE(KERN_F_NUM_SYS_FORK),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_CAPTURE(KERN_F_NUM_SYS_SETNS),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_CAPTURE(KERN_F_NUM_SYS_UNSHARE),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_CAPTURE(KERN_F_NUM_SYS_VFORK),
                _CREATE_FUNCTION_ACTIONABLE_BY_PID_MUST_CAPTURE(201),
                _CREATE_FUNCTION_ACTIONABLE_BY_PPID_MUST_CAPTURE(201),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_SUCCESS_MUST_CAPTURE(true),
                _CREATE_FUNCTION_ACTIONABLE_BY_UID_MUST_CAPTURE(1001),
                _CREATE_FUNCTION_POST_EXECUTION_ACTIONABLE_MUST_CAPTURE(201, 201, KERN_F_NUM_SYS_ACCEPT, true, 1001),
                _CREATE_FUNCTION_ACTIONABLE_BY_PID_MUST_IGNORE(101),
                _CREATE_FUNCTION_ACTIONABLE_BY_PPID_MUST_IGNORE(101),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_SUCCESS_MUST_IGNORE(false),
                _CREATE_FUNCTION_ACTIONABLE_BY_UID_MUST_IGNORE(2001),
                _CREATE_FUNCTION_POST_EXECUTION_ACTIONABLE_MUST_IGNORE(101, 101, KERN_F_NUM_SYS_ACCEPT, false, 2001),
                {}
            }
        }
    },
    {
        .type = TCT_FUNCTION,
        .id = "function_2",
        /* Testing multiple-uids,multiple-pids,multiple-ppids,network-io-false,include-ns-info-false since function_1 */
        .arg = {
            .monitor_user = {
                .m_mode = TMM_CAPTURE,
                .uids = {.len = 2, .arr = {1001, 1002}},
            },
            .monitor_pid = {
                .m_mode = TMM_IGNORE,
                .pids = {.len = 2, .arr = {101, 102}},
            },
            .monitor_ppid = {
                .m_mode = TMM_IGNORE,
                .ppids = {.len = 2, .arr = {101, 102}},
            },
            .monitor_function_result = TMFR_ONLY_SUCCESSFUL,
            .network_io = false,
            .include_ns_info = false,
            .nf = {}
        },
        .ff_list = {
            .list = {
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_CAPTURE(KERN_F_NUM_SYS_ACCEPT),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_CAPTURE(KERN_F_NUM_SYS_ACCEPT4),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_CAPTURE(KERN_F_NUM_SYS_BIND),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_CAPTURE(KERN_F_NUM_SYS_CONNECT),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_CAPTURE(KERN_F_NUM_SYS_KILL),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_IGNORE(KERN_F_NUM_SYS_RECVFROM),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_IGNORE(KERN_F_NUM_SYS_RECVMSG),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_IGNORE(KERN_F_NUM_SYS_SENDMSG),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_IGNORE(KERN_F_NUM_SYS_SENDMSG),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_IGNORE(KERN_F_NUM_SYS_CLONE),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_IGNORE(KERN_F_NUM_SYS_FORK),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_IGNORE(KERN_F_NUM_SYS_SETNS),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_IGNORE(KERN_F_NUM_SYS_UNSHARE),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_NUM_MUST_IGNORE(KERN_F_NUM_SYS_VFORK),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_SUCCESS_MUST_CAPTURE(true),
                _CREATE_FUNCTION_ACTIONABLE_BY_UID_MUST_CAPTURE(1001),
                _CREATE_FUNCTION_ACTIONABLE_BY_UID_MUST_CAPTURE(1002),
                _CREATE_FUNCTION_POST_EXECUTION_ACTIONABLE_MUST_CAPTURE(201, 201, KERN_F_NUM_SYS_ACCEPT, true, 1001),
                _CREATE_FUNCTION_ACTIONABLE_BY_PID_MUST_IGNORE(101),
                _CREATE_FUNCTION_ACTIONABLE_BY_PPID_MUST_IGNORE(101),
                _CREATE_FUNCTION_ACTIONABLE_BY_PID_MUST_IGNORE(102),
                _CREATE_FUNCTION_ACTIONABLE_BY_PPID_MUST_IGNORE(102),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_SUCCESS_MUST_IGNORE(false),
                _CREATE_FUNCTION_POST_EXECUTION_ACTIONABLE_MUST_IGNORE(101, 102, KERN_F_NUM_SYS_RECVFROM, false, 2001),
                {}
            }
        }
    },
    {
        .type = TCT_FUNCTION,
        .id = "function_3",
        /* Testing TMFR_ALL,TMM_IGNORE since function_2 */
        .arg = {
            .monitor_user = {
                .m_mode = TMM_IGNORE,
                .uids = {.len = 1, .arr = {1001}},
            },
            .monitor_pid = {
                .m_mode = TMM_IGNORE,
                .pids = {.len = 1, .arr = {101}},
            },
            .monitor_ppid = {
                .m_mode = TMM_IGNORE,
                .ppids = {.len = 1, .arr = {101}},
            },
            .monitor_function_result = TMFR_ALL,
            .network_io = false,
            .include_ns_info = false,
            .nf = {}
        },
        .ff_list = {
            .list = {
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_SUCCESS_MUST_CAPTURE(true),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_SUCCESS_MUST_CAPTURE(false),
                _CREATE_FUNCTION_ACTIONABLE_BY_UID_MUST_CAPTURE(2001),
                _CREATE_FUNCTION_ACTIONABLE_BY_UID_MUST_IGNORE(1001),
                _CREATE_FUNCTION_POST_EXECUTION_ACTIONABLE_MUST_CAPTURE(201, 201, KERN_F_NUM_SYS_ACCEPT, true, 2001),
                _CREATE_FUNCTION_POST_EXECUTION_ACTIONABLE_MUST_IGNORE(101, 101, KERN_F_NUM_SYS_RECVFROM, false, 1001),
                {}
            }
        }
    },
    {
        .type = TCT_FUNCTION,
        .id = "function_4",
        /* Testing only TMFR_ONLY_FAILED since function_3 */
        .arg = {
            .monitor_user = {
                .m_mode = TMM_IGNORE,
                .uids = {.len = 1, .arr = {1001}},
            },
            .monitor_pid = {
                .m_mode = TMM_IGNORE,
                .pids = {.len = 1, .arr = {101}},
            },
            .monitor_ppid = {
                .m_mode = TMM_IGNORE,
                .ppids = {.len = 1, .arr = {101}},
            },
            .monitor_function_result = TMFR_ONLY_FAILED,
            .network_io = false,
            .include_ns_info = false,
            .nf = {}
        },
        .ff_list = {
            .list = {
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_SUCCESS_MUST_IGNORE(true),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_SUCCESS_MUST_CAPTURE(false),
                _CREATE_FUNCTION_POST_EXECUTION_ACTIONABLE_MUST_CAPTURE(201, 201, KERN_F_NUM_SYS_ACCEPT, false, 2001),
                _CREATE_FUNCTION_POST_EXECUTION_ACTIONABLE_MUST_IGNORE(101, 101, KERN_F_NUM_SYS_RECVFROM, true, 1001),
                {}
            }
        }
    },
    {
        .type = TCT_FUNCTION,
        .id = "function_5",
        /* Testing pid and ppid in capture mode (TMM_CAPTURE) based on function_4 */
        .arg = {
            .monitor_user = {
                .m_mode = TMM_IGNORE,
                .uids = {.len = 1, .arr = {1001}},
            },
            .monitor_pid = {
                .m_mode = TMM_CAPTURE,
                .pids = {.len = 1, .arr = {101}},
            },
            .monitor_ppid = {
                .m_mode = TMM_CAPTURE,
                .ppids = {.len = 1, .arr = {101}},
            },
            .monitor_function_result = TMFR_ONLY_FAILED,
            .network_io = false,
            .include_ns_info = false,
            .nf = {}
        },
        .ff_list = {
            .list = {
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_SUCCESS_MUST_IGNORE(true),
                _CREATE_FUNCTION_ACTIONABLE_BY_FUNC_SUCCESS_MUST_CAPTURE(false),
                _CREATE_FUNCTION_ACTIONABLE_BY_PID_MUST_CAPTURE(101),
                _CREATE_FUNCTION_ACTIONABLE_BY_PPID_MUST_CAPTURE(101),
                _CREATE_FUNCTION_ACTIONABLE_BY_PID_MUST_IGNORE(201),
                _CREATE_FUNCTION_ACTIONABLE_BY_PPID_MUST_IGNORE(201),
                _CREATE_FUNCTION_POST_EXECUTION_ACTIONABLE_MUST_CAPTURE(101, 101, KERN_F_NUM_SYS_ACCEPT, false, 2001),
                _CREATE_FUNCTION_POST_EXECUTION_ACTIONABLE_MUST_IGNORE(201, 201, KERN_F_NUM_SYS_RECVFROM, true, 1001),
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

    if (global_is_initialized())
        global_deinit();
}

static void test_global_test_init_deinit(struct test_stats *stats)
{
    const char *test_name = "test_global_test_init_deinit";
    int err;
    bool dry_run = true;

    _ensure_global_arg_is_reset();

    _ensure_global_reset();

    stats->total++;

    if (global_is_initialized())
    {
        TEST_FAIL(stats, test_name, "global marked as initialized without initialization");
        return;
    }

    err = global_init(dry_run);
    if (err != 0 || !global_is_initialized())
    {
        TEST_FAIL(stats, test_name, "global failed to init. Err: %d", err);
        return;
    }

    err = global_auditing_start(&global_arg);
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

    err = global_deinit();
    if (err != 0 || global_is_initialized())
    {
        TEST_FAIL(stats, test_name, "global context failed to deinit. Err: %d", err);
        _ensure_global_reset();
        return;
    }

    TEST_PASS(stats, test_name);
}

static void test_global_test_event_filtering_func(struct test_stats *stats, const struct test_config *tc, struct filter_func *ff)
{
    const char *test_name = "test_global_test_event_filtering_func";
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

static void test_global_test_event_filtering_func_list(struct test_stats *stats, const struct test_config *tc, struct filter_func_list *ffl)
{
    int i;

    for (i = 0; i < FF_LIST_LEN; i++)
    {
        struct filter_func *ff = &ffl->list[i];
        if (!ff || !ff->header.test)
            continue;
        if (ff->header.type == FFT_NULL)
            break;
     
        test_global_test_event_filtering_func(stats, tc, ff);
    }
}

static void test_global_test_event_filtering_test_configs(struct test_stats *stats)
{
    const char *test_name = "test_global_test_event_filtering_test_configs";
    int err, i;
    const bool dry_run = true;

    _ensure_global_reset();

    stats->total++;

    err = global_init(dry_run);
    if (err != 0 || !global_is_initialized())
    {
        TEST_FAIL(stats, test_name, "global failed to init. Err: %d", err);
        return;
    }

    for (i = 0; i < TC_LIST_LEN; i++)
    {
        const struct test_config *tc = &TC_LIST[i];
        const struct arg *arg = &tc->arg;
        struct filter_func_list *ff_list;
        if (!tc)
            continue;
        if (tc->type == TCT_NULL)
            break;

        stats->total++;

        ff_list = (struct filter_func_list *)&tc->ff_list;

        err = global_auditing_start(arg);
        if (err != 0 || !global_is_auditing_started())
        {
            TEST_FAIL(stats, test_name, "global auditing failed to start with test config at index: %d. Err: %d", i, err);
            break;
        }

        test_global_test_event_filtering_func_list(stats, tc, ff_list);

        err = global_auditing_stop();
        if (err != 0 || global_is_auditing_started())
        {
            TEST_FAIL(stats, test_name, "global auditing failed to stop with test config at index: %d. Err: %d", i, err);
            break;
        }

        TEST_PASS(stats, test_name);
    }

    err = global_deinit();
    if (err != 0 || global_is_initialized())
    {
        TEST_FAIL(stats, test_name, "global failed to deinit. Err: %d", err);
        return;
    }

    TEST_PASS(stats, test_name);
}

int test_global_all(struct test_stats *stats)
{
    test_stats_init(stats);
    util_log_info("test_global", "Starting tests");

    test_global_test_init_deinit(stats);
    test_global_test_event_filtering_test_configs(stats);

    return 0;
}