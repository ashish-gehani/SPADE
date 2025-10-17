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

#include "test/kernel/spade/audit/common.h"
#include "test/kernel/spade/audit/global.h"

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
    FFT_NF_AUDIT_HOOKS_ON,
    FFT_NETWORK_LOGGING_NS_INFO,
    FFT_SYSCALL_LOGGABLE,
    FFT_SYSCALL_LOGGABLE_BY_SYS_NUM,
    FFT_SYSCALL_LOGGABLE_BY_SYS_SUCCESS,
    FFT_SYSCALL_LOGGABLE_BY_PID,
    FFT_SYSCALL_LOGGABLE_BY_PPID,
    FFT_SYSCALL_LOGGABLE_BY_UID
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

struct filter_func_is_netfilter_audit_hooks_on
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

struct filter_func_is_syscall_loggable_by_sys_num
{
    int arg_sys_num;
    bool result_expected;
};

struct filter_func_is_syscall_loggable_by_sys_success
{
    bool arg_sys_success;
    bool result_expected;
};

struct filter_func_is_syscall_loggable_by_pid
{
    pid_t arg_pid;
    bool result_expected;
};

struct filter_func_is_syscall_loggable_by_ppid
{
    pid_t arg_ppid;
    bool result_expected;
};

struct filter_func_is_syscall_loggable_by_uid
{
    uid_t arg_uid;
    bool result_expected;
};

union filter_func_union
{
    struct filter_func_is_netfilter_loggable_by_user is_netfilter_loggable_by_user;
    struct filter_func_is_netfilter_loggable_by_conntrack_info is_netfilter_loggable_by_conntrack_info;
    struct filter_func_is_netfilter_logging_ns_info is_netfilter_logging_ns_info;
    struct filter_func_is_netfilter_audit_hooks_on is_netfilter_audit_hooks_on;
    struct filter_func_is_network_logging_ns_info is_network_logging_ns_info;
    struct filter_func_is_syscall_loggable is_syscall_loggable;
    struct filter_func_is_syscall_loggable_by_sys_num is_syscall_loggable_by_sys_num;
    struct filter_func_is_syscall_loggable_by_sys_success is_syscall_loggable_by_sys_success;
    struct filter_func_is_syscall_loggable_by_pid is_syscall_loggable_by_pid;
    struct filter_func_is_syscall_loggable_by_ppid is_syscall_loggable_by_ppid;
    struct filter_func_is_syscall_loggable_by_uid is_syscall_loggable_by_uid;
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

#define _CREATE_SYSCALL_FF_LOGGABLE_BY_SYS_NUM(a_id, a_sys_num, a_result) \
    { \
        .header = { \
            .type = FFT_SYSCALL_LOGGABLE_BY_SYS_NUM, \
            .id = a_id, \
            .test = handle_filter_func_is_syscall_loggable_by_sys_num, \
        }, \
        .ff = { \
            .is_syscall_loggable_by_sys_num = { \
                .arg_sys_num = a_sys_num, \
                .result_expected = a_result \
            } \
        } \
    }

#define _CREATE_SYSCALL_FF_LOGGABLE_BY_SYS_SUCCESS(a_id, a_sys_success, a_result) \
    { \
        .header = { \
            .type = FFT_SYSCALL_LOGGABLE_BY_SYS_SUCCESS, \
            .id = a_id, \
            .test = handle_filter_func_is_syscall_loggable_by_sys_success, \
        }, \
        .ff = { \
            .is_syscall_loggable_by_sys_success = { \
                .arg_sys_success = a_sys_success, \
                .result_expected = a_result \
            } \
        } \
    }

#define _CREATE_SYSCALL_FF_LOGGABLE_BY_PID(a_id, a_pid, a_result) \
    { \
        .header = { \
            .type = FFT_SYSCALL_LOGGABLE_BY_PID, \
            .id = a_id, \
            .test = handle_filter_func_is_syscall_loggable_by_pid, \
        }, \
        .ff = { \
            .is_syscall_loggable_by_pid = { \
                .arg_pid = a_pid, \
                .result_expected = a_result \
            } \
        } \
    }

#define _CREATE_SYSCALL_FF_LOGGABLE_BY_PPID(a_id, a_ppid, a_result) \
    { \
        .header = { \
            .type = FFT_SYSCALL_LOGGABLE_BY_PPID, \
            .id = a_id, \
            .test = handle_filter_func_is_syscall_loggable_by_ppid, \
        }, \
        .ff = { \
            .is_syscall_loggable_by_ppid = { \
                .arg_ppid = a_ppid, \
                .result_expected = a_result \
            } \
        } \
    }

#define _CREATE_SYSCALL_FF_LOGGABLE_BY_UID(a_id, a_uid, a_result) \
    { \
        .header = { \
            .type = FFT_SYSCALL_LOGGABLE_BY_UID, \
            .id = a_id, \
            .test = handle_filter_func_is_syscall_loggable_by_uid, \
        }, \
        .ff = { \
            .is_syscall_loggable_by_uid = { \
                .arg_uid = a_uid, \
                .result_expected = a_result \
            } \
        } \
    }

//

// Wrapper macros for netfilter loggable by user tests
#define _CREATE_NF_LOGGABLE_BY_USER_MUST_CAPTURE(a_u_mode, a_uid) \
    _CREATE_NF_FF_LOGGABLE_BY_USER("nf_loggable_by_user_must_capture", a_uid, ((a_u_mode) == AMM_CAPTURE))

#define _CREATE_NF_LOGGABLE_BY_USER_MUST_IGNORE(a_u_mode, a_uid) \
    _CREATE_NF_FF_LOGGABLE_BY_USER("nf_loggable_by_user_must_ignore", (a_uid)+1, ((a_u_mode) == AMM_IGNORE))

// Wrapper macro for netfilter logging namespace info
#define _CREATE_NF_LOGGING_NS_INFO(a_incl_ns) \
    _CREATE_NF_FF_LOGGING_NS_INFO("nf_logging_ns_info", ((a_incl_ns) == true))

// Wrapper macro for netfilter audit hooks on
#define _CREATE_NF_AUDIT_HOOKS_ON(a_audit_hooks) \
    _CREATE_NF_FF_AUDIT_HOOKS_ON("nf_audit_hooks_on", ((a_audit_hooks) == true))

// Wrapper macros for netfilter loggable by conntrack info tests
#define _CREATE_NF_LOGGABLE_BY_CONNTRACK_ESTABLISHED(a_m_ct) \
    _CREATE_NF_FF_LOGGABLE_BY_CONNTRACK_INFO("nf_loggable_by_conntrack_estblshd_match_all", IP_CT_ESTABLISHED, ((a_m_ct) == AMMC_ALL))

#define _CREATE_NF_LOGGABLE_BY_CONNTRACK_RELATED(a_m_ct) \
    _CREATE_NF_FF_LOGGABLE_BY_CONNTRACK_INFO("nf_loggable_by_conntrack_rltd_match_all", IP_CT_RELATED, ((a_m_ct) == AMMC_ALL))

#define _CREATE_NF_LOGGABLE_BY_CONNTRACK_NEW(a_m_ct) \
    _CREATE_NF_FF_LOGGABLE_BY_CONNTRACK_INFO("nf_loggable_by_conntrack_new_match_all_or_new", IP_CT_NEW, ((a_m_ct) == AMMC_ALL || (a_m_ct) == AMMC_ONLY_NEW))

#define _CREATE_NF_LOGGABLE_BY_CONNTRACK_REPLY(a_m_ct) \
    _CREATE_NF_FF_LOGGABLE_BY_CONNTRACK_INFO("nf_loggable_by_conntrack_rply_match_all", IP_CT_IS_REPLY, ((a_m_ct) == AMMC_ALL))

#define _CREATE_NF_LOGGABLE_BY_CONNTRACK_ESTABLISHED_REPLY(a_m_ct) \
    _CREATE_NF_FF_LOGGABLE_BY_CONNTRACK_INFO("nf_loggable_by_conntrack_estblshdrply_match_all", IP_CT_ESTABLISHED_REPLY, ((a_m_ct) == AMMC_ALL))

#define _CREATE_NF_LOGGABLE_BY_CONNTRACK_RELATED_REPLY(a_m_ct) \
    _CREATE_NF_FF_LOGGABLE_BY_CONNTRACK_INFO("nf_loggable_by_conntrack_rltdrply_match_all", IP_CT_RELATED_REPLY, ((a_m_ct) == AMMC_ALL))

#define _CREATE_NF_LOGGABLE_BY_CONNTRACK_NUMBER(a_m_ct) \
    _CREATE_NF_FF_LOGGABLE_BY_CONNTRACK_INFO("nf_loggable_by_conntrack_num_match_all", IP_CT_NUMBER, ((a_m_ct) == AMMC_ALL))

#define _CREATE_NF_LOGGABLE_BY_CONNTRACK_UNTRACKED(a_m_ct) \
    _CREATE_NF_FF_LOGGABLE_BY_CONNTRACK_INFO("nf_loggable_by_conntrack_untrckd_match_all", IP_CT_UNTRACKED, ((a_m_ct) == AMMC_ALL))

// Wrapper macro for network logging namespace info
#define _CREATE_NETWORK_LOGGING_NS_INFO(a_incl_ns) \
    _CREATE_NETWORK_FF_LOGGING_NS_INFO("network_logging_ns_info", ((a_incl_ns) == true))

// Wrapper macros for syscall loggable tests - Full parameters
#define _CREATE_SYSCALL_LOGGABLE_MUST_CAPTURE(a_pid, a_ppid, a_sys_num, a_sys_success, a_uid) \
    _CREATE_SYSCALL_FF_LOGGABLE("syscall_is_loggable_must_capture", a_pid, a_ppid, a_sys_num, a_sys_success, a_uid, true)

#define _CREATE_SYSCALL_LOGGABLE_MUST_IGNORE(a_pid, a_ppid, a_sys_num, a_sys_success, a_uid) \
    _CREATE_SYSCALL_FF_LOGGABLE("syscall_is_loggable_must_ignore", a_pid, a_ppid, a_sys_num, a_sys_success, (a_uid)+1, false)

// Wrapper macros for syscall loggable tests - By syscall number
#define _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_CAPTURE(a_sys_num) \
    _CREATE_SYSCALL_FF_LOGGABLE_BY_SYS_NUM("syscall_loggable_by_sys_num_must_capture", a_sys_num, true)

#define _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_IGNORE(a_sys_num) \
    _CREATE_SYSCALL_FF_LOGGABLE_BY_SYS_NUM("syscall_loggable_by_sys_num_must_ignore", a_sys_num, false)

// Wrapper macros for syscall loggable tests - By success/failure
#define _CREATE_SYSCALL_LOGGABLE_BY_SYS_SUCCESS_MUST_CAPTURE(a_sys_success) \
    _CREATE_SYSCALL_FF_LOGGABLE_BY_SYS_SUCCESS("syscall_loggable_by_success_must_capture", a_sys_success, true)

#define _CREATE_SYSCALL_LOGGABLE_BY_SYS_SUCCESS_MUST_IGNORE(a_sys_success) \
    _CREATE_SYSCALL_FF_LOGGABLE_BY_SYS_SUCCESS("syscall_loggable_by_success_must_ignore", a_sys_success, false)

// Wrapper macros for syscall loggable tests - By PID
#define _CREATE_SYSCALL_LOGGABLE_BY_PID_MUST_CAPTURE(a_pid) \
    _CREATE_SYSCALL_FF_LOGGABLE_BY_PID("syscall_loggable_by_pid_must_capture", a_pid, true)

#define _CREATE_SYSCALL_LOGGABLE_BY_PID_MUST_IGNORE(a_pid) \
    _CREATE_SYSCALL_FF_LOGGABLE_BY_PID("syscall_loggable_by_pid_must_ignore", a_pid, false)

// Wrapper macros for syscall loggable tests - By PPID
#define _CREATE_SYSCALL_LOGGABLE_BY_PPID_MUST_CAPTURE(a_ppid) \
    _CREATE_SYSCALL_FF_LOGGABLE_BY_PPID("syscall_loggable_by_ppid_must_capture", a_ppid, true)

#define _CREATE_SYSCALL_LOGGABLE_BY_PPID_MUST_IGNORE(a_ppid) \
    _CREATE_SYSCALL_FF_LOGGABLE_BY_PPID("syscall_loggable_by_ppid_must_ignore", a_ppid, false)

// Wrapper macros for syscall loggable tests - By UID
#define _CREATE_SYSCALL_LOGGABLE_BY_UID_MUST_CAPTURE(a_uid) \
    _CREATE_SYSCALL_FF_LOGGABLE_BY_UID("syscall_loggable_by_uid_must_capture", a_uid, true)

#define _CREATE_SYSCALL_LOGGABLE_BY_UID_MUST_IGNORE(a_uid) \
    _CREATE_SYSCALL_FF_LOGGABLE_BY_UID("syscall_loggable_by_uid_must_ignore", a_uid, false)

//

#define _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_USER(a_u_mode, a_uid) \
    _CREATE_NF_LOGGABLE_BY_USER_MUST_CAPTURE(a_u_mode, a_uid), \
    _CREATE_NF_LOGGABLE_BY_USER_MUST_IGNORE(a_u_mode, a_uid)

#define _CREATE_NF_FF_LIST_ITEMS_LOGGING_NS_INFO(a_incl_ns) \
    _CREATE_NF_LOGGING_NS_INFO(a_incl_ns)

#define _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_CONNTRACK_INFO(a_m_ct) \
    _CREATE_NF_LOGGABLE_BY_CONNTRACK_ESTABLISHED(a_m_ct), \
    _CREATE_NF_LOGGABLE_BY_CONNTRACK_RELATED(a_m_ct), \
    _CREATE_NF_LOGGABLE_BY_CONNTRACK_NEW(a_m_ct), \
    _CREATE_NF_LOGGABLE_BY_CONNTRACK_REPLY(a_m_ct), \
    _CREATE_NF_LOGGABLE_BY_CONNTRACK_ESTABLISHED_REPLY(a_m_ct), \
    _CREATE_NF_LOGGABLE_BY_CONNTRACK_RELATED_REPLY(a_m_ct), \
    _CREATE_NF_LOGGABLE_BY_CONNTRACK_NUMBER(a_m_ct), \
    _CREATE_NF_LOGGABLE_BY_CONNTRACK_UNTRACKED(a_m_ct)

#define _CREATE_NETWORK_FF_LIST_ITEMS_LOGGING_NS_INFO(a_incl_ns) \
    _CREATE_NETWORK_LOGGING_NS_INFO(a_incl_ns)


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

static bool handle_filter_func_is_netfilter_audit_hooks_on(
    union filter_func_union *ffu
)
{
    struct filter_func_is_netfilter_audit_hooks_on* f = &ffu->is_netfilter_audit_hooks_on;
    return f->result_expected == global_is_netfilter_audit_hooks_on();
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

static bool handle_filter_func_is_syscall_loggable_by_sys_num(
    union filter_func_union *ffu
)
{
    struct filter_func_is_syscall_loggable_by_sys_num* f = &ffu->is_syscall_loggable_by_sys_num;
    return f->result_expected == global_is_syscall_loggable_by_sys_num(f->arg_sys_num);
}

static bool handle_filter_func_is_syscall_loggable_by_sys_success(
    union filter_func_union *ffu
)
{
    struct filter_func_is_syscall_loggable_by_sys_success* f = &ffu->is_syscall_loggable_by_sys_success;
    return f->result_expected == global_is_syscall_loggable_by_sys_success(f->arg_sys_success);
}

static bool handle_filter_func_is_syscall_loggable_by_pid(
    union filter_func_union *ffu
)
{
    struct filter_func_is_syscall_loggable_by_pid* f = &ffu->is_syscall_loggable_by_pid;
    return f->result_expected == global_is_syscall_loggable_by_pid(f->arg_pid);
}

static bool handle_filter_func_is_syscall_loggable_by_ppid(
    union filter_func_union *ffu
)
{
    struct filter_func_is_syscall_loggable_by_ppid* f = &ffu->is_syscall_loggable_by_ppid;
    return f->result_expected == global_is_syscall_loggable_by_ppid(f->arg_ppid);
}

static bool handle_filter_func_is_syscall_loggable_by_uid(
    union filter_func_union *ffu
)
{
    struct filter_func_is_syscall_loggable_by_uid* f = &ffu->is_syscall_loggable_by_uid;
    return f->result_expected == global_is_syscall_loggable_by_uid(f->arg_uid);
}

//

static const struct test_config TC_LIST[] = {
    {
        .type = TCT_NF,
        .id = "nf_capture_all_ct_for_u_1001_with_ns",
        .arg = {
            .include_ns_info = true,
            .nf = { .audit_hooks = false, .monitor_ct = AMMC_ALL, .use_user = true },
            .user = { .uid_monitor_mode = AMM_CAPTURE, .uids = { .len = 1, .arr = {1001} } } 
        },
        .ff_list = {
            .list = {
                _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_USER(AMM_CAPTURE, 1001),
                _CREATE_NF_FF_LIST_ITEMS_LOGGING_NS_INFO(true),
                _CREATE_NF_AUDIT_HOOKS_ON(false),
                _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_CONNTRACK_INFO(AMMC_ALL),
                {}
            }
        }
    },
    {
        .type = TCT_NF,
        .id = "nf_capture_only_new_ct_for_u_1001_without_ns",
        .arg = {
            .include_ns_info = false,
            .nf = { .audit_hooks = true, .monitor_ct = AMMC_ONLY_NEW, .use_user = true },
            .user = { .uid_monitor_mode = AMM_CAPTURE, .uids = { .len = 1, .arr = {1001} } }
        },
        .ff_list = {
            .list = {
                _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_USER(AMM_CAPTURE, 1001),
                _CREATE_NF_FF_LIST_ITEMS_LOGGING_NS_INFO(false),
                _CREATE_NF_AUDIT_HOOKS_ON(true),
                _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_CONNTRACK_INFO(AMMC_ONLY_NEW),
                {}
            }
        }
    },
    {
        .type = TCT_NF,
        .id = "nf_ignore_all_ct_for_u_1001_with_ns",
        .arg = {
            .include_ns_info = true,
            .nf = { .audit_hooks = true, .monitor_ct = AMMC_ALL, .use_user = true },
            .user = { .uid_monitor_mode = AMM_IGNORE, .uids = { .len = 1, .arr = {1001} } }
        },
        .ff_list = {
            .list = {
                _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_USER(AMM_IGNORE, 1001),
                _CREATE_NF_FF_LIST_ITEMS_LOGGING_NS_INFO(true),
                _CREATE_NF_AUDIT_HOOKS_ON(true),
                _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_CONNTRACK_INFO(AMMC_ALL),
                {}
            }
        }
    },
    {
        .type = TCT_NF,
        .id = "nf_ignore_only_new_ct_for_u_1001_with_ns",
        .arg = {
            .include_ns_info = true,
            .nf = { .audit_hooks = true, .monitor_ct = AMMC_ONLY_NEW, .use_user = true },
            .user = { .uid_monitor_mode = AMM_IGNORE, .uids = { .len = 1, .arr = {1001} } }
        },
        .ff_list = {
            .list = {
                _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_USER(AMM_IGNORE, 1001),
                _CREATE_NF_FF_LIST_ITEMS_LOGGING_NS_INFO(true),
                _CREATE_NF_AUDIT_HOOKS_ON(true),
                _CREATE_NF_FF_LIST_ITEMS_LOGGABLE_BY_CONNTRACK_INFO(AMMC_ONLY_NEW),
                {}
            }
        }
    },
    {
        .type = TCT_NF,
        .id = "nf_capture_all_users_because_not_using_users",
        .arg = {
            .include_ns_info = true,
            .nf = { .audit_hooks = true, .monitor_ct = AMMC_ALL, .use_user = false },
            .user = { .uid_monitor_mode = AMM_IGNORE, .uids = { .len = 1, .arr = {1001} } } 
        },
        .ff_list = {
            .list = {
                _CREATE_NF_LOGGABLE_BY_USER_MUST_CAPTURE(AMM_CAPTURE, 1001),
                _CREATE_NF_LOGGABLE_BY_USER_MUST_CAPTURE(AMM_CAPTURE, 2001),
                {}
            }
        }
    },
    {
        .type = TCT_NETWORK,
        .id = "network_logging_with_ns",
        .arg = {
            .include_ns_info = true,
            .nf = { .audit_hooks = true, .monitor_ct = AMMC_ALL, .use_user = true },
            .user = { .uid_monitor_mode = AMM_CAPTURE, .uids = { .len = 1, .arr = {1001} } } 
        },
        .ff_list = {
            .list = {
                _CREATE_NETWORK_FF_LIST_ITEMS_LOGGING_NS_INFO(true),
                {}
            }
        }
    },
    {
        .type = TCT_SYSCALL,
        .id = "syscall_1",
        .arg = {
            .user = {
                .uid_monitor_mode = AMM_CAPTURE,
                .uids = {.len = 1, .arr = {1001}},
            },
            .ignore_pids = {.len = 1, .arr = {101}},
            .ignore_ppids = {.len = 1, .arr = {101}},
            .monitor_syscalls = AMMS_ONLY_SUCCESSFUL,
            .network_io = true,
            .include_ns_info = true,
            .nf = {}
        },
        .ff_list = {
            .list = {
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_CAPTURE(__NR_accept),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_CAPTURE(__NR_accept4),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_CAPTURE(__NR_bind),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_CAPTURE(__NR_connect),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_CAPTURE(__NR_kill),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_CAPTURE(__NR_recvfrom),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_CAPTURE(__NR_recvmsg),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_CAPTURE(__NR_sendmsg),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_CAPTURE(__NR_sendto),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_CAPTURE(__NR_clone),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_CAPTURE(__NR_fork),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_CAPTURE(__NR_setns),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_CAPTURE(__NR_unshare),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_CAPTURE(__NR_vfork),
                _CREATE_SYSCALL_LOGGABLE_BY_PID_MUST_CAPTURE(201),
                _CREATE_SYSCALL_LOGGABLE_BY_PPID_MUST_CAPTURE(201),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_SUCCESS_MUST_CAPTURE(true),
                _CREATE_SYSCALL_LOGGABLE_BY_UID_MUST_CAPTURE(1001),
                _CREATE_SYSCALL_LOGGABLE_MUST_CAPTURE(201, 201, __NR_accept, true, 1001),
                _CREATE_SYSCALL_LOGGABLE_BY_PID_MUST_IGNORE(101),
                _CREATE_SYSCALL_LOGGABLE_BY_PPID_MUST_IGNORE(101),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_SUCCESS_MUST_IGNORE(false),
                _CREATE_SYSCALL_LOGGABLE_BY_UID_MUST_IGNORE(2001),
                _CREATE_SYSCALL_LOGGABLE_MUST_IGNORE(101, 101, __NR_accept, false, 2001),
                {}
            }
        }
    },
    {
        .type = TCT_SYSCALL,
        .id = "syscall_2",
        /* Testing multiple-uids,multiple-pids,multiple-ppids,network-io-false,include-ns-info-false since syscall_1 */
        .arg = {
            .user = {
                .uid_monitor_mode = AMM_CAPTURE,
                .uids = {.len = 2, .arr = {1001, 1002}},
            },
            .ignore_pids = {.len = 2, .arr = {101, 102}},
            .ignore_ppids = {.len = 2, .arr = {101, 102}},
            .monitor_syscalls = AMMS_ONLY_SUCCESSFUL,
            .network_io = false,
            .include_ns_info = false,
            .nf = {}
        },
        .ff_list = {
            .list = {
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_CAPTURE(__NR_accept),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_CAPTURE(__NR_accept4),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_CAPTURE(__NR_bind),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_CAPTURE(__NR_connect),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_CAPTURE(__NR_kill),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_IGNORE(__NR_recvfrom),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_IGNORE(__NR_recvmsg),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_IGNORE(__NR_sendmsg),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_IGNORE(__NR_sendto),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_IGNORE(__NR_clone),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_IGNORE(__NR_fork),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_IGNORE(__NR_setns),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_IGNORE(__NR_unshare),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_NUM_MUST_IGNORE(__NR_vfork),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_SUCCESS_MUST_CAPTURE(true),
                _CREATE_SYSCALL_LOGGABLE_BY_UID_MUST_CAPTURE(1001),
                _CREATE_SYSCALL_LOGGABLE_BY_UID_MUST_CAPTURE(1002),
                _CREATE_SYSCALL_LOGGABLE_MUST_CAPTURE(201, 201, __NR_accept, true, 1001),
                _CREATE_SYSCALL_LOGGABLE_BY_PID_MUST_IGNORE(101),
                _CREATE_SYSCALL_LOGGABLE_BY_PPID_MUST_IGNORE(101),
                _CREATE_SYSCALL_LOGGABLE_BY_PID_MUST_IGNORE(102),
                _CREATE_SYSCALL_LOGGABLE_BY_PPID_MUST_IGNORE(102),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_SUCCESS_MUST_IGNORE(false),
                _CREATE_SYSCALL_LOGGABLE_MUST_IGNORE(101, 102, __NR_recvfrom, false, 2001),
                {}
            }
        }
    },
    {
        .type = TCT_SYSCALL,
        .id = "syscall_3",
        /* Testing AMMS_ALL,AMM_IGNORE since syscall_2 */
        .arg = {
            .user = {
                .uid_monitor_mode = AMM_IGNORE,
                .uids = {.len = 1, .arr = {1001}},
            },
            .ignore_pids = {.len = 1, .arr = {101}},
            .ignore_ppids = {.len = 1, .arr = {101}},
            .monitor_syscalls = AMMS_ALL,
            .network_io = false,
            .include_ns_info = false,
            .nf = {}
        },
        .ff_list = {
            .list = {
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_SUCCESS_MUST_CAPTURE(true),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_SUCCESS_MUST_CAPTURE(false),
                _CREATE_SYSCALL_LOGGABLE_BY_UID_MUST_CAPTURE(2001),
                _CREATE_SYSCALL_LOGGABLE_BY_UID_MUST_IGNORE(1001),
                _CREATE_SYSCALL_LOGGABLE_MUST_CAPTURE(201, 201, __NR_accept, true, 2001),
                _CREATE_SYSCALL_LOGGABLE_MUST_IGNORE(101, 101, __NR_recvfrom, false, 1001),
                {}
            }
        }
    },
    {
        .type = TCT_SYSCALL,
        .id = "syscall_4",
        /* Testing only AMMS_ONLY_FAILED since syscall_3 */
        .arg = {
            .user = {
                .uid_monitor_mode = AMM_IGNORE,
                .uids = {.len = 1, .arr = {1001}},
            },
            .ignore_pids = {.len = 1, .arr = {101}},
            .ignore_ppids = {.len = 1, .arr = {101}},
            .monitor_syscalls = AMMS_ONLY_FAILED,
            .network_io = false,
            .include_ns_info = false,
            .nf = {}
        },
        .ff_list = {
            .list = {
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_SUCCESS_MUST_IGNORE(true),
                _CREATE_SYSCALL_LOGGABLE_BY_SYS_SUCCESS_MUST_CAPTURE(false),
                _CREATE_SYSCALL_LOGGABLE_MUST_CAPTURE(201, 201, __NR_accept, false, 2001),
                _CREATE_SYSCALL_LOGGABLE_MUST_IGNORE(101, 101, __NR_recvfrom, true, 1001),
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