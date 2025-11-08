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

#include "audit/util/log/log.h"

#include "test/kernel/audit/common.h"
#include "test/kernel/audit/msg.h"

#include "audit/msg/ops.h"
#include "audit/msg/namespace/namespace.h"
#include "audit/msg/netfilter/netfilter.h"
#include "audit/msg/network/network.h"
#include "audit/msg/ubsi/ubsi.h"
#include "audit/util/seqbuf/seqbuf.h"


#define MSG_TEST_SERIALIZE_CONFIG_LEN 3
#define MSG_AUDIT_STR_LEN 512


static const char* get_msg_name_by_type(enum msg_common_type t);


static void msg_test_serialize_config_common_equals_expected(
    struct test_stats *stats, const char *test_name, enum msg_common_type m_type,
    const char *actual, const char *expected
)
{
    stats->total++;
    if (strncmp(actual, expected, MSG_AUDIT_STR_LEN) != 0)
    {
        TEST_FAIL(
            stats, test_name,
            "m_ops->to_audit_str result mismatch for msg type: %s",
            get_msg_name_by_type(m_type)
        );
        util_log_info(test_name, "Actual: %s", actual);
        util_log_info(test_name, "Expected: %s", expected);
    } else
    {
        TEST_PASS(stats, test_name);
    }
}

static void msg_test_serialize_config_set_expected_dummy(struct msg_common_header *m)
{
    return;
}

static void msg_test_serialize_config_test_expected_dummy_ns(
    struct test_stats *stats, const char *test_name, enum msg_common_type m_type, const char *actual
)
{
    const char *expected = "ns_syscall=0 ns_subtype=ns_namespaces ns_operation=ns_NEWPROCESS "
                           "ns_ns_pid=0 ns_host_pid=0 ns_inum_mnt=0 ns_inum_net=0 "
                           "ns_inum_pid=0 ns_inum_pid_children=0 ns_inum_usr=0 "
                           "ns_inum_ipc=0 ns_inum_cgroup=0";
    msg_test_serialize_config_common_equals_expected(
        stats, test_name, m_type, actual, expected
    );
}

static void msg_test_serialize_config_test_expected_dummy_nf(
    struct test_stats *stats, const char *test_name, enum msg_common_type m_type, const char *actual
)
{
    const char *expected = "nf_subtype=nf_netfilter nf_hook=NF_INET_PRE_ROUTING nf_priority=UNKNOWN "
                           "nf_id=0000000000000000 nf_src_ip=unknown nf_src_port=0 "
                           "nf_dst_ip=unknown nf_dst_port=0 nf_protocol=UNKNOWN "
                           "nf_ip_version=UNKNOWN nf_net_ns=0";
    msg_test_serialize_config_common_equals_expected(
        stats, test_name, m_type, actual, expected
    );
}

static void msg_test_serialize_config_test_expected_dummy_netio(
    struct test_stats *stats, const char *test_name, enum msg_common_type m_type, const char *actual
)
{
    const char *expected = "netio_intercepted=\"syscall=0 exit=0 success=0 fd=0 pid=0 ppid=0 "
                           "gid=0 egid=0 sgid=0 fsgid=0 uid=0 euid=0 suid=0 fsuid=0 "
                           "comm=00000000000000000000000000000000 sock_type=0 local_saddr= "
                           "remote_saddr= remote_saddr_size=0 net_ns_inum=0\"";
    msg_test_serialize_config_common_equals_expected(
        stats, test_name, m_type, actual, expected
    );
}

static void msg_test_serialize_config_test_expected_dummy_ubsi(
    struct test_stats *stats, const char *test_name, enum msg_common_type m_type, const char *actual
)
{
    const char *expected = "ubsi_intercepted=\"syscall=0 success=no exit=0 a0=0 a1=0 a2=0 a3=0 "
                           "items=0 pid=0 ppid=0 gid=0 egid=0 sgid=0 fsgid=0 uid=0 euid=0 "
                           "suid=0 fsuid=0 comm=00000000000000000000000000000000\"";
    msg_test_serialize_config_common_equals_expected(
        stats, test_name, m_type, actual, expected
    );
}

static void msg_test_serialize_config_set_expected_ns_1(struct msg_common_header *m)
{
    struct msg_namespace *n = (struct msg_namespace *)m;
    n->host_pid = 1;
    n->ns_inum_cgroup = 2;
    n->ns_inum_ipc = 3;
    n->ns_inum_mnt = 4;
    n->ns_inum_net = 5;
    n->ns_inum_pid = 6;
    n->ns_inum_pid_children = 7;
    n->ns_inum_usr = 8;
    n->ns_pid = 9;
    n->op = NS_OP_SETNS;
    n->syscall_number = 10;
}

static void msg_test_serialize_config_test_expected_ns_1(
    struct test_stats *stats, const char *test_name, enum msg_common_type m_type, const char *actual
)
{
    const char *expected = "ns_syscall=10 ns_subtype=ns_namespaces ns_operation=ns_SETNS "
                           "ns_ns_pid=9 ns_host_pid=1 ns_inum_mnt=4 ns_inum_net=5 "
                           "ns_inum_pid=6 ns_inum_pid_children=7 ns_inum_usr=8 "
                           "ns_inum_ipc=3 ns_inum_cgroup=2";
    msg_test_serialize_config_common_equals_expected(
        stats, test_name, m_type, actual, expected
    );
}

static void msg_test_serialize_config_set_expected_nf_1(struct msg_common_header *m)
{
    struct msg_netfilter *n = (struct msg_netfilter *)m;
    n->dst_addr = (struct msg_netfilter_addr){
        .addr.ip4 = {
            .s_addr = htonl(0xFE0301D3)  // 254.3.1.211
        },
        .port = 80
    };
    n->hook_num = NF_INET_LOCAL_IN;
    n->ip_proto = NFPROTO_IPV4;
    n->net_ns_inum = 3;
    n->priority = NF_IP_PRI_LAST;
    n->skb_ptr = NULL;
    n->src_addr = (struct msg_netfilter_addr){
        .addr.ip4 = {
            .s_addr = htonl(0x7F000001)  // 127.0.0.1
        },
        .port = 8080
    };
    n->transport_proto = IPPROTO_TCP;
}

static void msg_test_serialize_config_test_expected_nf_1(
    struct test_stats *stats, const char *test_name, enum msg_common_type m_type, const char *actual
)
{
    const char *expected = "nf_subtype=nf_netfilter nf_hook=NF_INET_LOCAL_IN nf_priority=NF_IP_PRI_LAST "
                           "nf_id=0000000000000000 nf_src_ip=127.0.0.1 nf_src_port=8080 "
                           "nf_dst_ip=254.3.1.211 nf_dst_port=80 nf_protocol=TCP "
                           "nf_ip_version=IPV4 nf_net_ns=3";
    msg_test_serialize_config_common_equals_expected(
        stats, test_name, m_type, actual, expected
    );
}

static void msg_test_serialize_config_set_expected_nf_2(struct msg_common_header *m)
{
    struct msg_netfilter *n = (struct msg_netfilter *)m;
    n->dst_addr = (struct msg_netfilter_addr){
        .addr.ip6 = {
            .in6_u.u6_addr8 = {0x20, 0x01, 0x0d, 0xb8, 0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01}  // 2001:0db8:0000:0000:0000:0000:0000:0001
        },
        .port = 443
    };
    n->hook_num = NF_INET_LOCAL_OUT;
    n->ip_proto = NFPROTO_IPV6;
    n->net_ns_inum = 5;
    n->priority = NF_IP_PRI_FIRST;
    n->skb_ptr = NULL;
    n->src_addr = (struct msg_netfilter_addr){
        .addr.ip6 = {
            .in6_u.u6_addr8 = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                               0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01}  // 0000:0000:0000:0000:0000:0000:0000:0001
        },
        .port = 9090
    };
    n->transport_proto = IPPROTO_UDP;
}

static void msg_test_serialize_config_test_expected_nf_2(
    struct test_stats *stats, const char *test_name, enum msg_common_type m_type, const char *actual
)
{
    const char *expected = "nf_subtype=nf_netfilter nf_hook=NF_INET_LOCAL_OUT nf_priority=NF_IP_PRI_FIRST "
                           "nf_id=0000000000000000 nf_src_ip=0000:0000:0000:0000:0000:0000:0000:0001 nf_src_port=9090 "
                           "nf_dst_ip=2001:0db8:0000:0000:0000:0000:0000:0001 nf_dst_port=443 nf_protocol=UDP "
                           "nf_ip_version=IPV6 nf_net_ns=5";
    msg_test_serialize_config_common_equals_expected(
        stats, test_name, m_type, actual, expected
    );
}

static void msg_test_serialize_config_set_expected_netio_1(struct msg_common_header *m)
{
    struct msg_network *n = (struct msg_network *)m;
    n->fd = 1;
    *((struct sockaddr_in *)&n->local_saddr) = (struct sockaddr_in){
        .sin_family = AF_INET,
        .sin_port = htons(8080),
        .sin_addr = {
            .s_addr = htonl(0x7F000001)  // 127.0.0.1
        }
    };
    n->local_saddr_size = sizeof(struct sockaddr_in);
    n->net_ns_inum = 4;
    n->proc_info = (struct msg_common_process){
        .pid = 1234,
        .ppid = 1000,
        .uid = 1001,
        .euid = 1001,
        .suid = 1001,
        .fsuid = 1001,
        .gid = 1002,
        .egid = 1002,
        .sgid = 1002,
        .fsgid = 1002,
        .comm = "test_process"
    };
    *((struct sockaddr_in *)&n->remote_saddr) = (struct sockaddr_in){
        .sin_family = AF_INET,
        .sin_port = htons(80),
        .sin_addr = {
            .s_addr = htonl(0xFE0301D3)  // 254.3.1.211
        }
    };
    n->remote_saddr_size = sizeof(struct sockaddr_in);
    n->sock_type = 2;
    n->syscall_number = 45;
    n->syscall_result = 5;
    n->syscall_success = 1;
}

static void msg_test_serialize_config_test_expected_netio_1(
    struct test_stats *stats, const char *test_name, enum msg_common_type m_type, const char *actual
)
{
    const char *expected = "netio_intercepted=\"syscall=45 exit=5 success=1 fd=1 pid=1234 ppid=1000 "
                           "gid=1002 egid=1002 sgid=1002 fsgid=1002 uid=1001 euid=1001 suid=1001 fsuid=1001 "
                           "comm=746573745f70726f6365737300000000 sock_type=2 local_saddr=02001f907f0000010000000000000000 "
                           "remote_saddr=02000050fe0301d30000000000000000 remote_saddr_size=16 net_ns_inum=4\"";
    msg_test_serialize_config_common_equals_expected(
        stats, test_name, m_type, actual, expected
    );
}

static void msg_test_serialize_config_set_expected_netio_2(struct msg_common_header *m)
{
    struct msg_network *n = (struct msg_network *)m;
    struct sockaddr_in6 *local_addr, *remote_addr;

    n->fd = 2;
    n->syscall_number = 46;
    n->syscall_result = 10;
    n->syscall_success = 0;
    n->sock_type = 1;

    // Zero out and set local address
    local_addr = (struct sockaddr_in6 *)&n->local_saddr;
    memset(local_addr, 0, sizeof(struct sockaddr_in6));
    local_addr->sin6_family = AF_INET6;
    local_addr->sin6_port = htons(9090);
    local_addr->sin6_addr.in6_u.u6_addr8[0] = 0x00;
    local_addr->sin6_addr.in6_u.u6_addr8[1] = 0x00;
    local_addr->sin6_addr.in6_u.u6_addr8[2] = 0x00;
    local_addr->sin6_addr.in6_u.u6_addr8[3] = 0x00;
    local_addr->sin6_addr.in6_u.u6_addr8[4] = 0x00;
    local_addr->sin6_addr.in6_u.u6_addr8[5] = 0x00;
    local_addr->sin6_addr.in6_u.u6_addr8[6] = 0x00;
    local_addr->sin6_addr.in6_u.u6_addr8[7] = 0x00;
    local_addr->sin6_addr.in6_u.u6_addr8[8] = 0x00;
    local_addr->sin6_addr.in6_u.u6_addr8[9] = 0x00;
    local_addr->sin6_addr.in6_u.u6_addr8[10] = 0x00;
    local_addr->sin6_addr.in6_u.u6_addr8[11] = 0x00;
    local_addr->sin6_addr.in6_u.u6_addr8[12] = 0x00;
    local_addr->sin6_addr.in6_u.u6_addr8[13] = 0x00;
    local_addr->sin6_addr.in6_u.u6_addr8[14] = 0x00;
    local_addr->sin6_addr.in6_u.u6_addr8[15] = 0x01;  // ::1
    n->local_saddr_size = sizeof(struct sockaddr_in6);

    // Zero out and set remote address
    remote_addr = (struct sockaddr_in6 *)&n->remote_saddr;
    memset(remote_addr, 0, sizeof(struct sockaddr_in6));
    remote_addr->sin6_family = AF_INET6;
    remote_addr->sin6_port = htons(443);
    remote_addr->sin6_addr.in6_u.u6_addr8[0] = 0x20;
    remote_addr->sin6_addr.in6_u.u6_addr8[1] = 0x01;
    remote_addr->sin6_addr.in6_u.u6_addr8[2] = 0x0d;
    remote_addr->sin6_addr.in6_u.u6_addr8[3] = 0xb8;
    remote_addr->sin6_addr.in6_u.u6_addr8[4] = 0x00;
    remote_addr->sin6_addr.in6_u.u6_addr8[5] = 0x00;
    remote_addr->sin6_addr.in6_u.u6_addr8[6] = 0x00;
    remote_addr->sin6_addr.in6_u.u6_addr8[7] = 0x00;
    remote_addr->sin6_addr.in6_u.u6_addr8[8] = 0x00;
    remote_addr->sin6_addr.in6_u.u6_addr8[9] = 0x00;
    remote_addr->sin6_addr.in6_u.u6_addr8[10] = 0x00;
    remote_addr->sin6_addr.in6_u.u6_addr8[11] = 0x00;
    remote_addr->sin6_addr.in6_u.u6_addr8[12] = 0x00;
    remote_addr->sin6_addr.in6_u.u6_addr8[13] = 0x00;
    remote_addr->sin6_addr.in6_u.u6_addr8[14] = 0x00;
    remote_addr->sin6_addr.in6_u.u6_addr8[15] = 0x01;  // 2001:db8::1
    n->remote_saddr_size = sizeof(struct sockaddr_in6);

    n->net_ns_inum = 6;
    n->proc_info = (struct msg_common_process){
        .pid = 5678,
        .ppid = 5000,
        .uid = 2001,
        .euid = 2001,
        .suid = 2001,
        .fsuid = 2001,
        .gid = 2002,
        .egid = 2002,
        .sgid = 2002,
        .fsgid = 2002,
        .comm = "ipv6_test"
    };
}

static void msg_test_serialize_config_test_expected_netio_2(
    struct test_stats *stats, const char *test_name, enum msg_common_type m_type, const char *actual
)
{
    const char *expected = "netio_intercepted=\"syscall=46 exit=10 success=0 fd=2 pid=5678 ppid=5000 "
                           "gid=2002 egid=2002 sgid=2002 fsgid=2002 uid=2001 euid=2001 suid=2001 fsuid=2001 "
                           "comm=697076365f7465737400000000000000 sock_type=1 "
                           "local_saddr=0a002382000000000000000000000000000000000000000100000000 "
                           "remote_saddr=0a0001bb0000000020010db800000000000000000000000100000000 "
                           "remote_saddr_size=28 net_ns_inum=6\"";
    msg_test_serialize_config_common_equals_expected(
        stats, test_name, m_type, actual, expected
    );
}

static void msg_test_serialize_config_set_expected_ubsi_1(struct msg_common_header *m)
{
    struct msg_ubsi *n =(struct msg_ubsi*)m;
    n->proc_info = (struct msg_common_process){
        .pid = 5678,
        .ppid = 5000,
        .uid = 2001,
        .euid = 2001,
        .suid = 2001,
        .fsuid = 2001,
        .gid = 2002,
        .egid = 2002,
        .sgid = 2002,
        .fsgid = 2002,
        .comm = "test_process"
    };
    n->signal = 4;
    n->syscall_number = 44;
    n->syscall_result = 1;
    n->syscall_success = true;
    n->target_pid = 100;
}

static void msg_test_serialize_config_test_expected_ubsi_1(
    struct test_stats *stats, const char *test_name, enum msg_common_type m_type, const char *actual
)
{
    const char *expected = "ubsi_intercepted=\"syscall=44 success=yes exit=1 a0=64 a1=4 a2=0 a3=0 "
                           "items=0 pid=5678 ppid=5000 gid=2002 egid=2002 sgid=2002 fsgid=2002 uid=2001 euid=2001 "
                           "suid=2001 fsuid=2001 comm=746573745f70726f6365737300000000\"";
    msg_test_serialize_config_common_equals_expected(
        stats, test_name, m_type, actual, expected
    );
}

/////////////////////////////////////////

struct msg_test_serialize_config
{
    // Function to set values that produces the expected audit string.
    void (*set_expected)(struct msg_common_header *m);
    // Function to test the actual with the expected audit string.
    void (*test_expected)(struct test_stats *stats, const char *test_name, enum msg_common_type m_type, const char *actual);
};

struct msg_test_config
{
    enum msg_common_type type;
    char name[32];
    size_t size;
    struct msg_common_version version;
    struct msg_test_serialize_config serialize_configs[MSG_TEST_SERIALIZE_CONFIG_LEN];
};

static const struct msg_test_config MSG_TEST_CONFIGS[] = {
    {
        .type = MSG_NAMESPACES,
        .name = "MSG_NAMESPACES",
        .size = sizeof(struct msg_namespace),
        .version = {.major = 1, .minor = 0, .patch = 0},
        .serialize_configs = {
            {
                .set_expected = msg_test_serialize_config_set_expected_dummy,
                .test_expected = msg_test_serialize_config_test_expected_dummy_ns
            },
            {
                .set_expected = msg_test_serialize_config_set_expected_ns_1,
                .test_expected = msg_test_serialize_config_test_expected_ns_1
            },
            {0}
        }
    },
    {
        .type = MSG_NETFILTER,
        .name = "MSG_NETFILTER",
        .size = sizeof(struct msg_netfilter),
        .version = {.major = 1, .minor = 0, .patch = 0},
        .serialize_configs = {
            {
                .set_expected = msg_test_serialize_config_set_expected_dummy,
                .test_expected = msg_test_serialize_config_test_expected_dummy_nf
            },
            {
                .set_expected = msg_test_serialize_config_set_expected_nf_1,
                .test_expected = msg_test_serialize_config_test_expected_nf_1
            },
            {
                .set_expected = msg_test_serialize_config_set_expected_nf_2,
                .test_expected = msg_test_serialize_config_test_expected_nf_2
            }
        }
    },
    {
        .type = MSG_NETWORK,
        .name = "MSG_NETWORK",
        .size = sizeof(struct msg_network),
        .version = {.major = 1, .minor = 0, .patch = 0},
        .serialize_configs = {
            {
                .set_expected = msg_test_serialize_config_set_expected_dummy,
                .test_expected = msg_test_serialize_config_test_expected_dummy_netio
            },
            {
                .set_expected = msg_test_serialize_config_set_expected_netio_1,
                .test_expected = msg_test_serialize_config_test_expected_netio_1
            },
            {
                .set_expected = msg_test_serialize_config_set_expected_netio_2,
                .test_expected = msg_test_serialize_config_test_expected_netio_2
            }
        }
    },
    {
        .type = MSG_UBSI,
        .name = "MSG_UBSI",
        .size = sizeof(struct msg_ubsi),
        .version = {.major = 1, .minor = 0, .patch = 0},
        .serialize_configs = {
            {
                .set_expected = msg_test_serialize_config_set_expected_dummy,
                .test_expected = msg_test_serialize_config_test_expected_dummy_ubsi
            },
                        {
                .set_expected = msg_test_serialize_config_set_expected_ubsi_1,
                .test_expected = msg_test_serialize_config_test_expected_ubsi_1
            },
            {0}
        }
    }
};
#define MSG_TEST_CONFIGS_LEN (sizeof(MSG_TEST_CONFIGS) / sizeof(MSG_TEST_CONFIGS[0]))

/////////////////////////////////////////

static const char* get_msg_name_by_type(enum msg_common_type t)
{
    int i;
    for (i = 0; i < MSG_TEST_CONFIGS_LEN; i++)
    {
        if (MSG_TEST_CONFIGS[i].type == t)
            return &MSG_TEST_CONFIGS[i].name[0];
    }
    return NULL;
}

static bool msg_version_equal(const struct msg_common_version *a, const struct msg_common_version *b)
{
    return a->major == b->major && a->minor == b->minor && a->patch == b->patch;
}

static void test_msg_test_ops_generic(struct test_stats *stats)
{
    const char *test_name = "test_msg_test_ops_generic";
    int i, err;

    for (i = 0; i < MSG_TEST_CONFIGS_LEN; i++)
    {
        const struct msg_test_config *m_t_config = &MSG_TEST_CONFIGS[i];
        const struct msg_ops *m_ops;
        struct msg_common_header* m_c;
        int j;

        stats->total++;
        m_ops = msg_ops_get(m_t_config->type);
        if (!m_ops)
        {
            TEST_FAIL(
                stats, test_name, "msg_ops_get returned NULL for msg type %s", 
                get_msg_name_by_type(m_t_config->type)
            );
            continue;
        }
        TEST_PASS(stats, test_name);

        stats->total++;
        m_c = m_ops->kalloc();
        if (!m_c)
        {
            TEST_FAIL(
                stats, test_name, "m_ops->kalloc returned NULL for msg type %s",
                get_msg_name_by_type(m_t_config->type)
            );
            continue;
        }
        TEST_PASS(stats, test_name);

        stats->total++;
        err = m_ops->kinit(m_c);
        if (err != 0)
        {
            TEST_FAIL(
                stats, test_name, "m_ops->kinit failed for msg type %s. Err: %d",
                get_msg_name_by_type(m_t_config->type), err
            );
            msg_ops_kfree(m_c);
            continue;
        }
        TEST_PASS(stats, test_name);

        stats->total++;
        if (m_c->msg_type != m_t_config->type)
        {
            TEST_FAIL(
                stats, test_name, "m_ops->kinit msg type mismatch. Actual: %s. Expected: %s",
                get_msg_name_by_type(m_c->msg_type), get_msg_name_by_type(m_t_config->type)
            );
            msg_ops_kfree(m_c);
            continue;
        }
        TEST_PASS(stats, test_name);

        stats->total++;
        if (!msg_version_equal(&m_c->version, &m_t_config->version))
        {
            TEST_FAIL(
                stats, test_name,
                "m_ops->kinit version mismatch for msg type: %s. Actual: %u.%u.%u. Expected: %u.%u.%u",
                get_msg_name_by_type(m_c->msg_type),
                m_c->version.major, m_c->version.minor, m_c->version.patch,
                m_t_config->version.major, m_t_config->version.minor, m_t_config->version.patch
            );
            msg_ops_kfree(m_c);
            continue;
        }
        TEST_PASS(stats, test_name);

        for (j = 0; j < MSG_TEST_SERIALIZE_CONFIG_LEN; j++)
        {
            const struct msg_test_serialize_config *t_ser_config = &m_t_config->serialize_configs[j];
            struct seqbuf sb;
            char sb_buf[MSG_AUDIT_STR_LEN];

            if (!t_ser_config->set_expected || !t_ser_config->test_expected)
            {
                continue;
            }

            util_seqbuf_init(&sb, &sb_buf[0], MSG_AUDIT_STR_LEN);

            // Set the value that needs to be expected.
            t_ser_config->set_expected(m_c);

            stats->total++;
            err = m_ops->to_audit_str(&sb, m_c);
            if (err != 0)
            {
                TEST_FAIL(
                    stats, test_name, "m_ops->to_audit_str failed for msg type %s. Err: %d",
                    get_msg_name_by_type(m_c->msg_type), err
                );
                continue;
            }
            TEST_PASS(stats, test_name);

            // Test the expected value.
            t_ser_config->test_expected(stats, test_name, m_c->msg_type, &sb_buf[0]);
        }

        msg_ops_kfree(m_c);
    }
}

int test_msg_all(struct test_stats *stats)
{
    test_stats_init(stats);
    util_log_info("test_msg", "Starting tests");

    test_msg_test_ops_generic(stats);
    return 0;
}